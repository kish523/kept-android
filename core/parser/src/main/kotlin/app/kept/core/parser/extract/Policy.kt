package app.kept.core.parser.extract

import app.kept.core.parser.ParserConfig
import app.kept.core.parser.internal.DateParser
import app.kept.core.parser.internal.Row
import app.kept.core.parser.model.Field
import app.kept.core.parser.model.ReturnHint
import app.kept.core.parser.model.WarrantyHint

/** What the receipt itself says about returns and warranty. */
internal object PolicyExtractor {
    private val I = RegexOption.IGNORE_CASE
    private val NO_RETURNS = Regex(
        """\bno\s+(returns?|refunds?|exchanges?)\b|all\s+sales\s+(are\s+)?final|non[-\s]?(returnable|refundable)|""" +
            """goods\s+once\s+sold|not\s+be\s+(taken\s+back|returned|exchanged)""",
        I,
    )
    private val RETURN_WORD = Regex(
        """\b(returns?|returned|refunds?|exchanges?|échange|echange|retour|umtausch|devoluci[oó]n|cambio)\b""",
        I,
    )
    private val BY_WORD = Regex("""\b(by|until|before|through|thru|closes?|ends?|deadline|no\s+later\s+than)\b""", I)
    private val DAYS = Regex("""(?<!\d)(\d{1,3})\s*-?\s*(days?|jours|tagen?|d[ií]as|dagen)\b""", I)
    private val WARRANTY_WORD = Regex("""\b(warrant(y|ies)|guarantee|garantie|garant[ií]a)\b""", I)
    private val PERIOD = Regex(
        """(?<!\d)(\d{1,2}|one|two|three|four|five|six|ten)\s*-?\s*(years?|yrs?|months?|mos?|ans?|jahre?|monate?)\b""",
        I,
    )
    private val LIFETIME = Regex("""\blifetime\b""", I)
    private val WORD_NUMBERS = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "ten" to 10)

    fun returnHint(rows: List<Row>, config: ParserConfig): Field<ReturnHint>? {
        // 1. An explicit "return by <date>" beats everything.
        for (row in rows) {
            if (RETURN_WORD.containsMatchIn(row.text) && BY_WORD.containsMatchIn(row.text)) {
                val date = DateParser.find(row.text, config.dateOrder).firstOrNull()?.date
                if (date != null && !date.isBefore(config.today.minusYears(2))) {
                    return Field(ReturnHint.ByDate(date), 0.9f, row.text)
                }
            }
        }
        // 2. "No returns" / "Goods once sold will not be taken back".
        rows.firstOrNull { NO_RETURNS.containsMatchIn(it.text) }?.let {
            return Field(ReturnHint.NoReturns, 0.8f, it.text)
        }
        // 3. "Returns accepted within 30 days" (the number may wrap onto the next line).
        for (row in rows) {
            if (!RETURN_WORD.containsMatchIn(row.text)) continue
            val text = row.text + " " + (rows.getOrNull(row.index + 1)?.text ?: "")
            val days = DAYS.find(text)?.groupValues?.get(1)?.toIntOrNull()
            if (days != null && days in 1..365) return Field(ReturnHint.WithinDays(days), 0.8f, row.text)
        }
        return null
    }

    fun warranty(rows: List<Row>): Field<WarrantyHint>? {
        for (row in rows) {
            if (!WARRANTY_WORD.containsMatchIn(row.text)) continue
            if (LIFETIME.containsMatchIn(row.text)) return Field(WarrantyHint.Lifetime, 0.75f, row.text)
            val m = PERIOD.find(row.text) ?: continue
            val n = m.groupValues[1].toIntOrNull() ?: WORD_NUMBERS[m.groupValues[1].lowercase()] ?: continue
            val unit = m.groupValues[2].lowercase()
            val months = if (unit.startsWith("m")) n else n * 12
            if (months in 1..240) return Field(WarrantyHint.Months(months), 0.8f, row.text)
        }
        return null
    }
}
