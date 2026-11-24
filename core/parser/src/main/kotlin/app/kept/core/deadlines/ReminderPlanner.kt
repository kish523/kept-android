package app.kept.core.deadlines

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class ReminderKind { RETURN, WARRANTY }

/**
 * A reminder to store in the database. [fireAt] is a "floating" local time: 9:00 in whatever time
 * zone the phone is in when it fires, so travelling never moves a reminder to 3 a.m.
 */
data class PlannedReminder(
    val kind: ReminderKind,
    val dueDate: LocalDate,
    val daysBefore: Int,
    val fireAt: LocalDateTime,
)

object ReminderPlanner {
    val RETURN_OFFSETS = listOf(7, 3, 1)
    val WARRANTY_OFFSETS = listOf(30, 7)
    val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    fun defaultOffsets(kind: ReminderKind) = when (kind) {
        ReminderKind.RETURN -> RETURN_OFFSETS
        ReminderKind.WARRANTY -> WARRANTY_OFFSETS
    }

    fun plan(
        kind: ReminderKind,
        dueDate: LocalDate,
        now: LocalDateTime,
        offsets: List<Int> = defaultOffsets(kind),
        timeOfDay: LocalTime = DEFAULT_TIME,
    ): List<PlannedReminder> {
        val today = now.toLocalDate()
        if (dueDate.isBefore(today)) return emptyList()

        val planned = offsets.filter { it >= 0 }.distinct().sortedDescending().mapNotNull { days ->
            val at = dueDate.minusDays(days.toLong()).atTime(timeOfDay)
            if (at.isBefore(now)) null else PlannedReminder(kind, dueDate, days, at)
        }
        if (planned.isNotEmpty()) return planned

        // Every heads-up is already in the past (say, a receipt added late): send one as soon as sensible.
        val daysLeft = ChronoUnit.DAYS.between(today, dueDate).toInt()
        val todayAt = today.atTime(timeOfDay)
        val at = if (todayAt.isAfter(now)) todayAt else now.plusMinutes(1).withSecond(0).withNano(0)
        return listOf(PlannedReminder(kind, dueDate, daysLeft, at))
    }
}
