package app.kept.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import app.kept.core.deadlines.ReminderKind
import app.kept.data.db.ItemState
import app.kept.data.db.KeptDatabase
import app.kept.data.db.ReceiptStatus
import app.kept.data.db.ReminderDetails
import app.kept.data.db.ReminderEntity
import app.kept.data.db.ReminderStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Hands reminder rows to the OS. The database is the truth; this can always be re-run. */
interface ReminderScheduler {
    /** Arms an alarm for every live reminder, delivers ones missed while the phone was off, retires dead ones. */
    suspend fun sync(now: LocalDateTime = LocalDateTime.now())

    /** Cancels the alarms and any shown notifications for these reminders. */
    fun cancel(reminders: List<ReminderEntity>)

    object None : ReminderScheduler {
        override suspend fun sync(now: LocalDateTime) = Unit
        override fun cancel(reminders: List<ReminderEntity>) = Unit
    }
}

/** A reminder is worth showing only while its receipt and item are still open and the date hasn't passed. */
fun ReminderDetails.isLive(today: LocalDate): Boolean =
    receiptStatus == ReceiptStatus.ACTIVE &&
        (itemState == ItemState.KEPT || itemState == ItemState.RETURNING) &&
        !(reminder.kind == ReminderKind.RETURN && returnResolved) &&
        !reminder.dueDate.isBefore(today)

/**
 * Inexact, idle-safe alarms (`setAndAllowWhileIdle`): no exact-alarm permission, and Android may shift
 * them by a few minutes to save battery, which is fine for a 9:00 heads-up.
 */
class AlarmReminderScheduler(
    private val context: Context,
    private val db: KeptDatabase,
    private val notifications: ReminderNotifications,
) : ReminderScheduler {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override suspend fun sync(now: LocalDateTime) {
        val dao = db.reminderDao()
        val today = now.toLocalDate()
        for (r in dao.upcoming()) {
            val details = dao.details(r.id)
            if (details == null || !details.isLive(today)) {
                dao.update(r.copy(status = if (details != null && r.dueDate.isBefore(today)) ReminderStatus.DONE else ReminderStatus.CANCELLED))
                cancel(listOf(r))
                continue
            }
            if (!r.fireAt.isAfter(now)) {
                // Missed while the phone was off or the app was updated: tell now, once.
                notifications.show(details, today)
                dao.update(r.copy(status = ReminderStatus.DELIVERED))
                continue
            }
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, r.fireAt.toEpochMillis(), firePendingIntent(r.id))
            if (r.status != ReminderStatus.HANDED_TO_OS || r.osRequestCode == null) {
                dao.update(r.copy(status = ReminderStatus.HANDED_TO_OS, osRequestCode = r.id.hashCode(), lastScheduledAt = Instant.now()))
            }
        }
    }

    override fun cancel(reminders: List<ReminderEntity>) {
        val nm = NotificationManagerCompat.from(context)
        reminders.forEach { r ->
            alarms.cancel(firePendingIntent(r.id))
            nm.cancel(ReminderNotifications.notificationId(r.id))
        }
    }

    private fun firePendingIntent(reminderId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        ReminderReceiver.intent(context, ReminderReceiver.ACTION_FIRE, reminderId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Floating local time: 9:00 wherever the phone is now. Re-run on time-zone change. */
fun LocalDateTime.toEpochMillis(): Long = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

internal fun reminderUri(id: String, what: String): Uri = Uri.parse("kept://reminder/$id/$what")

internal fun Intent.forReminder(id: String, what: String): Intent = apply {
    data = reminderUri(id, what)
    putExtra(ReminderReceiver.EXTRA_REMINDER_ID, id)
}
