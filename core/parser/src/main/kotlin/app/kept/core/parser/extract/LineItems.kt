package app.kept.core.parser.extract

import app.kept.core.parser.internal.Kw
import app.kept.core.parser.internal.MoneyParser
import app.kept.core.parser.internal.Row
import app.kept.core.parser.internal.letterCount
import app.kept.core.parser.internal.normalizeSpaces
import app.kept.core.parser.model.LineItem
import app.kept.core.parser.model.Money
import java.math.BigDecimal
import java.math.RoundingMode

internal object LineItemExtractor {
    private val QTY_PREFIX = Regex("""^(\d{1,3})\s*[xX×]\s+""")
    private val QTY_LABEL = Regex("""\bqty\s*[:.]?\s*(\d{1,3})\b""", RegexOption.IGNORE_CASE)
    private val UNIT_AT = Regex("""\s*@\s*\S{0,4}?\s*(\d+[.,]\d{2})""")
    /** A quantity column: a small number separated from the name by 2+ spaces, right before the price. */
    private val QTY_COLUMN = Regex("""\s{2,}(\d{1,3})\s*$""")
    private const val MAX_ITEMS = 100

    /** Rows above [endRow] that end with a price. Discounts and negative lines are skipped in v1. */
    fun extract(rows: List<Row>, currency: String, endRow: Int?): List<LineItem> {
        val end = endRow ?: rows.size
        val items = mutableListOf<LineItem>()
        var lastNameRow: Int? = null

        for (row in rows) {
            if (row.index >= end || items.size >= MAX_ITEMS) break
            val text = row.text
            val tokens = MoneyParser.moneyTokens(text)
            if (Kw.NON_ITEM.containsMatchIn(text)) {
                lastNameRow = null
                continue
            }
            if (tokens.isEmpty()) {
                // Possibly a name whose price is on the next row ("Qty: 1  $149.99").
                lastNameRow = if (text.letterCount() >= 3) row.index else null
                continue
            }
            val price = tokens.last()
            // The price must be the last thing on the row (a tax flag like "A" or "*" may follow).
            if (text.substring(price.end).trim().length > 3) continue
            if (price.value.signum() <= 0) continue

            var name = text.substring(0, price.start)
            var quantity = 1
            var unit: BigDecimal? = null

            QTY_PREFIX.find(name.trim())?.let { m ->
                quantity = m.groupValues[1].toInt()
                name = name.trim().substring(m.range.last + 1)
            }
            QTY_LABEL.find(name)?.let { m ->
                quantity = m.groupValues[1].toInt()
                name = name.removeRange(m.range)
            }
            UNIT_AT.find(name)?.let { m ->
                unit = MoneyParser.parseNumber(m.groupValues[1])?.first
                name = name.removeRange(m.range)
            }
            QTY_COLUMN.find(name)?.let { m ->
                quantity = m.groupValues[1].toInt()
                name = name.removeRange(m.range)
            }
            name = name.normalizeSpaces().trim('-', ':', '.', ' ')

            if (name.letterCount() < 2) {
                // Price-only row: the name is the row just above it.
                val prev = lastNameRow
                if (prev == null || prev != row.index - 1) continue
                name = rows[prev].text.normalizeSpaces()
            }
            lastNameRow = null
            if (quantity !in 1..999) quantity = 1

            val totalPrice = Money.of(price.value, currency)
            val unitPrice = unit?.let { Money.of(it, currency) }
                ?: if (quantity > 1) Money.of(price.value.divide(BigDecimal(quantity), 2, RoundingMode.HALF_UP), currency) else null
            items += LineItem(name, quantity, unitPrice, totalPrice, 0.7f * row.ocrFactor, text)
        }
        return items
    }
}
