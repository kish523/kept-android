package app.kept.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.kept.MainActivity
import app.kept.R
import app.kept.core.deadlines.ReminderKind
import app.kept.data.db.ReminderDetails
import java.time.LocalDate

/** Builds and posts reminder notifications with Keeping it / Returned it / Remind me tomorrow. */
class ReminderNotifications(private val context: Context) {

    fun ensureChannel() {
        val channel = NotificationChannel(CHANNEL, "Return and warranty reminders", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Heads-ups before a return window closes or a warranty ends."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun show(d: ReminderDetails, today: LocalDate) {
        if (!canPost()) return
        ensureChannel()
        val r = d.reminder
        val copy = reminderCopy(r.kind, d.itemName, d.merchantName, r.dueDate, today)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(reminderUri(r.id, "open"))
                .putExtra(MainActivity.EXTRA_OPEN_RECEIPT, d.receiptId)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kept)
            .setColor(0xFF0F6E56.toInt())
            .setContentTitle(copy.title)
            .setContentText(copy.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(copy.body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
        if (r.kind == ReminderKind.RETURN) {
            builder.addAction(0, "Keeping it", action(ReminderReceiver.ACTION_KEEP, r.id))
            builder.addAction(0, "Returned it", action(ReminderReceiver.ACTION_RETURNED, r.id))
        }
        builder.addAction(0, snoozeLabel(r.dueDate, today), action(ReminderReceiver.ACTION_SNOOZE, r.id))
        try {
            NotificationManagerCompat.from(context).notify(notificationId(r.id), builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post: the deadline still shows on Home.
        }
    }

    fun cancel(reminderId: String) = NotificationManagerCompat.from(context).cancel(notificationId(reminderId))

    private fun action(action: String, reminderId: String): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        ReminderReceiver.intent(context, action, reminderId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL = "reminders"
        fun notificationId(reminderId: String) = reminderId.hashCode()
    }
}
