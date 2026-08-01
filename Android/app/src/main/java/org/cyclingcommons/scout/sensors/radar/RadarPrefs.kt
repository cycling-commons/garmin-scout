package org.cyclingcommons.scout.sensors.radar

import android.content.Context
import androidx.core.content.edit

/** Persists preferred radar transport + device ids. */
class RadarPrefs(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var transport: RadarTransport
        get() = RadarTransport.entries
            .getOrElse(prefs.getInt(KEY_TRANSPORT, 0)) { RadarTransport.AUTO }
        set(value) = prefs.edit { putInt(KEY_TRANSPORT, value.ordinal) }

    var address: String?
        get() = prefs.getString(KEY_ADDRESS, null)
        set(value) = prefs.edit { putString(KEY_ADDRESS, value) }

    var name: String?
        get() = prefs.getString(KEY_NAME, null)
        set(value) = prefs.edit { putString(KEY_NAME, value) }

    var antDeviceNumber: Int?
        get() = prefs.getInt(KEY_ANT_NUM, -1).takeIf { it >= 0 }
        set(value) = prefs.edit { putInt(KEY_ANT_NUM, value ?: -1) }

    /** Last known BLE GAP / Utility name for a MAC (Magene often omits name in ads). */
    fun rememberedName(address: String): String? =
        prefs.getString(KEY_KNOWN_PREFIX + address.uppercase(), null)

    fun rememberName(address: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        prefs.edit { putString(KEY_KNOWN_PREFIX + address.uppercase(), trimmed) }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    private companion object {
        const val PREFS = "scout_radar"
        const val KEY_ADDRESS = "ble_address"
        const val KEY_NAME = "ble_name"
        const val KEY_TRANSPORT = "transport"
        const val KEY_ANT_NUM = "ant_device_number"
        const val KEY_KNOWN_PREFIX = "known_name_"
    }
}
