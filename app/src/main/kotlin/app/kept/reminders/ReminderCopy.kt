package app.kept.reminders

import app.kept.core.deadlines.ReminderKind
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What a reminder notification says. Pure, so it's unit-tested. */
data class ReminderCopy(val title: String, val body: String)

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())

fun reminderCopy(kind: ReminderKind, itemName: String, merchant: String?, due: LocalDate, today: LocalDate): ReminderCopy {
    val left = ChronoUnit.DAYS.between(today, due)
    val what = listOfNotNull(itemName, merchant).joinToString(" · ")
    return when (kind) {
        ReminderKind.RETURN -> ReminderCopy(
            title = when {
                left <= 0L -> "Last day to return $itemName"
                left == 1L -> "Return window closes tomorrow"
                else -> "Return window closes in $left days"
            },
            body = "$what. Return by ${due.format(DAY)}.",
        )
        ReminderKind.WARRANTY -> ReminderCopy(
            title = when {
                left <= 0L -> "Warranty ends today"
                left == 1L -> "Warranty ends tomorrow"
                else -> "Warranty ends in $left days"
            },
            body = "$what. Covered until ${due.format(DAY)}.",
        )
    }
}

/**
 * "Remind me tomorrow": 9:00 tomorrow, unless the deadline is today, then three hours from now
 * (so it can't skip past the last day).
 */
fun snoozeUntil(due: LocalDate, now: LocalDateTime): LocalDateTime {
    val today = now.toLocalDate()
    return if (due.isAfter(today)) today.plusDays(1).atTime(LocalTime.of(9, 0)) else now.plusHours(3).withSecond(0).withNano(0)
}

fun snoozeLabel(due: LocalDate, today: LocalDate): String = if (due.isAfter(today)) "Remind me tomorrow" else "Remind me later"
