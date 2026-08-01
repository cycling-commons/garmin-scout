package org.cyclingcommons.scout

import android.content.Context
import androidx.core.content.edit
import org.cyclingcommons.scout.ui.theme.ThemeMode

/** App-wide settings (P5). Defaults match SPEC battery: keep-screen-on off. */
class AppPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Strip speeds in mph when true; kph when false. */
    var imperial: Boolean
        get() = prefs.getBoolean(KEY_IMPERIAL, false)
        set(value) = prefs.edit { putBoolean(KEY_IMPERIAL, value) }

    /** Keep screen awake only while timer == RUNNING. Default off. */
    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN, false)
        set(value) = prefs.edit { putBoolean(KEY_KEEP_SCREEN, value) }

    /**
     * Light, dark, or whatever the phone is doing. Sun and dusk want different
     * answers, so this is the rider's call rather than ours.
     */
    var themeMode: ThemeMode
        get() = prefs.getString(KEY_THEME, null)
            ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
        set(value) = prefs.edit { putString(KEY_THEME, value.name) }

    /** The welcome screen is a first-run primer, not a splash on every launch. */
    var introSeen: Boolean
        get() = prefs.getBoolean(KEY_INTRO_SEEN, false)
        set(value) = prefs.edit { putBoolean(KEY_INTRO_SEEN, value) }

    private companion object {
        const val PREFS = "scout_app"
        const val KEY_IMPERIAL = "imperial"
        const val KEY_KEEP_SCREEN = "keep_screen_on"
        const val KEY_THEME = "theme_mode"
        const val KEY_INTRO_SEEN = "intro_seen"
    }
}
