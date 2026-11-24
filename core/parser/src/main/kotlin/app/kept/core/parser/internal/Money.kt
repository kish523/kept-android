package app.kept.core.parser.internal

import app.kept.core.parser.ParserConfig
import app.kept.core.parser.model.Field
import app.kept.core.parser.model.FieldSource
import java.math.BigDecimal

internal data class AmountToken(
    val value: BigDecimal,
    /** Start of the match, including any currency symbol in front of the number. */
    val start: Int,
    val end: Int,
    val currencyHint: String?,
    /** True when the number has exactly two decimals ("12.99", "12,99"). */
    val hasDecimals: Boolean,
) {
    /** Integers like "2", "95113" or "14" are quantities, zip codes and times unless a symbol says otherwise. */
    val isMoney: Boolean get() = hasDecimals || currencyHint != null
}

internal object MoneyParser {
    private const val SYMBOLS =
        """US\$|A\$|C\$|S\$|NZ\$|HK\$|\$|€|£|¥|₹|₨|රු|₩|฿|₱|Rs\.?|RM|LKR|USD|EUR|GBP|INR|AUD|CAD|SGD|NZD|JPY|CNY|AED|MYR"""

    // group 1: symbol, 2: leading minus, 3: number, 4: trailing minus
    private val TOKEN = Regex(
        """(?<![\w.,])(?:($SYMBOLS)\s*)?(-)?(\d{1,3}(?:[,.']\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)(-)?(?![.,]?\d)(?!\s?%)""",
    )

    fun find(text: String): List<AmountToken> = TOKEN.findAll(text).mapNotNull { m ->
        val (value, decimals) = parseNumber(m.groupValues[3]) ?: return@mapNotNull null
        val negative = m.groupValues[2].isNotEmpty() || m.groupValues[4].isNotEmpty()
        AmountToken(
            value = if (negative) value.negate() else value,
            start = m.range.first,
            end = m.range.last + 1,
            currencyHint = m.groupValues[1].ifEmpty { null },
            hasDecimals = decimals,
        )
    }.toList()

    fun moneyTokens(text: String): List<AmountToken> = find(text).filter { it.isMoney }

    /** "1,234.56" and "1.234,56" -> 1234.56; "1,234" -> 1234 (grouping, not decimals). */
    fun parseNumber(raw: String): Pair<BigDecimal, Boolean>? = runCatching {
        val lastSep = raw.indexOfLast { it == '.' || it == ',' }
        val tail = if (lastSep >= 0) raw.substring(lastSep + 1) else ""
        if (lastSep >= 0 && tail.length in 1..2) {
            val intPart = raw.substring(0, lastSep).filter(Char::isDigit).ifEmpty { "0" }
            BigDecimal("$intPart.$tail") to (tail.length == 2)
        } else {
            BigDecimal(raw.filter(Char::isDigit)) to false
        }
    }.getOrNull()
}

internal object CurrencyDetector {
    private val ISO = listOf(
        "USD", "EUR", "GBP", "LKR", "INR", "PKR", "NPR", "BDT", "MVR", "AUD", "CAD", "SGD", "NZD",
        "HKD", "JPY", "CNY", "KRW", "AED", "SAR", "QAR", "KWD", "MYR", "THB", "IDR", "PHP", "CHF",
        "SEK", "NOK", "DKK", "ZAR",
    )
    private val ISO_RE = Regex("""\b(${ISO.joinToString("|")})\b""")
    private val SYMBOL_RE = Regex(
        """US\$|A\$|C\$|S\$|NZ\$|HK\$|€|£|₹|රු|₩|฿|₱|₨|¥|\$|\bRs\.?(?=\s*\d)|\bRM(?=\s*\d)""",
    )
    private val UNAMBIGUOUS = mapOf(
        "US$" to "USD", "A$" to "AUD", "C$" to "CAD", "S$" to "SGD", "NZ$" to "NZD", "HK$" to "HKD",
        "€" to "EUR", "£" to "GBP", "₹" to "INR", "රු" to "LKR", "₩" to "KRW", "฿" to "THB",
        "₱" to "PHP", "RM" to "MYR",
    )
    /**
     * Local receipts often print no currency at all, and then the phone's currency is almost always
     * right. Highlighting it on every such receipt would nag, so it sits just above the review line.
     * A foreign symbol ("$" on a phone set to rupees) is what lowers confidence instead.
     */
    private const val NO_MARKER_CONFIDENCE = 0.75f

    private val DOLLARS = listOf("USD", "AUD", "CAD", "SGD", "NZD", "HKD")
    private val RUPEES = listOf("INR", "LKR", "PKR", "NPR")
    private val YEN = listOf("JPY", "CNY")

    fun detect(rows: List<Row>, config: ParserConfig): Field<String> {
        data class Vote(var confidence: Float, var count: Int, var evidence: String)
        val votes = linkedMapOf<String, Vote>()
        fun vote(code: String, confidence: Float, evidence: String) {
            val v = votes.getOrPut(code) { Vote(0f, 0, evidence) }
            v.count++
            if (confidence > v.confidence) {
                v.confidence = confidence
                v.evidence = evidence
            }
        }
        for (row in rows) {
            ISO_RE.findAll(row.text).forEach { vote(it.value, 0.95f, row.text) }
            for (m in SYMBOL_RE.findAll(row.text)) {
                val symbol = m.value.removeSuffix(".")
                val known = UNAMBIGUOUS[symbol]
                if (known != null) {
                    vote(known, 0.9f, row.text)
                    continue
                }
                val family = when (symbol) {
                    "$" -> DOLLARS
                    "Rs", "₨" -> RUPEES
                    "¥" -> YEN
                    else -> continue
                }
                if (config.defaultCurrency in family) vote(config.defaultCurrency, 0.8f, row.text)
                else vote(family.first(), 0.5f, row.text)
            }
        }
        val best = votes.entries.maxWithOrNull(
            compareBy<Map.Entry<String, Vote>> { it.value.confidence }.thenBy { it.value.count },
        ) ?: return Field(config.defaultCurrency, NO_MARKER_CONFIDENCE, null, FieldSource.CONFIG_DEFAULT)
        return Field(best.key, best.value.confidence, best.value.evidence)
    }
}
