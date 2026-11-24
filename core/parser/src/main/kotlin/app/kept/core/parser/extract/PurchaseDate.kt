package app.kept.core.parser.extract

import app.kept.core.parser.ParserConfig
import app.kept.core.parser.internal.DateParser
import app.kept.core.parser.internal.Row
import app.kept.core.parser.model.Field
import java.time.LocalDate

internal object PurchaseDateExtractor {
    private val I = RegexOption.IGNORE_CASE
    private val PURCHASE_KW = Regex(
        """\b(date|dated|dt|purchased?|order\s*date|invoice\s*date|bill\s*date|sale|transaction|txn|issued|datum|fecha)\b""",
        I,
    )

    /** Rows about deadlines or delivery: their dates are never the purchase date. */
    val DEADLINE_KW = Regex(
        """\b(returns?|exchanges?|refunds?|valid|expir\w*|warrant\w*|guarantee|until|due|deliver\w*|ship\w*|closes?|by)\b""",
        I,
    )

    fun extract(rows: List<Row>, config: ParserConfig): Field<LocalDate>? {
        data class Candidate(val date: LocalDate, val confidence: Float, val row: Row)
        val earliestAllowed = config.today.minusYears(15)
        val latestAllowed = config.today.plusDays(1)

        val candidates = rows.flatMap { row ->
            val purchaseWord = PURCHASE_KW.containsMatchIn(row.text)
            if (DEADLINE_KW.containsMatchIn(row.text) && !purchaseWord) return@flatMap emptyList()
            DateParser.find(row.text, config.dateOrder).mapNotNull { m ->
                if (m.date.isBefore(earliestAllowed) || m.date.isAfter(latestAllowed)) return@mapNotNull null
                var c = if (m.ambiguous) 0.7f else 0.78f
                if (purchaseWord) c += 0.15f
                if (row.index < rows.size * 0.4) c += 0.04f
                Candidate(m.date, (c * row.ocrFactor).coerceAtMost(0.95f), row)
            }
        }
        val best = candidates.maxWithOrNull(
            compareBy<Candidate> { it.confidence }.thenByDescending { it.row.index },
        ) ?: return null
        return Field(best.date, best.confidence, best.row.text)
    }
}
