package app.kept.core.parser

import app.kept.core.parser.model.Money
import app.kept.core.parser.model.ParsedReceipt
import app.kept.core.parser.model.ReturnHint
import app.kept.core.parser.model.WarrantyHint
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Runs the parser over every receipt in src/test/resources/golden and scores it field by field.
 *
 * Add real receipts here as you collect them: `name.txt` holds the recognised text, `name.expected`
 * the correct answers (only the fields you list are checked). The report printed by this test is the
 * accuracy number the roadmap's "90%+ on clear receipts" gate refers to.
 */
class GoldenSetTest {
    private val today = LocalDate.of(2026, 11, 24)

    @Test
    fun goldenSetAccuracy() {
        val dir = File(requireNotNull(javaClass.getResource("/golden")) { "golden folder missing" }.toURI())
        val cases = dir.listFiles { f -> f.name.endsWith(".txt") }!!.sortedBy { it.name }
        assertTrue("No golden receipts found", cases.isNotEmpty())

        var checked = 0
        var correct = 0
        val failures = mutableListOf<String>()
        val perField = sortedMapOf<String, IntArray>() // field -> [correct, checked]

        for (txt in cases) {
            val expected = File(txt.path.removeSuffix(".txt") + ".expected").readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
            val (currency, order) = (expected["config"] ?: "USD,MDY").split(',').map { it.trim() }
            val parsed = ReceiptParser(ParserConfig(currency, DateOrder.valueOf(order), today)).parseText(txt.readText())

            for ((field, want) in expected) {
                if (field == "config") continue
                val got = actual(parsed, field)
                val ok = matches(field, want, got)
                checked++
                val counts = perField.getOrPut(field) { IntArray(2) }
                counts[1]++
                if (ok) {
                    correct++
                    counts[0]++
                } else {
                    failures += "${txt.nameWithoutExtension}: $field expected '$want' got '$got'"
                }
            }
        }

        val accuracy = correct.toDouble() / checked
        println("Golden set: ${cases.size} receipts, $correct/$checked fields correct (${"%.1f".format(accuracy * 100)}%)")
        perField.forEach { (f, c) -> println("  %-10s %d/%d".format(f, c[0], c[1])) }
        failures.forEach { println("  MISS $it") }
        assertTrue("Accuracy ${"%.1f".format(accuracy * 100)}% is below the 90% gate", accuracy >= 0.9)
    }

    private fun actual(p: ParsedReceipt, field: String): String? = when (field) {
        "merchant" -> p.merchant?.value
        "date" -> p.purchaseDate?.value?.toString()
        "currency" -> p.currency.value
        "total" -> p.total?.value?.plain()
        "subtotal" -> p.subtotal?.value?.plain()
        "tax" -> p.tax?.value?.plain()
        "last4" -> p.payment?.value?.cardLast4
        "order" -> p.orderNumber?.value
        "serial" -> p.serialNumbers.joinToString(",") { it.value }
        "return" -> when (val h = p.returnHint?.value) {
            is ReturnHint.ByDate -> "date:${h.date}"
            is ReturnHint.WithinDays -> "days:${h.days}"
            ReturnHint.NoReturns -> "none"
            null -> null
        }
        "warranty" -> when (val w = p.warranty?.value) {
            is WarrantyHint.Months -> "months:${w.months}"
            WarrantyHint.Lifetime -> "lifetime"
            null -> null
        }
        "category" -> p.suggestedCategory?.value?.name
        "items" -> p.items.size.toString()
        else -> error("Unknown field '$field' in .expected file")
    }

    private fun matches(field: String, want: String, got: String?): Boolean {
        if (got == null) return false
        return when (field) {
            "merchant" -> norm(want) == norm(got)
            "total", "subtotal", "tax" -> BigDecimal(want).compareTo(BigDecimal(got)) == 0
            "serial" -> want in got.split(',')
            else -> want.equals(got, ignoreCase = true)
        }
    }

    private fun Money.plain() = toDecimal().toPlainString()
    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
}
