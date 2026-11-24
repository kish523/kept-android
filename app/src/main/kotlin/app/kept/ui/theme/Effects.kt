package app.kept.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * How much visual polish this phone gets. Budget phones struggle with live blur and endless
 * background animation, so they get a lighter version of the same design: translucent bars
 * instead of blurred ones, a still glow, and simpler entrances. Everything still works the same.
 */
@Immutable
data class KeptEffects(
    /** Frosted, live-blurred bars. Off: a plain translucent tint. */
    val blur: Boolean,
    /** The slowly drifting colour glow behind Home's header. Off: drawn once, still. */
    val ambientMotion: Boolean,
    /** Rows rising in one after another, bars growing. Off: everything simply appears. */
    val richEntrances: Boolean,
) {
    companion object {
        val Full = KeptEffects(blur = true, ambientMotion = true, richEntrances = true)
        val Lite = KeptEffects(blur = false, ambientMotion = false, richEntrances = false)
    }
}

val LocalKeptEffects = staticCompositionLocalOf { KeptEffects.Full }

object DevicePerformance {
    /**
     * A phone that should get the light effects by default: Android's own "low RAM" flag, under
     * 4 GB of memory, or older than Android 10. (Blur needs Android 12 anyway, but older phones also
     * tend to have slower graphics chips for everything else.)
     */
    fun isLowEnd(context: Context): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        if (am.isLowRamDevice) return true
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalGb = mem.totalMem / (1024.0 * 1024 * 1024)
        return totalGb < 3.6 || Build.VERSION.SDK_INT < 29
    }
}
