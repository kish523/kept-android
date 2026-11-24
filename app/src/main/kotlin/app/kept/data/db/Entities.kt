package app.kept.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.kept.core.deadlines.ReminderKind
import app.kept.core.parser.model.Category
import app.kept.core.parser.model.DocType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

// Money is always integer minor units + ISO 4217 currency. Deadlines are plain local dates.

/** TRASHED: deleted with Undo still possible; removed for good when the Undo message goes, or on next start. */
enum class ReceiptStatus { DRAFT, NEEDS_REVIEW, ACTIVE, ARCHIVED, TRASHED }
enum class CaptureSource { CAMERA, PHOTOS, PDF, SHARE_SHEET }
enum class ItemState { KEPT, RETURNING, RETURNED, CLAIMED }
enum class AttachmentKind { PAGE_IMAGE, ORIGINAL_PDF }
enum class ExtractionTier { RULES, ON_DEVICE_MODEL, CLOUD }
enum class ExtractionStatus { QUEUED, RUNNING, DONE, FAILED }
enum class ReminderChannel { NOTIFICATION, CALENDAR }
enum class ReminderStatus { PENDING, HANDED_TO_OS, DELIVERED, SNOOZED, DONE, CANCELLED }

@Entity(tableName = "receipts", indices = [Index("status"), Index("purchaseDate")])
data class ReceiptEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val merchantName: String? = null,
    val purchaseDate: LocalDate? = null,
    val totalMinor: Long? = null,
    val taxMinor: Long? = null,
    val currency: String? = null,
    /** e.g. "Visa ··4417". Never a full card number. */
    val paymentLabel: String? = null,
    val orderNumber: String? = null,
    val docType: DocType = DocType.RECEIPT,
    val category: Category? = null,
    val source: CaptureSource,
    val status: ReceiptStatus = ReceiptStatus.DRAFT,
    val notes: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(ReceiptEntity::class, ["id"], ["receiptId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(ItemGroupEntity::class, ["id"], ["groupId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("receiptId"), Index("groupId"), Index("returnBy"), Index("warrantyEnds")],
)
data class ItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val receiptId: String,
    val name: String,
    val quantity: Int = 1,
    val unitPriceMinor: Long? = null,
    val totalPriceMinor: Long? = null,
    val category: Category? = null,
    val groupId: String? = null,
    val serialNumber: String? = null,
    val modelNumber: String? = null,
    val returnBy: LocalDate? = null,
    /** Why this date: "Printed on the receipt", "15 days, VoltMart policy", "Set by you". */
    val returnNote: String? = null,
    val noReturns: Boolean = false,
    val warrantyEnds: LocalDate? = null,
    val warrantyNote: String? = null,
    val lifetimeWarranty: Boolean = false,
    val state: ItemState = ItemState.KEPT,
    val supportUrl: String? = null,
    /** The user answered the return question ("Keeping it"): no more return reminders, off "Returns closing". */
    @ColumnInfo(defaultValue = "0") val returnResolved: Boolean = false,
)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(ReceiptEntity::class, ["id"], ["receiptId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("receiptId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val receiptId: String,
    val kind: AttachmentKind,
    /** Relative to filesDir, e.g. "receipts/<id>/page-0.jpg". */
    val relativePath: String,
    val pageIndex: Int,
    /** Detects corruption and duplicate imports. */
    val sha256: String,
    /** Recognised text with card numbers already redacted. */
    val ocrText: String? = null,
    val createdAt: Instant = Instant.now(),
)

@Entity(
    tableName = "extraction_results",
    foreignKeys = [ForeignKey(ReceiptEntity::class, ["id"], ["receiptId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("receiptId")],
)
data class ExtractionResultEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val receiptId: String,
    val tier: ExtractionTier,
    val status: ExtractionStatus,
    val attempts: Int = 0,
    val lastError: String? = null,
    /** Values, confidences and hints per field; see [app.kept.data.ExtractionJson]. */
    val fieldsJson: String? = null,
    val engineVersion: String = "rules-1",
    val createdAt: Instant = Instant.now(),
)

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(ItemEntity::class, ["id"], ["itemId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("itemId"), Index("status"), Index("fireAt")],
)
data class ReminderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val kind: ReminderKind,
    val dueDate: LocalDate,
    val daysBefore: Int,
    /** Floating local time: 9:00 wherever the phone is. */
    val fireAt: LocalDateTime,
    val channel: ReminderChannel = ReminderChannel.NOTIFICATION,
    val status: ReminderStatus = ReminderStatus.PENDING,
    /** PendingIntent request code, so the alarm can be cancelled or replaced. */
    val osRequestCode: Int? = null,
    val calendarEventId: Long? = null,
    val lastScheduledAt: Instant? = null,
)

@Entity(tableName = "item_groups")
data class ItemGroupEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Instant = Instant.now(),
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
)

@Entity(
    tableName = "receipt_tags",
    primaryKeys = ["receiptId", "tagId"],
    foreignKeys = [
        ForeignKey(ReceiptEntity::class, ["id"], ["receiptId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class ReceiptTagEntity(val receiptId: String, val tagId: String)

/** Full-text index over store, items, notes, numbers and OCR text. Rebuilt on every save. */
@Fts4(notIndexed = ["receiptId"])
@Entity(tableName = "receipt_search")
data class ReceiptSearchEntity(
    @ColumnInfo(name = "receiptId") val receiptId: String,
    val body: String,
)

data class ReceiptWithDetails(
    @Embedded val receipt: ReceiptEntity,
    @Relation(parentColumn = "id", entityColumn = "receiptId") val items: List<ItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "receiptId") val attachments: List<AttachmentEntity>,
)

/** One row on Home: an item and its deadlines. */
data class DeadlineRow(
    val itemId: String,
    val receiptId: String,
    val itemName: String,
    val merchantName: String?,
    val purchaseDate: LocalDate?,
    val category: Category?,
    val returnBy: LocalDate?,
    val warrantyEnds: LocalDate?,
    val noReturns: Boolean,
    val returnResolved: Boolean,
    val state: ItemState,
)

/** Everything a reminder notification needs, in one row. */
data class ReminderDetails(
    @Embedded val reminder: ReminderEntity,
    val itemName: String,
    val receiptId: String,
    val merchantName: String?,
    val itemState: ItemState,
    val returnResolved: Boolean,
    val receiptStatus: ReceiptStatus,
)

/** An item with its receipt's status, for "clean up finished receipts". */
data class ItemWithStatus(
    @Embedded val item: ItemEntity,
    val receiptStatus: ReceiptStatus,
)
