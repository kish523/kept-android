package app.kept.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.kept.AppContainer
import app.kept.reminders.ReminderReceiver.Companion.ACTION_FIRE
import app.kept.reminders.ReminderReceiver.Companion.ACTION_KEEP
import app.kept.reminders.ReminderReceiver.Companion.ACTION_RETURNED
import app.kept.reminders.ReminderReceiver.Companion.ACTION_SNOOZE
import app.kept.reminders.ReminderReceiver.Companion.EXTRA_REMINDER_ID
import app.kept.KeptApplication
import app.kept.data.db.ReminderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * One receiver for everything reminder-related:
 * - an alarm firing (show the notification),
 * - the notification's buttons (Keeping it, Returned it, Remind me tomorrow),
 * - boot, app update and clock or time-zone changes (re-arm everything from the database).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? KeptApplication ?: return
        val pending = goAsync()
        scope.launch {
            try {
                handleReminderIntent(app.container, intent)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "app.kept.reminder.FIRE"
        const val ACTION_KEEP = "app.kept.reminder.KEEP"
        const val ACTION_RETURNED = "app.kept.reminder.RETURNED"
        const val ACTION_SNOOZE = "app.kept.reminder.SNOOZE"
        const val EXTRA_REMINDER_ID = "reminderId"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun intent(context: Context, action: String, reminderId: String): Intent =
            Intent(context, ReminderReceiver::class.java).setAction(action).forReminder(reminderId, action.substringAfterLast('.'))
    }
}

/** What the receiver does, separate from the receiver so tests can call it directly. */
internal suspend fun handleReminderIntent(c: AppContainer, intent: Intent) {
    val id = intent.getStringExtra(EXTRA_REMINDER_ID)
    when (intent.action) {
        ACTION_FIRE -> {
            val d = id?.let { c.database.reminderDao().details(it) } ?: return
            val active = d.reminder.status in listOf(ReminderStatus.PENDING, ReminderStatus.HANDED_TO_OS, ReminderStatus.SNOOZED)
            if (!active) return
            if (d.isLive(LocalDate.now())) {
                c.reminderNotifications.show(d, LocalDate.now())
                c.database.reminderDao().update(d.reminder.copy(status = ReminderStatus.DELIVERED))
            } else {
                c.database.reminderDao().update(d.reminder.copy(status = ReminderStatus.CANCELLED))
            }
        }
        ACTION_KEEP -> {
            val d = id?.let { c.database.reminderDao().details(it) } ?: return
            c.reminderNotifications.cancel(d.reminder.id)
            c.repository.markKeeping(d.reminder.itemId)
        }
        ACTION_RETURNED -> {
            val d = id?.let { c.database.reminderDao().details(it) } ?: return
            c.reminderNotifications.cancel(d.reminder.id)
            c.repository.markReturned(d.reminder.itemId)
        }
        ACTION_SNOOZE -> {
            val r = id?.let { c.database.reminderDao().get(it) } ?: return
            c.reminderNotifications.cancel(r.id)
            c.database.reminderDao().update(r.copy(fireAt = snoozeUntil(r.dueDate, LocalDateTime.now()), status = ReminderStatus.SNOOZED))
            c.scheduler.sync()
        }
        Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED ->
            c.scheduler.sync() // re-arm everything from the database
    }
}
