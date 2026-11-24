package app.kept

import android.app.Application
import app.kept.reminders.DailyWorker
import kotlinx.coroutines.launch

class KeptApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.reminderNotifications.ensureChannel()
        container.appScope.launch {
            // Re-arm reminders (cheap, idempotent) and finish deletions a crash may have left half-done.
            runCatching { container.repository.purgeTrash() }
            runCatching { container.scheduler.sync() }
        }
        // Daily safety net for reminders, plus the update check. Not available in some test setups.
        runCatching { DailyWorker.ensureScheduled(this) }
    }
}
