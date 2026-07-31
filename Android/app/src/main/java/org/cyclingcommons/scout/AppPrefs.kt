package org.cyclingcommons.scout

import android.content.Context

/** App-wide settings (P5). Defaults match SPEC battery: keep-screen-on off. */
class AppPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Strip speeds in mph when true; kph when false. */
    var imperial: Boolean
        get() = prefs.getBoolean(KEY_IMPERIAL, false)
        set(value) {
            prefs.edit().putBoolean(KEY_IMPERIAL, value).apply()
        }

    /** Keep screen awake only while timer == RUNNING. Default off. */
    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN, false)
        set(value) {
            prefs.edit().putBoolean(KEY_KEEP_SCREEN, value).apply()
        }

    companion object {
        private const val PREFS = "scout_app"
        private const val KEY_IMPERIAL = "imperial"
        private const val KEY_KEEP_SCREEN = "keep_screen_on"
    }
}
