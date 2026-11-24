package app.kept.core.parser

import app.kept.core.parser.model.Box
import app.kept.core.parser.model.DocType
import app.kept.core.parser.model.FieldSource
import app.kept.core.parser.model.OcrDocument
import app.kept.core.parser.model.OcrLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {
    private val today = LocalDate.of(2026, 11, 24)
    private val us = ReceiptParser(ParserConfig("USD", DateOrder.MDY, today))
    private val lk = ReceiptParser(ParserConfig("LKR", DateOrder.DMY, today))

    private fun golden(name: String) = javaClass.getResource("/golden/$name.txt")!!.readText()

    @Test
    fun `totals that add up are trusted`() {
        val r = us.parseText(golden("us_electronics"))
        assertEquals("530.44", r.total!!.value.toDecimal().toPlainString())
        assertTrue(r.total!!.confidence >= 0.95f)
        assertFalse(r.total!!.needsReview)
        assertTrue(r.fieldsNeedingReview.isEmpty())
    }

    @Test
    fun `line items keep names, quantities and unit prices`() {
        val r = ReceiptParser(ParserConfig("GBP", DateOrder.DMY, today)).parseText(golden("uk_grocery"))
        val bananas = r.items.single { it.name == "BANANAS" }
        assertEquals(2, bananas.quantity)
        assertEquals("0.25", bananas.unitPrice!!.toDecimal().toPlainString())
        assertEquals("0.50", bananas.totalPrice.toDecimal().toPlainString())
        assertEquals("OLIVE OIL 500ML", r.items.last().name)
    }

    @Test
    fun `legal suffixes are removed and capitals tidied`() {
        assertEquals("Lanka Home Centre", lk.parseText(golden("lk_appliance")).merchant!!.value)
    }

    @Test
    fun `order confirmations use the seller name, not the first line`() {
        val r = us.parseText(golden("order_email"))
        assertEquals(DocType.ORDER_CONFIRMATION, r.docType.value)
        assertEquals("Northwind Home Store", r.merchant!!.value)
        // The delivery window and return deadline must not be mistaken for the purchase date.
        assertEquals(LocalDate.of(2026, 11, 20), r.purchaseDate!!.value)
    }

    @Test
    fun `a receipt with no total line is flagged for review`() {
        val r = lk.parseText(golden("faded_hardware"))
        assertEquals("104.50", r.total!!.value.toDecimal().toPlainString())
        assertEquals(FieldSource.DERIVED, r.total!!.source)
        assertTrue(r.total!!.needsReview)
        assertTrue("total" in r.fieldsNeedingReview)
        assertTrue(r.warnings.any { it.contains("No total line") })
    }

    @Test
    fun `missing currency falls back to the phone's currency without nagging`() {
        val r = lk.parseText(golden("faded_hardware"))
        assertEquals("LKR", r.currency.value)
        assertEquals(FieldSource.CONFIG_DEFAULT, r.currency.source)
        assertFalse("currency" in r.fieldsNeedingReview)
    }

    @Test
    fun `a foreign dollar sign on a rupee phone is flagged`() {
        val r = lk.parseText("GIFT SHOP\n10/11/2026\nMUG  \$12.00\nTOTAL  \$12.00")
        assertEquals("USD", r.currency.value)
        assertTrue("currency" in r.fieldsNeedingReview)
    }

    @Test
    fun `full card numbers never survive parsing`() {
        val r = us.parseText(
            """
            CORNER CAFE
            11/20/2026
            LATTE            4.50
            TOTAL            4.50
            VISA 4111 1111 1111 1111
            """.trimIndent(),
        )
        assertTrue(r.redactedLines.none { it.contains("4111 1111 1111 1111") })
        assertTrue(r.redactedLines.any { it.contains("•••• 1111") })
        assertEquals("1111", r.payment!!.value.cardLast4)
        assertEquals("Visa", r.payment!!.value.label)
    }

    @Test
    fun `label and price split into separate OCR lines are joined by position`() {
        fun line(text: String, left: Int, top: Int, right: Int) = OcrLine(text, Box(left, top, right, top + 20))
        val doc = OcrDocument(
            listOf(
                line("PIXEL PHONES", 40, 10, 300),
                line("CASE", 20, 100, 120),
                line("TOTAL", 20, 160, 120),
                line("25.00", 400, 102, 480), // price for CASE, recognised as its own block
                line("27.10", 400, 158, 480), // price for TOTAL
                line("TAX", 20, 130, 80),
                line("2.10", 400, 131, 470),
            ),
        )
        val r = us.parse(doc)
        assertEquals("27.10", r.total!!.value.toDecimal().toPlainString())
        assertEquals("2.10", r.tax!!.value.toDecimal().toPlainString())
        assertEquals(listOf("CASE"), r.items.map { it.name })
    }

    @Test
    fun `known merchants from the policy table win`() {
        val parser = ReceiptParser(ParserConfig("USD", DateOrder.MDY, today, mapOf("voltmart" to "VoltMart")))
        val r = parser.parseText(golden("us_electronics"))
        assertEquals("VoltMart", r.merchant!!.value)
        assertTrue(r.merchant!!.confidence >= 0.95f)
    }

    @Test
    fun `future dates are treated as misreads`() {
        val r = us.parseText("SHOP\nDate: 12/30/2027\nTOTAL 5.00")
        assertNull(r.purchaseDate)
        assertTrue(r.warnings.any { it.contains("No purchase date") })
    }

    @Test
    fun `day-first and month-first receipts read the same digits differently`() {
        val text = "SHOP\nDate: 03/04/2026\nTOTAL 5.00"
        assertEquals(LocalDate.of(2026, 3, 4), us.parseText(text).purchaseDate!!.value)
        assertEquals(LocalDate.of(2026, 4, 3), lk.parseText(text).purchaseDate!!.value)
    }

    @Test
    fun `subtotal larger than total lowers confidence`() {
        val r = us.parseText("SHOP\n11/01/2026\nSUBTOTAL 50.00\nTOTAL 5.00")
        assertNotNull(r.total)
        assertTrue(r.total!!.needsReview)
        assertTrue(r.warnings.any { it.contains("smaller than the subtotal") })
    }
}
