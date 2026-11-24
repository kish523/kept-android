package app.kept.core.parser

import java.time.LocalDate
import java.util.Currency
import java.util.Locale

/** How to read a numeric date like 03/04/2026 when both parts could be the month. */
enum class DateOrder { DMY, MDY, YMD }

data class ParserConfig(
    /** ISO 4217 code used when the receipt shows no currency, or only "$" / "Rs" / "¥". */
    val defaultCurrency: String,
    val dateOrder: DateOrder,
    /** Purchase dates after today (+1 day for time zones) are rejected as misreads. */
    val today: LocalDate,
    /** Lower-case alias -> display name, e.g. "voltmart" -> "VoltMart Electronics". */
    val knownMerchants: Map<String, String> = emptyMap(),
) {
    companion object {
        private val MDY_COUNTRIES = setOf("US", "PH", "FM", "MH", "PW", "BZ")
        private val YMD_COUNTRIES = setOf("CN", "JP", "KR", "TW", "HU", "LT", "MN")

        fun forLocale(
            locale: Locale,
            today: LocalDate,
            knownMerchants: Map<String, String> = emptyMap(),
        ): ParserConfig {
            val currency = runCatching { Currency.getInstance(locale).currencyCode }.getOrNull() ?: "USD"
            val order = when (locale.country.uppercase()) {
                in MDY_COUNTRIES -> DateOrder.MDY
                in YMD_COUNTRIES -> DateOrder.YMD
                else -> DateOrder.DMY
            }
            return ParserConfig(currency, order, today, knownMerchants)
        }
    }
}
