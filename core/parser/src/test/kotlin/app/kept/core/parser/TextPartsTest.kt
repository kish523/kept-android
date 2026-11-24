package app.kept.core.parser

import app.kept.core.parser.internal.DateParser
import app.kept.core.parser.internal.MoneyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class MoneyParserTest {
    private fun money(text: String) = MoneyParser.moneyTokens(text).map { it.value.toPlainString() }

    @Test fun `US, European and South Asian number formats`() {
        assertEquals(listOf("1234.56"), money("TOTAL 1,234.56"))
        assertEquals(listOf("1234.56"), money("SUMME 1.234,56"))
        assertEquals(listOf("12.99"), money("BROT 12,99"))
        assertEquals(listOf("189900.00"), money("FRIDGE  Rs. 189,900.00"))
        assertEquals(listOf("1250"), money("Rs 1,250")) // a symbol makes an integer count as money
    }

    @Test fun `tax flags after a price are fine`() {
        assertEquals(listOf("5.49"), money("OLIVE OIL 500ML  5.49 A"))
    }

    @Test fun `dates, times, percentages and quantities are not money`() {
        assertTrue(money("15.11.2026 11:42").isEmpty())
        assertTrue(money("SALES TAX 9.375%").isEmpty())
        assertTrue(money("dont TVA 5,5%").isEmpty())
        assertTrue(money("REG 03 TRN 5521").isEmpty())
    }

    @Test fun `trailing minus marks a refund line`() {
        assertEquals(BigDecimal("-5.00"), MoneyParser.moneyTokens("COUPON 5.00-").single().value)
    }
}

class DateParserTest {
    private fun dates(text: String, order: DateOrder = DateOrder.DMY) = DateParser.find(text, order).map { it.date }

    @Test fun `common receipt date formats`() {
        val d = LocalDate.of(2026, 11, 12)
        assertEquals(listOf(d), dates("2026-11-12 10:03"))
        assertEquals(listOf(d), dates("12/11/2026"))
        assertEquals(listOf(d), dates("11/12/2026", DateOrder.MDY))
        assertEquals(listOf(d), dates("12.11.26"))
        assertEquals(listOf(d), dates("12 Nov 2026"))
        assertEquals(listOf(d), dates("12-Nov-26"))
        assertEquals(listOf(d), dates("Nov 12, 2026"))
        assertEquals(listOf(d), dates("November 12th 2026"))
    }

    @Test fun `unambiguous dates ignore the locale order`() {
        assertEquals(listOf(LocalDate.of(2026, 12, 25)), dates("25/12/2026", DateOrder.MDY))
        assertEquals(listOf(LocalDate.of(2026, 12, 25)), dates("12/25/2026", DateOrder.DMY))
        assertFalse(DateParser.find("25/12/2026", DateOrder.MDY).single().ambiguous)
        assertTrue(DateParser.find("03/04/2026", DateOrder.MDY).single().ambiguous)
    }

    @Test fun `ranges and street names are not dates`() {
        assertTrue(dates("Estimated delivery: Nov 26 - Nov 28").isEmpty())
        assertTrue(dates("1 Market Street 2026").isEmpty())
    }
}

class CardNumberRedactorTest {
    @Test fun `card numbers are masked, order numbers are not`() {
        assertEquals("VISA •••• 1111", CardNumberRedactor.redact("VISA 4111 1111 1111 1111"))
        assertEquals("•••• 4444", CardNumberRedactor.redact("5555-5555-5555-4444"))
        assertEquals("Order #: 112-4456789-1234567", CardNumberRedactor.redact("Order #: 112-4456789-1234567"))
        assertEquals("SIRET 123 456 789 00012", CardNumberRedactor.redact("SIRET 123 456 789 00012"))
    }

    @Test fun `numbers failing the Luhn check are left alone`() {
        assertEquals("REF 4111 1111 1111 1112", CardNumberRedactor.redact("REF 4111 1111 1111 1112"))
    }
}
