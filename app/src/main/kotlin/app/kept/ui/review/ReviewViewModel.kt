package app.kept.ui.review

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.kept.AppContainer
import app.kept.core.deadlines.Deadline
import app.kept.core.deadlines.DeadlineCalculator
import app.kept.core.deadlines.DeadlineSource
import app.kept.core.deadlines.ReminderKind
import app.kept.core.parser.model.Category
import app.kept.core.parser.model.Money
import app.kept.data.ExtractionJson
import app.kept.data.ExtractionSnapshot
import app.kept.data.ReviewedItem
import app.kept.data.ReviewedReceipt
import app.kept.data.SnapshotItem
import app.kept.data.db.AttachmentKind
import app.kept.data.db.ExtractionStatus
import app.kept.data.db.ItemEntity
import app.kept.data.db.ReceiptEntity
import app.kept.data.db.ReceiptStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class ReviewForm(
    val merchant: String = "",
    val purchaseDate: LocalDate? = null,
    val total: String = "",
    val currency: String = "",
    val category: Category? = null,
    val returnDeadline: Deadline = Deadline.Unknown,
    val returnSetByUser: Boolean = false,
    val warrantyDeadline: Deadline = Deadline.Unknown,
    val warrantySetByUser: Boolean = false,
    val items: List<SnapshotItem> = emptyList(),
    val serialNumber: String = "",
    val orderNumber: String = "",
    val paymentLabel: String? = null,
)

data class ReviewUiState(
    val reading: Boolean = true,
    /** Re-opening a saved receipt (title "Receipt" instead of "Check receipt"). */
    val editing: Boolean = false,
    val readFailed: Boolean = false,
    val form: ReviewForm = ReviewForm(),
    /** Fields to show in amber with "Check". Cleared one by one as the user edits them. */
    val needsCheck: Set<String> = emptySet(),
    val warnings: List<String> = emptyList(),
    val preview: Bitmap? = null,
    val saving: Boolean = false,
    /** Saved: the button shows a check for a moment before the screen closes. */
    val saved: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = !reading && !saving && form.merchant.isNotBlank() && form.purchaseDate != null
}

