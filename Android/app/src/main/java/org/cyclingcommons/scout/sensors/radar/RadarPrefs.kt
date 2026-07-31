package org.cyclingcommons.scout.sensors.radar

import android.content.Context

/** Persists preferred radar transport + device ids. */
class RadarPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var transport: RadarTransport
        get() = RadarTransport.entries.getOrElse(prefs.getInt(KEY_TRANSPORT, 0)) { RadarTransport.AUTO }
        set(value) {
            prefs.edit().putInt(KEY_TRANSPORT, value.ordinal).apply()
        }

    var address: String?
        get() = prefs.getString(KEY_ADDRESS, null)
        set(value) {
            prefs.edit().putString(KEY_ADDRESS, value).apply()
        }

    var name: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) {
            prefs.edit().putString(KEY_NAME, value).apply()
        }

    var antDeviceNumber: Int?
        get() {
            val v = prefs.getInt(KEY_ANT_NUM, -1)
            return if (v < 0) null else v
        }
        set(value) {
            prefs.edit().putInt(KEY_ANT_NUM, value ?: -1).apply()
        }

    /** Last known BLE GAP / Utility name for a MAC (Magene often omits name in ads). */
    fun rememberedName(address: String): String? =
        prefs.getString(KEY_KNOWN_PREFIX + address.uppercase(), null)

    fun rememberName(address: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        prefs.edit().putString(KEY_KNOWN_PREFIX + address.uppercase(), trimmed).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "scout_radar"
        private const val KEY_ADDRESS = "ble_address"
        private const val KEY_NAME = "ble_name"
        private const val KEY_TRANSPORT = "transport"
        private const val KEY_ANT_NUM = "ant_device_number"
        private const val KEY_KNOWN_PREFIX = "known_name_"
    }
}
