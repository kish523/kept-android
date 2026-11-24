package app.kept.data

import android.net.Uri
import androidx.room.withTransaction
import app.kept.core.deadlines.ReminderKind
import app.kept.core.deadlines.ReminderPlanner
import app.kept.core.parser.model.Category
import app.kept.core.parser.model.ParsedReceipt
import app.kept.data.db.AttachmentEntity
import app.kept.data.db.AttachmentKind
import app.kept.data.db.CaptureSource
import app.kept.data.db.ItemEntity
import app.kept.data.db.ItemState
import app.kept.data.db.KeptDatabase
import app.kept.data.db.ReceiptEntity
import app.kept.data.db.ReceiptSearchEntity
import app.kept.data.db.ReceiptStatus
import app.kept.data.db.ReminderEntity
import app.kept.data.db.ReminderStatus
import app.kept.data.files.AttachmentStore
import app.kept.data.files.StoredFile
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** What the user confirmed on the review card. */
data class ReviewedReceipt(
    val merchantName: String,
    val purchaseDate: LocalDate,
    val totalMinor: Long?,
    val currency: String,
    val category: Category?,
    val returnBy: LocalDate?,
    val returnNote: String?,
    val noReturns: Boolean,
    val warrantyEnds: LocalDate?,
    val warrantyNote: String?,
    val lifetimeWarranty: Boolean,
    val items: List<ReviewedItem>,
    val serialNumber: String?,
    val orderNumber: String?,
    val paymentLabel: String?,
    val notes: String?,
)

data class ReviewedItem(val name: String, val quantity: Int, val totalMinor: Long?, val unitMinor: Long?)