class ReviewViewModel(
    private val container: AppContainer,
    private val receiptId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(ReviewUiState())
    val state: StateFlow<ReviewUiState> = _state

    private val db = container.database

    /** The parse result, kept so deadlines can be re-derived when the user changes the date or store. */
    private var snapshot: ExtractionSnapshot? = null

    init {
        viewModelScope.launch {
            loadPreview()
            // Wait until reading has finished (or the receipt was already saved), then build the form once.
            val (receipt, extraction) = combine(
                db.receiptDao().observe(receiptId),
                db.extractionDao().observeLatest(receiptId),
            ) { r, e -> r to e }.first { (r, e) ->
                r != null && (r.status == ReceiptStatus.ACTIVE || e?.status == ExtractionStatus.DONE || e?.status == ExtractionStatus.FAILED)
            }
            val snapshot = extraction?.fieldsJson?.let { runCatching { ExtractionJson.decode(it) }.getOrNull() }
            val items = db.itemDao().forReceipt(receiptId)
            _state.update {
                it.copy(
                    reading = false,
                    readFailed = extraction?.status == ExtractionStatus.FAILED,
                    editing = receipt!!.status == ReceiptStatus.ACTIVE,
                    form = buildForm(receipt!!, snapshot, items),
                    needsCheck = if (receipt.status == ReceiptStatus.ACTIVE) emptySet() else snapshot?.needsReview.orEmpty(),
                    warnings = snapshot?.warnings.orEmpty(),
                )
            }
        }
    }

    private suspend fun loadPreview() {
        val first = db.attachmentDao().forReceipt(receiptId).firstOrNull { it.kind == AttachmentKind.PAGE_IMAGE } ?: return
        val bitmap = withContext(Dispatchers.IO) {
            val file = container.attachments.resolve(first.relativePath)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1200) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        _state.update { it.copy(preview = bitmap) }
    }

    private fun buildForm(r: ReceiptEntity, snap: ExtractionSnapshot?, saved: List<ItemEntity>): ReviewForm {
        snapshot = snap
        val date = r.purchaseDate
        val category = r.category ?: snap?.category
        val currency = r.currency ?: "USD"
        val base = ReviewForm(
            merchant = r.merchantName.orEmpty(),
            purchaseDate = date,
            total = r.totalMinor?.let { Money(it, currency).toDecimal().toPlainString() }.orEmpty(),
            currency = currency,
            category = category,
            items = if (saved.isNotEmpty()) {
                saved.map { SnapshotItem(it.name, it.quantity, it.totalPriceMinor ?: 0, it.unitPriceMinor) }
            } else {
                snap?.items.orEmpty()
            },
            serialNumber = saved.firstNotNullOfOrNull { it.serialNumber } ?: snap?.serials?.firstOrNull().orEmpty(),
            orderNumber = r.orderNumber.orEmpty(),
            paymentLabel = r.paymentLabel,
        )
        // Editing a saved receipt: show exactly what was saved.
        val first = saved.firstOrNull()
        if (first != null) {
            return base.copy(
                returnDeadline = when {
                    first.noReturns -> Deadline.NotAllowed(DeadlineSource.USER)
                    first.returnBy != null -> Deadline.On(first.returnBy, DeadlineSource.USER, first.returnNote ?: "Saved")
                    else -> Deadline.Unknown
                },
                warrantyDeadline = when {
                    first.lifetimeWarranty -> Deadline.Lifetime
                    first.warrantyEnds != null -> Deadline.On(first.warrantyEnds, DeadlineSource.USER, first.warrantyNote ?: "Saved")
                    else -> Deadline.Unknown
                },
                returnSetByUser = true,
                warrantySetByUser = true,
            )
        }
        return withComputedDeadlines(base, snap)
    }

    /** Re-derives return and warranty dates from the receipt, the store table and the category. */
    private fun withComputedDeadlines(form: ReviewForm, snap: ExtractionSnapshot? = snapshot): ReviewForm {
        val date = form.purchaseDate ?: return form
        val policy = container.policies.find(form.merchant)
        return form.copy(
            returnDeadline = if (form.returnSetByUser) form.returnDeadline
            else DeadlineCalculator.returnDeadline(date, snap?.returnHint, policy, form.category),
            warrantyDeadline = if (form.warrantySetByUser) form.warrantyDeadline
            else DeadlineCalculator.warrantyDeadline(date, snap?.warranty, form.category),
        )
    }

    // ---- edits from the UI

    private fun edit(field: String, change: (ReviewForm) -> ReviewForm) = _state.update {
        it.copy(form = change(it.form), needsCheck = it.needsCheck - field, error = null)
    }

    fun onMerchant(v: String) = edit("merchant") { withComputedDeadlines(it.copy(merchant = v)) }
    fun onDate(v: LocalDate) = edit("purchaseDate") { withComputedDeadlines(it.copy(purchaseDate = v)) }
    fun onTotal(v: String) = edit("total") { it.copy(total = v.filter { c -> c.isDigit() || c == '.' || c == ',' }) }
    fun onCurrency(v: String) = edit("currency") { it.copy(currency = v.uppercase().take(3)) }
    fun onCategory(v: Category) = edit("category") { withComputedDeadlines(it.copy(category = v)) }
    fun onSerial(v: String) = edit("serial") { it.copy(serialNumber = v) }

    fun onReturnDays(days: Int) = edit("returnHint") { f ->
        val d = f.purchaseDate ?: LocalDate.now()
        f.copy(returnDeadline = Deadline.On(d.plusDays(days.toLong()), DeadlineSource.USER, "$days days, set by you"), returnSetByUser = true)
    }
    fun onReturnDate(date: LocalDate) = edit("returnHint") {
        it.copy(returnDeadline = Deadline.On(date, DeadlineSource.USER, "Set by you"), returnSetByUser = true)
    }
    fun onNoReturns() = edit("returnHint") {
        it.copy(returnDeadline = Deadline.NotAllowed(DeadlineSource.USER), returnSetByUser = true)
    }
    fun onWarrantyMonths(months: Int) = edit("warranty") { f ->
        val d = f.purchaseDate ?: LocalDate.now()
        f.copy(warrantyDeadline = Deadline.On(d.plusMonths(months.toLong()), DeadlineSource.USER, "$months months, set by you"), warrantySetByUser = true)
    }
    fun onNoWarranty() = edit("warranty") { it.copy(warrantyDeadline = Deadline.Unknown, warrantySetByUser = true) }

    fun save(onSaved: (String) -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        val f = s.form
        val totalMinor = f.total.replace(',', '.').toBigDecimalOrNull()?.let { Money.of(it, f.currency.ifBlank { "USD" }).minorUnits }
        val ret = f.returnDeadline as? Deadline.On
        val war = f.warrantyDeadline as? Deadline.On
        val reviewed = ReviewedReceipt(
            merchantName = f.merchant.trim(),
            purchaseDate = f.purchaseDate!!,
            totalMinor = totalMinor,
            currency = f.currency.ifBlank { "USD" },
            category = f.category,
            returnBy = ret?.date,
            returnNote = ret?.note,
            noReturns = f.returnDeadline is Deadline.NotAllowed,
            warrantyEnds = war?.date,
            warrantyNote = war?.note,
            lifetimeWarranty = f.warrantyDeadline is Deadline.Lifetime,
            items = f.items.map { ReviewedItem(it.name, it.quantity, it.totalMinor, it.unitMinor) },
            serialNumber = f.serialNumber.ifBlank { null },
            orderNumber = f.orderNumber.ifBlank { null },
            paymentLabel = f.paymentLabel,
            notes = null,
        )
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { container.repository.saveReviewed(receiptId, reviewed) }
                .onSuccess { reminders ->
                    _state.update { it.copy(saved = true) }
                    kotlinx.coroutines.delay(650)
                    onSaved(savedMessage(reviewed, reminders.filter { it.kind == ReminderKind.RETURN }.map { it.fireAt.toLocalDate() }))
                }
                .onFailure { e -> _state.update { it.copy(saving = false, error = "Couldn't save: ${e.message}") } }
        }
    }

    /** "Return by Fri, Dec 24 · reminders Dec 17, 21, 23" */
    private fun savedMessage(r: ReviewedReceipt, returnReminderDates: List<LocalDate>): String {
        val day = DateTimeFormatter.ofPattern("EEE, MMM d")
        val short = DateTimeFormatter.ofPattern("MMM d")
        return when {
            r.returnBy != null && returnReminderDates.isNotEmpty() ->
                "Saved. Reminders set for ${joinDates(returnReminderDates.sorted().map { it.format(short) })}."
            r.returnBy != null -> "Return by ${r.returnBy.format(day)}"
            r.warrantyEnds != null -> "Saved · warranty until ${r.warrantyEnds.format(short)}"
            else -> "Saved"
        }
    }

    companion object {
        fun factory(container: AppContainer, receiptId: String) =
            viewModelFactory { initializer { ReviewViewModel(container, receiptId) } }
    }
}

private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()

/** "Dec 1, Dec 5 and Dec 7". */
internal fun joinDates(parts: List<String>): String = when (parts.size) {
    0 -> ""
    1 -> parts[0]
    else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
}
