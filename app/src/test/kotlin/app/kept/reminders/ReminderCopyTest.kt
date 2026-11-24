package app.kept.reminders

import app.kept.core.deadlines.ReminderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ReminderCopyTest {
    private val today = LocalDate.of(2026, 11, 24)

    @Test fun `return reminder wording counts down`() {
        assertEquals("Return window closes in 7 days", reminderCopy(ReminderKind.RETURN, "Headphones", "Northline", today.plusDays(7), today).title)
        assertEquals("Return window closes tomorrow", reminderCopy(ReminderKind.RETURN, "Headphones", "Northline", today.plusDays(1), today).title)
        assertEquals("Last day to return Headphones", reminderCopy(ReminderKind.RETURN, "Headphones", null, today, today).title)
        val body = reminderCopy(ReminderKind.RETURN, "Headphones", "Northline", today.plusDays(4), today).body
        assertTrue(body, body.startsWith("Headphones · Northline. Return by "))
    }

    @Test fun `warranty reminder wording`() {
        assertEquals("Warranty ends in 30 days", reminderCopy(ReminderKind.WARRANTY, "Vacuum", "Kestrel", today.plusDays(30), today).title)
        assertTrue(reminderCopy(ReminderKind.WARRANTY, "Vacuum", "Kestrel", today.plusDays(30), today).body.contains("Covered until"))
    }

    @Test fun `snooze goes to 9am tomorrow, or three hours on the last day`() {
        val now = LocalDateTime.of(2026, 11, 24, 9, 0)
        assertEquals(LocalDateTime.of(2026, 11, 25, 9, 0), snoozeUntil(today.plusDays(3), now))
        assertEquals(LocalDateTime.of(2026, 11, 24, 12, 0), snoozeUntil(today, now))
        assertEquals("Remind me tomorrow", snoozeLabel(today.plusDays(3), today))
        assertEquals("Remind me later", snoozeLabel(today, today))
    }
}
