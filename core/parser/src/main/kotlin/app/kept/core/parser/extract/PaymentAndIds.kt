package app.kept.core.parser.extract

import app.kept.core.parser.internal.DateParser
import app.kept.core.parser.DateOrder
import app.kept.core.parser.internal.Row
import app.kept.core.parser.model.Field
import app.kept.core.parser.model.PaymentInfo

internal object PaymentExtractor {
    private val I = RegexOption.IGNORE_CASE
    private val BRANDS = listOf(
        Regex("""\bvisa\b""", I) to "Visa",
        Regex("""\bmaster\s*card\b|\bmc\b""", I) to "Mastercard",
        Regex("""\bamex\b|american\s+express""", I) to "Amex",
        Regex("""\bdiscover\b""", I) to "Discover",
        Regex("""\bunion\s*pay\b""", I) to "UnionPay",
        Regex("""\brupay\b""", I) to "RuPay",
        Regex("""\bapple\s*pay\b""", I) to "Apple Pay",
        Regex("""\bgoogle\s*pay\b|\bgpay\b""", I) to "Google Pay",
        Regex("""\bpaypal\b""", I) to "PayPal",
        Regex("""\bupi\b""", I) to "UPI",
        Regex("""\b(debit|credit)\s*card\b""", I) to "Card",
        Regex("""\bcash\b""", I) to "Cash",
    )
    private val LAST4 = listOf(
        Regex("""(?<![A-Za-z])(?:[*xX•#]{2,}[\s-]*)+(\d{4})(?!\d)"""),
        Regex("""\b(?:ending|ends)\s*(?:in|with)?\s*[:#]?\s*(\d{4})(?!\d)""", I),
    )

    fun extract(rows: List<Row>): Field<PaymentInfo>? {
        var label: Pair<String, Row>? = null
        var last4: Pair<String, Row>? = null
        // Payment lines sit near the bottom: scan upwards.
        for (row in rows.asReversed()) {
            if (last4 == null) {
                LAST4.firstNotNullOfOrNull { it.find(row.text)?.groupValues?.get(1) }?.let { last4 = it to row }
            }
            val brand = BRANDS.firstOrNull { it.first.containsMatchIn(row.text) }?.second
            if (brand != null && (label == null || (label!!.first == "Cash" && brand != "Cash"))) {
                label = brand to row
            }
        }
        val l = label
        val d = last4
        return when {
            l != null && d != null -> Field(PaymentInfo(l.first, d.first), 0.85f, d.second.text)
            l != null -> Field(PaymentInfo(l.first, null), 0.75f, l.second.text)
            d != null -> Field(PaymentInfo("Card", d.first), 0.7f, d.second.text)
            else -> null
        }
    }
}

internal object IdentifierExtractor {
    private val I = RegexOption.IGNORE_CASE
    private val ORDER = Regex(
        """\b(order|ord|invoice|inv|receipt|rcpt|ticket|transaction|trans|txn|bill|ref(?:erence)?)\b\.?\s*""" +
            """(?:no\b\.?|number|num|#|id)?\s*[:#.]?\s*([A-Z0-9][A-Z0-9/-]{3,39})""",
        I,
    )
    private val SERIAL = Regex(
        """\b(?:s\s*/\s*n|serial(?:\s*(?:no\b\.?|number|#))?|imei\d?)\s*[:#.]?\s*([A-Z0-9][A-Z0-9-]{4,39})""",
        I,
    )

    private fun priority(keyword: String): Float = when (keyword.lowercase()) {
        "order", "ord" -> 0.85f
        "invoice", "inv" -> 0.8f
        "ref", "reference" -> 0.6f
        else -> 0.7f
    }

    fun orderNumber(rows: List<Row>): Field<String>? = rows.flatMap { row ->
        ORDER.findAll(row.text).mapNotNull { m ->
            val value = m.groupValues[2].trimEnd('-', '/')
            if (value.none { it.isDigit() }) return@mapNotNull null
            if (DateParser.find(value, DateOrder.DMY).isNotEmpty()) return@mapNotNull null
            Field(value, priority(m.groupValues[1]), row.text)
        }.toList()
    }.maxByOrNull { it.confidence }

    fun serialNumbers(rows: List<Row>): List<Field<String>> = rows.flatMap { row ->
        SERIAL.findAll(row.text).mapNotNull { m ->
            m.groupValues[1].takeIf { v -> v.any { it.isDigit() } }?.let { Field(it, 0.8f, row.text) }
        }.toList()
    }.distinctBy { it.value }
}
