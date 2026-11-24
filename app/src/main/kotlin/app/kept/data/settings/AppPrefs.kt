package app.kept.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Small app-wide flags: updates, what's-new bookkeeping, whether we've asked for notifications. */
class AppPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("app", Context.MODE_PRIVATE)

    private val _autoUpdate = MutableStateFlow(prefs.getBoolean(AUTO_UPDATE, true))
    val autoUpdate: StateFlow<Boolean> = _autoUpdate.asStateFlow()

    fun setAutoUpdate(on: Boolean) {
        prefs.edit().putBoolean(AUTO_UPDATE, on).apply()
        _autoUpdate.value = on
    }

    /** "Reduce visual effects": null = decide from the phone (light on budget phones), else the user's choice. */
    private val _reduceEffects = MutableStateFlow(if (prefs.contains(REDUCE_EFFECTS)) prefs.getBoolean(REDUCE_EFFECTS, false) else null)
    val reduceEffects: StateFlow<Boolean?> = _reduceEffects.asStateFlow()

    fun setReduceEffects(on: Boolean?) {
        prefs.edit().apply { if (on == null) remove(REDUCE_EFFECTS) else putBoolean(REDUCE_EFFECTS, on) }.apply()
        _reduceEffects.value = on
    }

    var lastUpdateCheckMillis: Long
        get() = prefs.getLong(LAST_CHECK, 0L)
        set(v) = prefs.edit().putLong(LAST_CHECK, v).apply()

    /** The newest version whose "What's new" the user has seen. 0 = never recorded. */
    var lastSeenVersionCode: Int
        get() = prefs.getInt(LAST_SEEN, 0)
        set(v) = prefs.edit().putInt(LAST_SEEN, v).apply()

    var askedForNotifications: Boolean
        get() = prefs.getBoolean(ASKED_NOTIFICATIONS, false)
        set(v) = prefs.edit().putBoolean(ASKED_NOTIFICATIONS, v).apply()

    /** The newest update build we've already opened Android's install prompt for (phones without silent installs). */
    var promptedUpdateBuild: Int
        get() = prefs.getInt(PROMPTED_BUILD, 0)
        set(v) = prefs.edit().putInt(PROMPTED_BUILD, v).apply()

    /** The user went to allow "Install unknown apps": carry on installing when they come back. */
    var installAfterPermission: Boolean
        get() = prefs.getBoolean(INSTALL_AFTER_PERMISSION, false)
        set(v) = prefs.edit().putBoolean(INSTALL_AFTER_PERMISSION, v).apply()

    private companion object {
        const val REDUCE_EFFECTS = "reduce_effects"
        const val PROMPTED_BUILD = "prompted_update_build"
        const val INSTALL_AFTER_PERMISSION = "install_after_permission"
        const val AUTO_UPDATE = "auto_update"
        const val LAST_CHECK = "last_update_check"
        const val LAST_SEEN = "last_seen_version_code"
        const val ASKED_NOTIFICATIONS = "asked_notifications"
    }
}