class ReceiptRepository(
    private val db: KeptDatabase,
    private val files: AttachmentStore,
) {
    /**
     * Step 1 of every capture, before any reading: copy the files into app storage and write a DRAFT
     * row in one transaction. From here on nothing can lose the capture.
     */
    suspend fun createDraft(uris: List<Uri>, source: CaptureSource, isPdf: Boolean = false): String {
        val receipt = ReceiptEntity(source = source)
        try {
            val stored: List<StoredFile> =
                if (isPdf) files.importPdf(receipt.id, uris.single())
                else uris.mapIndexed { i, uri -> files.importImage(receipt.id, i, uri) }
            db.withTransaction {
                db.receiptDao().insert(receipt)
                db.attachmentDao().insertAll(stored.map {
                    AttachmentEntity(
                        receiptId = receipt.id,
                        kind = if (it.isPdf) AttachmentKind.ORIGINAL_PDF else AttachmentKind.PAGE_IMAGE,
                        relativePath = it.relativePath,
                        pageIndex = it.pageIndex,
                        sha256 = it.sha256,
                    )
                })
            }
        } catch (e: Throwable) {
            // No orphaned files when the copy or the insert fails (or the coroutine is cancelled).
            withContext(NonCancellable) { files.deleteReceipt(receipt.id) }
            throw e
        }
        return receipt.id
    }

    /** Pre-fills the draft with what the parser read; the user confirms on the review card. */
    suspend fun applyExtraction(receiptId: String, parsed: ParsedReceipt) {
        val receipt = db.receiptDao().get(receiptId) ?: return
        val payment = parsed.payment?.value
        db.receiptDao().update(
            receipt.copy(
                merchantName = parsed.merchant?.value,
                purchaseDate = parsed.purchaseDate?.value,
                totalMinor = parsed.total?.value?.minorUnits,
                taxMinor = parsed.tax?.value?.minorUnits,
                currency = parsed.currency.value,
                paymentLabel = payment?.let { p -> listOfNotNull(p.label, p.cardLast4?.let { "··$it" }).joinToString(" ") },
                orderNumber = parsed.orderNumber?.value,
                docType = parsed.docType.value,
                category = parsed.suggestedCategory?.value,
                status = ReceiptStatus.NEEDS_REVIEW,
                updatedAt = Instant.now(),
            ),
        )
    }

    /**
     * Saves what the user confirmed: receipt, items, reminder rows and the search index, all in one
     * transaction. Returns the planned reminders so the caller can show "reminders Dec 17, 21, 23".
     */
    suspend fun saveReviewed(receiptId: String, r: ReviewedReceipt, now: LocalDateTime = LocalDateTime.now()): List<ReminderEntity> {
        val existing = db.receiptDao().get(receiptId) ?: error("Receipt $receiptId not found")
        // Re-saving an edited receipt keeps what the user already decided (kept, returned, claimed).
        val previous = db.itemDao().forReceipt(receiptId).firstOrNull()
        val newReturnBy = if (r.noReturns) null else r.returnBy
        val keepDecision = previous != null && previous.returnResolved && previous.returnBy == newReturnBy
        val items = r.items.ifEmpty { listOf(ReviewedItem(r.merchantName, 1, r.totalMinor, null)) }.mapIndexed { i, item ->
            ItemEntity(
                receiptId = receiptId,
                name = item.name,
                quantity = item.quantity,
                unitPriceMinor = item.unitMinor,
                totalPriceMinor = item.totalMinor,
                category = r.category,
                serialNumber = if (i == 0) r.serialNumber else null,
                returnBy = newReturnBy,
                returnNote = r.returnNote,
                noReturns = r.noReturns,
                warrantyEnds = r.warrantyEnds,
                warrantyNote = r.warrantyNote,
                lifetimeWarranty = r.lifetimeWarranty,
                state = previous?.state ?: ItemState.KEPT,
                returnResolved = keepDecision,
            )
        }
        val reminders = items.flatMap { item ->
            val returns = item.returnBy?.takeUnless { item.returnResolved }
                ?.let { ReminderPlanner.plan(ReminderKind.RETURN, it, now) }.orEmpty()
            val warranty = item.warrantyEnds?.let { ReminderPlanner.plan(ReminderKind.WARRANTY, it, now) }.orEmpty()
            (returns + warranty).map { p ->
                ReminderEntity(itemId = item.id, kind = p.kind, dueDate = p.dueDate, daysBefore = p.daysBefore, fireAt = p.fireAt)
            }
        }
        // One reminder per date per receipt: three items bought together share one notification.
        val deduped = reminders.distinctBy { Triple(it.kind, it.dueDate, it.fireAt) }

        val ocrText = db.attachmentDao().forReceipt(receiptId).mapNotNull { it.ocrText }.joinToString("\n")
        db.withTransaction {
            db.receiptDao().update(
                existing.copy(
                    merchantName = r.merchantName,
                    purchaseDate = r.purchaseDate,
                    totalMinor = r.totalMinor,
                    currency = r.currency,
                    category = r.category,
                    orderNumber = r.orderNumber,
                    paymentLabel = r.paymentLabel,
                    notes = r.notes,
                    status = ReceiptStatus.ACTIVE,
                    updatedAt = Instant.now(),
                ),
            )
            db.reminderDao().deleteForReceipt(receiptId)
            db.itemDao().deleteForReceipt(receiptId)
            db.itemDao().insertAll(items)
            db.reminderDao().insertAll(deduped)
            db.searchDao().deleteForReceipt(receiptId)
            db.searchDao().insert(
                ReceiptSearchEntity(
                    receiptId,
                    listOfNotNull(r.merchantName, r.orderNumber, r.serialNumber, r.notes, ocrText)
                        .plus(items.map { it.name })
                        .joinToString("\n"),
                ),
            )
        }
        // TODO(reminders phase): hand `deduped` to the AlarmScheduler (inexact, idle-safe alarms).
        return deduped
    }

    /** "Keeping it": the return window no longer matters. Its return reminders are cancelled; the warranty stays. */
    suspend fun markKeeping(itemId: String) = db.withTransaction {
        db.itemDao().setReturnResolved(itemId, true)
        db.reminderDao().moveStatus(itemId, listOf(ReminderKind.RETURN), ACTIVE_REMINDERS, ReminderStatus.CANCELLED)
        // TODO(reminders phase): cancel the matching OS alarms.
    }

    suspend fun undoKeeping(itemId: String) = db.withTransaction {
        db.itemDao().setReturnResolved(itemId, false)
        db.reminderDao().moveStatus(itemId, listOf(ReminderKind.RETURN), listOf(ReminderStatus.CANCELLED), ReminderStatus.PENDING)
    }

    /** "Returned it": the item leaves Home and every reminder for it stops. Returns the state to restore on undo. */
    suspend fun markReturned(itemId: String): ItemState = db.withTransaction {
        val before = db.itemDao().get(itemId)?.state ?: ItemState.KEPT
        db.itemDao().setState(itemId, ItemState.RETURNED)
        db.reminderDao().moveStatus(itemId, ReminderKind.entries, ACTIVE_REMINDERS, ReminderStatus.CANCELLED)
        before
    }

    suspend fun undoReturned(itemId: String, restore: ItemState) = db.withTransaction {
        db.itemDao().setState(itemId, restore)
        val item = db.itemDao().get(itemId)
        val kinds = if (item?.returnResolved == true) listOf(ReminderKind.WARRANTY) else ReminderKind.entries
        db.reminderDao().moveStatus(itemId, kinds, listOf(ReminderStatus.CANCELLED), ReminderStatus.PENDING)
    }

    /** Permanently deletes a receipt: rows (cascade), files and search entry. */
    suspend fun delete(receiptId: String) {
        db.withTransaction {
            db.searchDao().deleteForReceipt(receiptId)
            db.receiptDao().delete(receiptId)
        }
        files.deleteReceipt(receiptId)
        // TODO(reminders phase): cancel this receipt's OS alarms and calendar events.
    }

    /** Prefix search on every word the user typed: "sony head" finds "SONY WH-1000XM6 HEADPHONES". */
    suspend fun search(userInput: String): List<String> {
        val words = userInput.split(Regex("\\s+")).map { it.filter(Char::isLetterOrDigit) }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return db.searchDao().search(words.joinToString(" ") { "$it*" })
    }
}

private val ACTIVE_REMINDERS = listOf(ReminderStatus.PENDING, ReminderStatus.HANDED_TO_OS, ReminderStatus.SNOOZED)
