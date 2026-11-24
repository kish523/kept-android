package app.kept.core.parser.extract

import app.kept.core.parser.internal.Kw
import app.kept.core.parser.internal.MoneyParser
import app.kept.core.parser.internal.Row
import app.kept.core.parser.internal.letterCount
import app.kept.core.parser.model.Field
import app.kept.core.parser.model.FieldSource
import app.kept.core.parser.model.Money
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class TotalsResult(
    val total: Field<Money>?,
    val subtotal: Field<Money>?,
    val tax: Field<Money>?,
    /** First subtotal or total row: line items live above it. */
    val itemsEndRow: Int?,
    val warnings: List<String>,
)

internal object TotalsExtractor {

    fun extract(rows: List<Row>, currency: String): TotalsResult {
        val warnings = mutableListOf<String>()

        // ---- total: keyword rows, later rows preferred (the real total sits below the subtotal)
        data class Candidate(val row: Row, val money: Money, val confidence: Float, val score: Float)
        val candidates = rows.mapNotNull { row ->
            val t = row.text
            if (Kw.SUBTOTAL.containsMatchIn(t) || Kw.TOTAL_EXCLUDE.containsMatchIn(t)) return@mapNotNull null
            val base = when {
                Kw.TOTAL_STRONG.containsMatchIn(t) -> 0.85f
                Kw.TOTAL_WEAK.containsMatchIn(t) -> 0.75f
                else -> return@mapNotNull null
            }
            val (money, factor) = amountFor(rows, row.index, currency) ?: return@mapNotNull null
            if (money.minorUnits <= 0) return@mapNotNull null
            val position = if (rows.size > 1) row.index.toFloat() / (rows.size - 1) else 1f
            val confidence = base * factor * row.ocrFactor
            Candidate(row, money, confidence, confidence * (0.8f + 0.2f * position))
        }
        val best = candidates.maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { it.money.minorUnits })
        var total: Field<Money>? = best?.let { Field(it.money, it.confidence, it.row.text) }

        // ---- subtotal
        var subtotal: Field<Money>? = rows.firstNotNullOfOrNull { row ->
            if (!Kw.SUBTOTAL.containsMatchIn(row.text)) return@firstNotNullOfOrNull null
            amountFor(rows, row.index, currency)?.let { (m, f) -> Field(m, 0.8f * f * row.ocrFactor, row.text) }
        }

        // ---- tax: every tax row that isn't really a total line ("Total incl. VAT")
        val taxRows = rows.filter { row ->
            val t = row.text
            val isTotalLine = !Kw.TOTAL_EXCLUDE.containsMatchIn(t) &&
                (Kw.TOTAL_STRONG.containsMatchIn(t) || Kw.TOTAL_WEAK.containsMatchIn(t))
            Kw.TAX.containsMatchIn(t) && !Kw.TAX_EXCLUDE.containsMatchIn(t) &&
                !Kw.SUBTOTAL.containsMatchIn(t) && !isTotalLine
        }.mapNotNull { row -> amountFor(rows, row.index, currency)?.let { row to it.first } }
            .filter { it.second.minorUnits > 0 }
            .distinctBy { it.second.minorUnits }
        var tax: Field<Money>? = when (taxRows.size) {
            0 -> null
            1 -> Field(taxRows[0].second, 0.75f, taxRows[0].first.text)
            else -> Field(
                Money(taxRows.sumOf { it.second.minorUnits }, currency),
                0.6f,
                taxRows.joinToString(" | ") { it.first.text },
            )
        }

        // ---- cross-check: subtotal (+ tax) should equal the total
        if (total != null && subtotal != null) {
            val t = total.value.minorUnits
            val s = subtotal.value.minorUnits
            val withTax = s + (tax?.value?.minorUnits ?: 0L)
            when {
                close(withTax, t) -> {
                    total = total.withConfidence(max(total.confidence, 0.95f))
                    subtotal = subtotal.withConfidence(0.9f)
                    tax = tax?.withConfidence(0.9f)
                }
                close(s, t) -> { // tax already included (VAT receipts)
                    total = total.withConfidence(max(total.confidence, 0.9f))
                    subtotal = subtotal.withConfidence(0.85f)
                }
                t < s -> {
                    warnings += "Total is smaller than the subtotal; check both."
                    total = total.withConfidence(min(total.confidence, 0.45f))
                }
            }
        }

        // ---- fallback: no total keyword (faded or unusual receipts). Largest price in the lower part,
        // ignoring cash / change / card lines, which can exceed the real total.
        if (total == null) {
            val start = (rows.size * 0.4).toInt()
            val fallback = rows.drop(start)
                .filterNot { Kw.PAYMENT_ROW.containsMatchIn(it.text) }
                .flatMap { row -> MoneyParser.moneyTokens(row.text).map { row to it } }
                .filter { it.second.value.signum() > 0 }
                .maxByOrNull { it.second.value }
            if (fallback != null) {
                total = Field(Money.of(fallback.second.value, currency), 0.35f, fallback.first.text, FieldSource.DERIVED)
                warnings += "No total line found; used the largest amount. Please check it."
            } else {
                warnings += "No total found."
            }
        }

        val itemsEnd = rows.firstOrNull { Kw.SUBTOTAL.containsMatchIn(it.text) }?.index
            ?: best?.row?.index
            ?: rows.firstOrNull { Kw.PAYMENT_ROW.containsMatchIn(it.text) && MoneyParser.moneyTokens(it.text).isNotEmpty() }?.index

        return TotalsResult(total, subtotal, tax, itemsEnd, warnings)
    }

    /** Rightmost price on the row; if the row is only a label, the price on the next row. */
    private fun amountFor(rows: List<Row>, index: Int, currency: String): Pair<Money, Float>? {
        MoneyParser.moneyTokens(rows[index].text).lastOrNull()?.let { return Money.of(it.value, currency) to 1f }
        val next = rows.getOrNull(index + 1) ?: return null
        if (next.page != rows[index].page || next.text.letterCount() > 3) return null
        return MoneyParser.moneyTokens(next.text).lastOrNull()?.let { Money.of(it.value, currency) to 0.85f }
    }

    /** Equal within 1% or two minor units, to absorb rounding. */
    fun close(a: Long, b: Long): Boolean = abs(a - b) <= max(2L, abs(b) / 100)
}
