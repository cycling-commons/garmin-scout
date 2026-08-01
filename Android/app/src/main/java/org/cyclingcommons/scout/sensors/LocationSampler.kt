package org.cyclingcommons.scout.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat

/** Latest fix while RUNNING. Cleared / stopped on pause. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    /** Metres per second when the provider reported it; null otherwise. */
    val speedMps: Float?,
    /** Horizontal accuracy in metres, when the provider reported it. */
    val accuracyM: Float?,
    val timeMs: Long,
)

/**
 * ~1 Hz GPS while recording. GPS provider only (TECHNICAL §5 — no redundant network poll),
 * and updates are requested only between [start] and [stop] so idle costs nothing.
 */
class LocationSampler(context: Context) {
    private val app = context.applicationContext
    private val lm = app.getSystemService(LocationManager::class.java)

    @Volatile
    var latest: LocationFix? = null
        private set

    private var listening = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            latest = location.toFix()
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (listening || !hasPermission()) return
        latest = null
        try {
            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                listener,
                Looper.getMainLooper(),
            )
            listening = true
            seedLastKnown()
        } catch (_: SecurityException) {
            listening = false
        }
    }

    fun stop() {
        if (!listening) return
        try {
            lm.removeUpdates(listener)
        } catch (_: Exception) {
            // ignore
        }
        listening = false
    }

    /** Tag the first samples with the last fix rather than nothing while GPS warms up. */
    @SuppressLint("MissingPermission")
    private fun seedLastKnown() {
        if (latest != null || !hasPermission()) return
        latest = runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }
            .getOrNull()
            ?.toFix()
    }

    private fun Location.toFix() = LocationFix(
        latitude = latitude,
        longitude = longitude,
        speedMps = if (hasSpeed()) speed else null,
        accuracyM = if (hasAccuracy()) accuracy else null,
        timeMs = time,
    )

    private companion object {
        /** SPEC §4.2 cadence — one Scout sample per second. */
        const val UPDATE_INTERVAL_MS = 1_000L
    }
}
