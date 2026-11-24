package app.kept.core.parser

import app.kept.core.parser.extract.CategorySuggester
import app.kept.core.parser.extract.DocTypeClassifier
import app.kept.core.parser.extract.IdentifierExtractor
import app.kept.core.parser.extract.LineItemExtractor
import app.kept.core.parser.extract.MerchantExtractor
import app.kept.core.parser.extract.PaymentExtractor
import app.kept.core.parser.extract.PolicyExtractor
import app.kept.core.parser.extract.PurchaseDateExtractor
import app.kept.core.parser.extract.TotalsExtractor
import app.kept.core.parser.internal.CurrencyDetector
import app.kept.core.parser.internal.RowBuilder
import app.kept.core.parser.model.FieldSource
import app.kept.core.parser.model.OcrDocument
import app.kept.core.parser.model.ParsedReceipt
import kotlin.math.max

/**
 * Turns recognised receipt text into structured fields, each with a confidence score.
 *
 * Runs on every phone, fully offline, in a few milliseconds. On phones with an on-device model the
 * app can ask the model too and use this parser to check its answer; elsewhere this is the main path.
 */
class ReceiptParser(private val config: ParserConfig) {

    fun parse(document: OcrDocument): ParsedReceipt {
        // Redact first, so a full card number never reaches the extractors, the database or logs.
        val redacted = OcrDocument(document.lines.map { it.copy(text = CardNumberRedactor.redact(it.text)) })
        val rows = RowBuilder.build(redacted)
        val warnings = mutableListOf<String>()

        val currency = CurrencyDetector.detect(rows, config)
        val docType = DocTypeClassifier.classify(rows)
        val totals = TotalsExtractor.extract(rows, currency.value)
        warnings += totals.warnings

        var items = LineItemExtractor.extract(rows, currency.value, totals.itemsEndRow)
        var total = totals.total

        // Items that add up to the subtotal or total confirm both.
        if (items.isNotEmpty()) {
            val sum = items.sumOf { it.totalPrice.minorUnits }
            val taxMinor = totals.tax?.value?.minorUnits ?: 0L
            val targets = listOfNotNull(
                totals.subtotal?.value?.minorUnits,
                total?.value?.minorUnits,
                total?.value?.minorUnits?.minus(taxMinor),
            )
            if (targets.any { TotalsExtractor.close(sum, it) }) {
                items = items.map { it.copy(confidence = 0.9f) }
                total?.let { t ->
                    val agrees = TotalsExtractor.close(sum, t.value.minorUnits) ||
                        TotalsExtractor.close(sum + taxMinor, t.value.minorUnits)
                    if (agrees) {
                        val boosted = if (t.source == FieldSource.DERIVED) 0.6f else 0.9f
                        total = t.withConfidence(max(t.confidence, boosted))
                    }
                }
            }
        }

        val merchant = MerchantExtractor.extract(rows, docType.value, config.knownMerchants)
        val purchaseDate = PurchaseDateExtractor.extract(rows, config)
        if (purchaseDate == null) warnings += "No purchase date found."
        if (currency.source == FieldSource.CONFIG_DEFAULT) warnings += "No currency shown; assumed ${currency.value}."

        val categoryText = buildString {
            merchant?.let { append(it.value).append('\n') }
            if (items.isEmpty()) rows.forEach { append(it.text).append('\n') }
            else items.forEach { append(it.name).append('\n') }
        }

        return ParsedReceipt(
            docType = docType,
            merchant = merchant,
            purchaseDate = purchaseDate,
            currency = currency,
            total = total,
            subtotal = totals.subtotal,
            tax = totals.tax,
            payment = PaymentExtractor.extract(rows),
            orderNumber = IdentifierExtractor.orderNumber(rows),
            serialNumbers = IdentifierExtractor.serialNumbers(rows),
            items = items,
            returnHint = PolicyExtractor.returnHint(rows, config),
            warranty = PolicyExtractor.warranty(rows),
            suggestedCategory = CategorySuggester.suggest(categoryText),
            redactedLines = rows.map { it.text },
            warnings = warnings,
        )
    }

    fun parseText(text: String): ParsedReceipt = parse(OcrDocument.fromPlainText(text))
}
