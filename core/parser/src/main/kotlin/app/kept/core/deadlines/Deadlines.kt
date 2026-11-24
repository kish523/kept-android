package app.kept.core.deadlines

import app.kept.core.parser.model.Category
import app.kept.core.parser.model.ReturnHint
import app.kept.core.parser.model.WarrantyHint
import java.time.LocalDate

/** A store's return policy from the bundled table (or the user's own override). */
data class MerchantPolicy(
    val name: String,
    val aliases: List<String> = emptyList(),
    val returnDays: Int?,
    /** Some stores give electronics a shorter window than everything else. */
    val categoryReturnDays: Map<Category, Int> = emptyMap(),
    /** Shown in the app as "policy as of <date>", because policies change. */
    val asOf: LocalDate? = null,
    val returnsUrl: String? = null,
)

class MerchantPolicyTable(private val policies: List<MerchantPolicy>) {

    fun find(merchantName: String?): MerchantPolicy? {
        val key = normalize(merchantName ?: return null)
        if (key.length < 3) return null
        // Exact, or the scanned name extends a known alias ("VoltMart Electronics" -> "voltmart").
        // Never the other way round: a partial read like "Volt" must not pick up a store's policy.
        return policies.firstOrNull { p ->
            (listOf(p.name) + p.aliases).any { alias ->
                val k = normalize(alias)
                k.length >= 4 && (key == k || key.startsWith(k))
            }
        }
    }

    /** Alias -> display name, for [app.kept.core.parser.ParserConfig.knownMerchants]. */
    fun aliasMap(): Map<String, String> =
        policies.flatMap { p -> (listOf(p.name) + p.aliases).map { it.lowercase() to p.name } }.toMap()

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}

enum class DeadlineSource { RECEIPT_DATE, RECEIPT_TEXT, MERCHANT_POLICY, CATEGORY_DEFAULT, USER }

sealed interface Deadline {
    data class On(val date: LocalDate, val source: DeadlineSource, val note: String) : Deadline
    /** "No returns" printed on the receipt. */
    data class NotAllowed(val source: DeadlineSource) : Deadline
    data object Lifetime : Deadline
    /** Nothing known: the review card asks the user with one-tap chips. */
    data object Unknown : Deadline
}

object DeadlineCalculator {

    /** Typical manufacturer warranty when the receipt doesn't say. Shown to the user as a default. */
    val DEFAULT_WARRANTY_MONTHS: Map<Category, Int> = mapOf(
        Category.ELECTRONICS to 12,
        Category.APPLIANCES to 12,
        Category.TOOLS to 12,
        Category.FURNITURE to 12,
    )

    fun returnDeadline(
        purchaseDate: LocalDate,
        hint: ReturnHint?,
        policy: MerchantPolicy?,
        category: Category?,
    ): Deadline {
        when (hint) {
            is ReturnHint.ByDate -> return Deadline.On(hint.date, DeadlineSource.RECEIPT_DATE, "Printed on the receipt")
            ReturnHint.NoReturns -> return Deadline.NotAllowed(DeadlineSource.RECEIPT_TEXT)
            else -> Unit
        }
        val options = buildList {
            if (hint is ReturnHint.WithinDays) {
                add(Deadline.On(purchaseDate.plusDays(hint.days.toLong()), DeadlineSource.RECEIPT_TEXT, "${hint.days} days, from the receipt"))
            }
            if (policy != null) {
                val days = category?.let { policy.categoryReturnDays[it] } ?: policy.returnDays
                if (days != null) {
                    add(Deadline.On(purchaseDate.plusDays(days.toLong()), DeadlineSource.MERCHANT_POLICY, "$days days, ${policy.name} policy"))
                }
            }
        }
        // When sources disagree the shorter window wins: an early reminder is harmless, a late one isn't.
        return options.minByOrNull { it.date } ?: Deadline.Unknown
    }

    fun warrantyDeadline(
        purchaseDate: LocalDate,
        hint: WarrantyHint?,
        category: Category?,
        defaults: Map<Category, Int> = DEFAULT_WARRANTY_MONTHS,
    ): Deadline = when (hint) {
        is WarrantyHint.Months -> Deadline.On(
            purchaseDate.plusMonths(hint.months.toLong()),
            DeadlineSource.RECEIPT_TEXT,
            "${hint.months} months, from the receipt",
        )
        WarrantyHint.Lifetime -> Deadline.Lifetime
        null -> category?.let { defaults[it] }?.let { months ->
            Deadline.On(purchaseDate.plusMonths(months.toLong()), DeadlineSource.CATEGORY_DEFAULT, "Usual $months-month warranty")
        } ?: Deadline.Unknown
    }
}
