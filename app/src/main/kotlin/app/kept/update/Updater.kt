package app.kept.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.kept.BuildConfig
import app.kept.MainActivity
import app.kept.R
import app.kept.data.settings.AppPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    /** Downloaded and verified; waiting for a good moment (or the user) to install. */
    data class Ready(val info: UpdateInfo) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Keeps Kept up to date from the public releases repo ([BuildConfig.UPDATE_REPO]).
 *
 * Only talks to GitHub, and only to ask "is there a newer build?" and download it. Nothing about the
 * user's receipts is ever sent. The downloaded APK must be Kept, newer, and signed with the same key,
 * or it's thrown away (Android enforces the signature too).
 *
 * Installing: Android 12+ lets an app update itself silently once it installed itself before (it's
 * then the "installer of record"). The first time, or on older phones, Android shows one confirm dialog.
 */
class Updater(private val context: Context, private val prefs: AppPrefs) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val lock = Mutex()
    private val dir get() = File(context.cacheDir, "updates").apply { mkdirs() }

    /** Called when the app opens: at most every few hours, quietly. */
    suspend fun checkOnLaunch() {
        if (!prefs.autoUpdate.value) return
        if (System.currentTimeMillis() - prefs.lastUpdateCheckMillis < LAUNCH_CHECK_INTERVAL_MS) return
        checkAndDownload()
    }

    /** "Check for updates" in Settings, the daily job, and launch. Ends in Ready, UpToDate or Failed. */
    suspend fun checkAndDownload(): UpdateState = lock.withLock {
        val current = _state.value
        if (current is UpdateState.Ready || current is UpdateState.Installing) return current
        _state.value = UpdateState.Checking
        val result = runCatching {
            val info = withContext(Dispatchers.IO) { fetchLatest() }
            prefs.lastUpdateCheckMillis = System.currentTimeMillis()
            if (info == null) {
                dir.listFiles()?.forEach { it.delete() } // tidy old downloads
                UpdateState.UpToDate
            } else {
                val apk = withContext(Dispatchers.IO) { download(info) }
                withContext(Dispatchers.IO) { verify(apk, info) }
                UpdateState.Ready(info)
            }
        }.getOrElse { e -> UpdateState.Failed(friendly(e)) }
        _state.value = result
        result
    }

    /** The daily job: check, download, and install if that can happen without bothering anyone. */
    suspend fun checkAndInstall(fromBackground: Boolean) {
        val s = checkAndDownload()
        if (s is UpdateState.Ready) {
            if (canInstallSilently()) install() else if (fromBackground) notifyReady(s.info)
        }
    }

    /** The user left the app with an update waiting: a good moment to install silently. */
    fun installIfQuietlyPossible() {
        if (prefs.autoUpdate.value && _state.value is UpdateState.Ready && canInstallSilently()) install()
    }

    /** Whether Kept may install APKs at all (the "Install unknown apps" switch for Kept). */
    fun canRequestInstalls(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun canInstallSilently(): Boolean {
        if (Build.VERSION.SDK_INT < 31 || !canRequestInstalls()) return false
        val installer = runCatching { context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName }.getOrNull()
        return installer == context.packageName
    }

    /** Hands the verified APK to Android's installer. Kept closes and reopens updated when it finishes. */
    fun install() {
        val ready = _state.value as? UpdateState.Ready ?: return
        val apk = apkFile(ready.info)
        if (!apk.exists()) { _state.value = UpdateState.Idle; return }
        _state.value = UpdateState.Installing(ready.info)
        runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                setSize(apk.length())
                if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                session.openWrite("kept.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val status = PendingIntent.getBroadcast(
                    context, sessionId,
                    Intent(context, UpdateReceiver::class.java).setAction(UpdateReceiver.ACTION_STATUS),
                    // Mutable: the installer adds the status and, if needed, the confirm-dialog intent.
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                session.commit(status.intentSender)
            }
        }.onFailure { _state.value = UpdateState.Failed(friendly(it)) }
    }

    internal fun onInstallFailed(message: String?) {
        val info = (_state.value as? UpdateState.Installing)?.info
        _state.value = if (info != null && apkFile(info).exists()) UpdateState.Ready(info) else UpdateState.Failed(message ?: "The update didn't install.")
    }

    /** Back to Ready after the user closed Android's confirm dialog without installing. */
    internal fun onInstallNeedsUser() {
        val info = (_state.value as? UpdateState.Installing)?.info ?: return
        _state.value = UpdateState.Ready(info)
    }

    private fun fetchLatest(): UpdateInfo? {
        val url = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases?per_page=10")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Kept-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (conn.responseCode == 404) throw IOException("The update source isn't set up yet.")
            if (conn.responseCode != 200) throw IOException("GitHub answered ${conn.responseCode}.")
            return pickUpdate(conn.inputStream.bufferedReader().use { it.readText() }, BuildConfig.VERSION_CODE)
        } finally {
            conn.disconnect()
        }
    }

    private fun apkFile(info: UpdateInfo) = File(dir, "kept-${info.build}.apk")

    private fun download(info: UpdateInfo): File {
        val target = apkFile(info)
        if (target.exists() && (info.sizeBytes <= 0 || target.length() == info.sizeBytes)) return target
        val part = File(dir, "kept-${info.build}.part")
        val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Kept-Android/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (conn.responseCode != 200) throw IOException("Download failed (${conn.responseCode}).")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastReport > 512 * 1024) {
                            lastReport = done
                            _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    out.fd.sync()
                }
            }
        } finally {
            conn.disconnect()
        }
        if (!part.renameTo(target)) throw IOException("Couldn't save the update.")
        return target
    }

    /** Same app, newer, same signing key. Anything else is deleted. */
    private fun verify(apk: File, info: UpdateInfo) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
        val installed = pm.getPackageInfo(context.packageName, flags)
        val ok = archive != null &&
            archive.packageName == context.packageName &&
            archive.versionCodeCompat() > BuildConfig.VERSION_CODE &&
            signers(archive) == signers(installed)
        if (!ok) {
            apk.delete()
            throw IOException("The downloaded update didn't check out, so it was discarded.")
        }
    }

    private fun signers(p: PackageInfo): Set<String> =
        if (Build.VERSION.SDK_INT >= 28) {
            p.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()
        } else {
            @Suppress("DEPRECATION") p.signatures.orEmpty().map { it.toCharsString() }.toSet()
        }

    private fun PackageInfo.versionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= 28) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()

    private fun notifyReady(info: UpdateInfo) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 1,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_INSTALL_UPDATE, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_kept)
            .setColor(0xFF0F6E56.toInt())
            .setContentTitle("Kept ${info.versionName} is ready")
            .setContentText("Tap to install the update.")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        if (canNotify(context)) {
            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n)
            } catch (_: SecurityException) {
                // Permission withdrawn in the meantime; Settings and Home still show the update.
            }
        }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is java.net.UnknownHostException, is java.net.ConnectException -> "No connection. Kept will try again later."
        is java.net.SocketTimeoutException -> "GitHub took too long to answer. Kept will try again later."
        else -> e.message ?: "Something went wrong while updating."
    }

    companion object {
        private const val CHANNEL = "updates"
        const val NOTIFICATION_ID = 7001
        private const val LAUNCH_CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000
    }
}

/** Notification permission (Android 13+) and the app's notification switch. */
internal fun canNotify(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)
