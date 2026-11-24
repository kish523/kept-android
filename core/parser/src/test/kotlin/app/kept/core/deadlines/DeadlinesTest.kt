package app.kept.core.deadlines

import app.kept.core.parser.model.Category
import app.kept.core.parser.model.ReturnHint
import app.kept.core.parser.model.WarrantyHint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeadlineCalculatorTest {
    private val bought = LocalDate.of(2026, 11, 12)
    private val policy = MerchantPolicy(
        name = "VoltMart Electronics",
        aliases = listOf("VoltMart"),
        returnDays = 30,
        categoryReturnDays = mapOf(Category.ELECTRONICS to 15),
    )

    @Test fun `a date printed on the receipt wins`() {
        val d = DeadlineCalculator.returnDeadline(bought, ReturnHint.ByDate(LocalDate.of(2026, 12, 28)), policy, null)
        assertEquals(Deadline.On(LocalDate.of(2026, 12, 28), DeadlineSource.RECEIPT_DATE, "Printed on the receipt"), d)
    }

    @Test fun `when the receipt and the store table disagree, the shorter window wins`() {
        val d = DeadlineCalculator.returnDeadline(bought, ReturnHint.WithinDays(30), policy, Category.ELECTRONICS) as Deadline.On
        assertEquals(LocalDate.of(2026, 11, 27), d.date) // 15-day electronics policy beats the 30 days on the receipt
        assertEquals(DeadlineSource.MERCHANT_POLICY, d.source)
    }

    @Test fun `no returns and nothing known are distinct answers`() {
        assertTrue(DeadlineCalculator.returnDeadline(bought, ReturnHint.NoReturns, policy, null) is Deadline.NotAllowed)
        assertEquals(Deadline.Unknown, DeadlineCalculator.returnDeadline(bought, null, null, null))
    }

    @Test fun `warranty from the receipt, else a category default, else ask`() {
        val printed = DeadlineCalculator.warrantyDeadline(bought, WarrantyHint.Months(24), Category.APPLIANCES) as Deadline.On
        assertEquals(LocalDate.of(2028, 11, 12), printed.date)
        val default = DeadlineCalculator.warrantyDeadline(bought, null, Category.ELECTRONICS) as Deadline.On
        assertEquals(DeadlineSource.CATEGORY_DEFAULT, default.source)
        assertEquals(Deadline.Unknown, DeadlineCalculator.warrantyDeadline(bought, null, Category.CLOTHING))
    }

    @Test fun `month arithmetic clamps to the end of the month`() {
        val d = DeadlineCalculator.warrantyDeadline(LocalDate.of(2026, 1, 31), WarrantyHint.Months(1), null) as Deadline.On
        assertEquals(LocalDate.of(2026, 2, 28), d.date)
    }

    @Test fun `policy table matches names loosely`() {
        val table = MerchantPolicyTable(listOf(policy))
        assertEquals(policy, table.find("Voltmart Electronics"))
        assertEquals(policy, table.find("VOLTMART"))
        assertNull(table.find("Volt"))
    }
}

class ReminderPlannerTest {
    private val due = LocalDate.of(2026, 12, 24)

    @Test fun `default return reminders are 7, 3 and 1 days before at 9am`() {
        val plan = ReminderPlanner.plan(ReminderKind.RETURN, due, LocalDateTime.of(2026, 11, 24, 12, 0))
        assertEquals(listOf(7, 3, 1), plan.map { it.daysBefore })
        assertEquals(LocalDateTime.of(2026, 12, 17, 9, 0), plan.first().fireAt)
    }

    @Test fun `past reminders are dropped`() {
        val plan = ReminderPlanner.plan(ReminderKind.RETURN, due, LocalDateTime.of(2026, 12, 22, 12, 0))
        assertEquals(listOf(1), plan.map { it.daysBefore })
    }

    @Test fun `a receipt added at the last minute still gets one reminder`() {
        val now = LocalDateTime.of(2026, 12, 24, 15, 30)
        val plan = ReminderPlanner.plan(ReminderKind.RETURN, due, now)
        assertEquals(1, plan.size)
        assertEquals(0, plan.single().daysBefore)
        assertEquals(LocalDateTime.of(2026, 12, 24, 15, 31), plan.single().fireAt)
    }

    @Test fun `expired deadlines get no reminders`() {
        assertTrue(ReminderPlanner.plan(ReminderKind.WARRANTY, due, LocalDateTime.of(2026, 12, 25, 8, 0)).isEmpty())
    }
}
