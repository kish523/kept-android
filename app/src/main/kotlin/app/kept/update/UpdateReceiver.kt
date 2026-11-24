package app.kept.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.kept.KeptApplication
import app.kept.MainActivity
import app.kept.R

/** Android's installer reporting back: done, failed, or "needs the user to confirm". */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_STATUS) return
        val app = context.applicationContext as? KeptApplication ?: return
        val updater = app.container.updater
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm: Intent = (
                    if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    ) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                updater.onInstallNeedsUser()
                if (MainActivity.isInForeground) {
                    context.startActivity(confirm)
                } else {
                    val pi = PendingIntent.getActivity(context, 2, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    val n = NotificationCompat.Builder(context, "updates")
                        .setSmallIcon(R.drawable.ic_stat_kept)
                        .setContentTitle("A Kept update is ready")
                        .setContentText("Tap to install it.")
                        .setContentIntent(pi)
                        .setAutoCancel(true)
                        .build()
                    if (canNotify(context)) {
                        try {
                            NotificationManagerCompat.from(context).notify(Updater.NOTIFICATION_ID, n)
                        } catch (_: SecurityException) {
                            // Permission withdrawn in the meantime; Settings and Home still show the update.
                        }
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // Kept is being replaced; the new version shows What's new.
            else -> updater.onInstallFailed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }

    companion object {
        const val ACTION_STATUS = "app.kept.update.STATUS"
    }
}
