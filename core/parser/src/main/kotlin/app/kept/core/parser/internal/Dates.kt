package app.kept.core.parser.internal

import app.kept.core.parser.DateOrder
import java.time.LocalDate

internal data class DateMatch(val date: LocalDate, val ambiguous: Boolean, val range: IntRange)

internal object DateParser {
    private val MONTHS = mapOf(
        "january" to 1, "february" to 2, "march" to 3, "april" to 4, "may" to 5, "june" to 6,
        "july" to 7, "august" to 8, "september" to 9, "october" to 10, "november" to 11, "december" to 12,
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "jun" to 6, "jul" to 7, "aug" to 8,
        "sep" to 9, "sept" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    // Longest names first so "sept" wins over "sep"; the lookahead stops "mar" matching "market".
    private val MON = "(" + MONTHS.keys.sortedByDescending { it.length }.joinToString("|") + ")\\.?(?![a-z])"

    private val ISO = Regex("""(?<!\d)(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?!\d)""")
    private val NUMERIC = Regex("""(?<!\d)(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})(?!\d)""")
    private val DAY_MON_YEAR = Regex(
        """(?<!\d)(\d{1,2})(?:st|nd|rd|th)?(?:\s+|[-/.])$MON(?:,?\s+|[-/.])(\d{4}|\d{2})(?!\d)""",
        RegexOption.IGNORE_CASE,
    )
    private val MON_DAY_YEAR = Regex(
        """\b$MON\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})(?!\d)""",
        RegexOption.IGNORE_CASE,
    )

    fun find(text: String, order: DateOrder): List<DateMatch> {
        val found = mutableListOf<DateMatch>()
        fun add(range: IntRange, build: () -> DateMatch?) {
            if (found.any { it.range.first <= range.last && range.first <= it.range.last }) return
            build()?.let { found += it }
        }
        for (m in ISO.findAll(text)) add(m.range) {
            date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                ?.let { DateMatch(it, false, m.range) }
        }
        for (m in DAY_MON_YEAR.findAll(text)) add(m.range) {
            val month = MONTHS[m.groupValues[2].lowercase()] ?: return@add null
            date(year(m.groupValues[3]), month, m.groupValues[1].toInt())?.let { DateMatch(it, false, m.range) }
        }
        for (m in MON_DAY_YEAR.findAll(text)) add(m.range) {
            val month = MONTHS[m.groupValues[1].lowercase()] ?: return@add null
            date(m.groupValues[3].toInt(), month, m.groupValues[2].toInt())?.let { DateMatch(it, false, m.range) }
        }
        for (m in NUMERIC.findAll(text)) add(m.range) {
            numeric(m.groupValues[1].toInt(), m.groupValues[2].toInt(), year(m.groupValues[3]), order)
                ?.let { (d, ambiguous) -> DateMatch(d, ambiguous, m.range) }
        }
        return found.sortedBy { it.range.first }
    }

    private fun numeric(a: Int, b: Int, y: Int, order: DateOrder): Pair<LocalDate, Boolean>? = when {
        a > 12 && b <= 12 -> date(y, b, a)?.let { it to false }
        b > 12 && a <= 12 -> date(y, a, b)?.let { it to false }
        a == b -> date(y, a, a)?.let { it to false }
        order == DateOrder.MDY -> date(y, a, b)?.let { it to true }
        else -> date(y, b, a)?.let { it to true } // DMY, and YMD locales rarely print day-first-less dates
    }

    private fun year(s: String): Int = if (s.length == 2) 2000 + s.toInt() else s.toInt()

    private fun date(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()
}
