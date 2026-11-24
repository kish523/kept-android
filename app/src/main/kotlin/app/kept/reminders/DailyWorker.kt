package app.kept.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kept.KeptApplication
import java.util.concurrent.TimeUnit

/**
 * Once a day: re-check every reminder against the OS (a safety net if an alarm was lost),
 * finish any deletions left by a crash, and look for an app update if that's switched on.
 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as KeptApplication).container
        c.scheduler.sync()
        c.repository.purgeTrash()
        if (c.prefs.autoUpdate.value) c.updater.checkAndInstall(fromBackground = true)
        return Result.success()
    }

    companion object {
        private const val NAME = "kept-daily"

        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
