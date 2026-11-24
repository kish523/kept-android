package app.kept.core.parser.extract

import app.kept.core.parser.internal.MoneyParser
import app.kept.core.parser.internal.Row
import app.kept.core.parser.internal.letterCount
import app.kept.core.parser.internal.normalizeKey
import app.kept.core.parser.internal.normalizeSpaces
import app.kept.core.parser.internal.smartTitleCase
import app.kept.core.parser.internal.stripLegalSuffix
import app.kept.core.parser.model.DocType
import app.kept.core.parser.model.Field
import app.kept.core.parser.model.FieldSource

internal object MerchantExtractor {
    private val I = RegexOption.IGNORE_CASE

    /** Header lines that are never the store name. */
    private val SKIP = Regex(
        """\b(receipt|tax\s*invoice|invoice|bill|cash\s*sale|copy|duplicate|welcome|thank|order|tel|phone|fax|""" +
            """www|http|vat|gst|tin|abn|siret|branch|cashier|till|reg|date|time|item|qty)\b|@|\d{3}[\s-]?\d{3,4}|store\s*(#|no)""",
        I,
    )
    private val ADDRESS = Regex(
        """\b(street|st\.|road|rd\.?|avenue|ave\.?|lane|ln\.|mawatha|blvd|boulevard|drive|suite|floor|plaza|""" +
            """highway|hwy|rue|strasse|straße|no\.\s*\d+)\b|\b\d{5}(?:-\d{4})?\b|\b[A-Z]{1,2}\d[A-Z\d]?\s*\d[A-Z]{2}\b""",
        I,
    )
    private val PHRASE = Regex(
        """\b(?:sold\s+by|shopping\s+(?:at|with)|shop\s+at|welcome\s+to|merchant\s*:|seller\s*:)\s+([A-Za-z0-9&'.\- ]{2,40})""",
        I,
    )
    private val DOMAIN = Regex("""\b(?:www\.)?([a-z0-9-]{3,})\.(?:com|co\.uk|lk|in|de|fr|net|shop|store)\b""", I)
    private val NOT_A_NAME = setOf("us", "our store", "the store", "store", "you")

    fun extract(rows: List<Row>, docType: DocType, known: Map<String, String>): Field<String>? {
        // 1. A merchant from the bundled / user table, anywhere near the top, wins outright.
        if (known.isNotEmpty()) {
            for (row in rows.take(12)) {
                val key = normalizeKey(row.text)
                val hit = known.entries.firstOrNull { (alias, _) ->
                    normalizeKey(alias).let { it.length >= 4 && key.contains(it) }
                }
                if (hit != null) return Field(hit.value, 0.95f, row.text)
            }
        }

        // 2. The first plausible header line above the first price.
        val firstPriceRow = rows.firstOrNull { MoneyParser.moneyTokens(it.text).isNotEmpty() }?.index ?: rows.size
        val header = rows.asSequence()
            .take(6)
            .filter { it.index < firstPriceRow }
            .filterNot { SKIP.containsMatchIn(it.text) || ADDRESS.containsMatchIn(it.text) }
            .firstOrNull { row ->
                val t = row.text.normalizeSpaces()
                val nonSpace = t.count { !it.isWhitespace() }
                t.length <= 48 && t.letterCount() >= 3 && t.letterCount() * 2 >= nonSpace
            }
            ?.let { row ->
                val allCaps = row.text.filter { it.isLetter() }.all { it.isUpperCase() }
                val confidence = (if (row.index == 0) 0.8f else 0.7f) + (if (allCaps) 0.05f else 0f)
                Field(clean(row.text), confidence * row.ocrFactor, row.text)
            }

        // 3. "Sold by X", "Thank you for shopping at X".
        val phrase = rows.firstNotNullOfOrNull { row ->
            PHRASE.find(row.text)?.groupValues?.get(1)
                ?.let { clean(it) }
                ?.takeIf { it.letterCount() >= 3 && it.lowercase() !in NOT_A_NAME }
                ?.let { Field(it, 0.8f, row.text) }
        }

        // 4. A web domain on the receipt, as a last resort.
        val domain = rows.firstNotNullOfOrNull { row ->
            DOMAIN.find(row.text)?.groupValues?.get(1)?.let {
                Field(it.replaceFirstChar { c -> c.titlecase() }, 0.5f, row.text, FieldSource.DERIVED)
            }
        }

        if (header != null && phrase != null) {
            val h = normalizeKey(header.value)
            val p = normalizeKey(phrase.value)
            if (h.contains(p) || p.contains(h)) return header.withConfidence(0.92f)
            if (docType == DocType.ORDER_CONFIRMATION) return phrase
        }
        if (docType == DocType.ORDER_CONFIRMATION && phrase != null) return phrase
        return listOfNotNull(header, phrase, domain).maxByOrNull { it.confidence }
    }

    private fun clean(raw: String): String = smartTitleCase(stripLegalSuffix(raw.normalizeSpaces()))
}
