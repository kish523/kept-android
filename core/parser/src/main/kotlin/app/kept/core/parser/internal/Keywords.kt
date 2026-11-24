package app.kept.core.parser.internal

private val I = RegexOption.IGNORE_CASE

/**
 * Keyword patterns. English first, with the most common French, German, Spanish, Italian and Dutch
 * receipt words. Add languages here; every extractor reads from this file.
 */
internal object Kw {
    val SUBTOTAL = Regex(
        """\b(sub\s*-?\s*total|taxable\s*(value|amount|amt)|zwischensumme|sous[-\s]?total|subtotale)\b""",
        I,
    )

    val TOTAL_STRONG = Regex(
        """\b(grand\s*total|total\s*due|amount\s*due|balance\s*due|total\s*amount|amount\s*payable|""" +
            """total\s*payable|net\s*payable|order\s*total|invoice\s*total|total\s*to\s*pay|net\s*amo?u?n?t|""" +
            """bill\s*amount|total\s*amt|amount\s*to\s*pay|summe|""" +
            """gesamtbetrag|montant\s*total|importe\s*total|totale)\b""",
        I,
    )
    val TOTAL_WEAK = Regex("""\b(net\s*total|total|gesamt|totaal)\b""", I)
    val TOTAL_EXCLUDE = Regex(
        """\b(total\s*(tax|vat|savings?|saved|discount|items?|qty|quantity|points|number|count|weight|before|excl\w*)|""" +
            """items?\s*total|tax\s*total)\b|you\s+saved""",
        I,
    )

    val TAX = Regex("""\b(sales\s*tax|tax|vat|gst|cgst|sgst|igst|utgst|cess|hst|pst|sst|tva|mwst|ust|iva|btw)\b""", I)
    val TAX_EXCLUDE = Regex(
        """tax\s*invoice|taxable|\b(vat|gst|tax|tva|btw)\s*(reg\w*|no\b|no\.|number|id\b|#)|gstin|tax\s*exempt|""" +
            """excl\.?\s*(vat|tax)""",
        I,
    )

    /** Tender lines: they repeat or exceed the total, so they must never be mistaken for it. */
    val PAYMENT_ROW = Regex(
        """\b(cash|change|tender(ed)?|paid|payment|card|visa|master\s*card|amex|debit|credit|auth\w*|approval|balance)\b""",
        I,
    )

    /** Rows that carry a price but are not purchased items. */
    val NON_ITEM = Regex(
        """\b(total|tax|vat|gst|tva|change|cash|card|visa|master\s*card|amex|tender(ed)?|balance|due|paid|""" +
            """payment|discount|savings?|saved|points|auth\w*|approval|tel|phone|date|time|cashier|receipt|""" +
            """invoice|thank|returns?|refund|exchange|warranty|subtotal|rounding)\b""",
        I,
    )
}
