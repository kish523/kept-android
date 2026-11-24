package app.kept.core.parser.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Currency

/** Where a value came from. The review card shows this so users know why a value was chosen. */
enum class FieldSource { RECEIPT_TEXT, DERIVED, CONFIG_DEFAULT }

/** An extracted value plus how sure the parser is about it (0..1). */
data class Field<T>(
    val value: T,
    val confidence: Float,
    /** The receipt line the value was read from, for the review card and debugging. */
    val evidence: String? = null,
    val source: FieldSource = FieldSource.RECEIPT_TEXT,
) {
    /** Below the threshold the review card highlights the field in amber with the word "Check". */
    val needsReview: Boolean get() = confidence < REVIEW_THRESHOLD

    fun withConfidence(c: Float): Field<T> = copy(confidence = c.coerceIn(0f, 1f))

    companion object {
        const val REVIEW_THRESHOLD = 0.7f
    }
}

/** Money as integer minor units plus an ISO 4217 code. Never a Double. */
data class Money(val minorUnits: Long, val currency: String) : Comparable<Money> {
    val fractionDigits: Int get() = fractionDigitsOf(currency)

    fun toDecimal(): BigDecimal = BigDecimal.valueOf(minorUnits, fractionDigits)

    override fun compareTo(other: Money): Int = minorUnits.compareTo(other.minorUnits)

    override fun toString(): String = "${toDecimal().toPlainString()} $currency"

    companion object {
        fun fractionDigitsOf(code: String): Int =
            runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrNull()
                ?.takeIf { it >= 0 } ?: 2

        fun of(amount: BigDecimal, currency: String): Money = Money(
            amount.setScale(fractionDigitsOf(currency), RoundingMode.HALF_UP).unscaledValue().toLong(),
            currency,
        )

        fun of(amount: String, currency: String): Money = of(BigDecimal(amount), currency)
    }
}

enum class DocType { RECEIPT, INVOICE, ORDER_CONFIRMATION, WARRANTY_CARD }

enum class Category { ELECTRONICS, APPLIANCES, HOME, FURNITURE, CLOTHING, TOOLS, GROCERIES, OTHER }

data class PaymentInfo(
    /** "Visa", "Mastercard", "Cash", "Card", ... */
    val label: String,
    /** Only ever the last four digits. Full card numbers are redacted before parsing. */
    val cardLast4: String?,
)

data class LineItem(
    val name: String,
    val quantity: Int,
    val unitPrice: Money?,
    val totalPrice: Money,
    val confidence: Float,
    val evidence: String,
)

/** What the receipt itself says about returns. Merchant tables and defaults are applied later. */
sealed interface ReturnHint {
    data class ByDate(val date: LocalDate) : ReturnHint
    data class WithinDays(val days: Int) : ReturnHint
    data object NoReturns : ReturnHint
}

sealed interface WarrantyHint {
    data class Months(val months: Int) : WarrantyHint
    data object Lifetime : WarrantyHint
}

data class ParsedReceipt(
    val docType: Field<DocType>,
    val merchant: Field<String>?,
    val purchaseDate: Field<LocalDate>?,
    val currency: Field<String>,
    val total: Field<Money>?,
    val subtotal: Field<Money>?,
    val tax: Field<Money>?,
    val payment: Field<PaymentInfo>?,
    val orderNumber: Field<String>?,
    val serialNumbers: List<Field<String>>,
    val items: List<LineItem>,
    val returnHint: Field<ReturnHint>?,
    val warranty: Field<WarrantyHint>?,
    val suggestedCategory: Field<Category>?,
    /** Recognised text with any full card number replaced by "•••• 1234". Safe to store. */
    val redactedLines: List<String>,
    val warnings: List<String>,
) {
    /** Core fields the review card must highlight: missing or below the confidence threshold. */
    val fieldsNeedingReview: List<String>
        get() = buildList {
            if (merchant == null || merchant.needsReview) add("merchant")
            if (purchaseDate == null || purchaseDate.needsReview) add("purchaseDate")
            if (total == null || total.needsReview) add("total")
            if (currency.needsReview) add("currency")
        }
}
