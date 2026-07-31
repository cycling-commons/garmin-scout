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
    val speedMps: Float?,
    val hasSpeed: Boolean,
    val timeMs: Long,
)

/**
 * ~1 Hz GPS while recording. GPS provider only (TECHNICAL §5 — no redundant network poll).
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
            latest = LocationFix(
                latitude = location.latitude,
                longitude = location.longitude,
                speedMps = if (location.hasSpeed()) location.speed else null,
                hasSpeed = location.hasSpeed(),
                timeMs = location.time,
            )
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
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    listener,
                    Looper.getMainLooper(),
                )
                listening = true
                seedLastKnown()
            }
        } catch (_: SecurityException) {
            listening = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun seedLastKnown() {
        if (!hasPermission()) return
        val last = runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            ?: return
        if (latest == null) {
            latest = LocationFix(
                latitude = last.latitude,
                longitude = last.longitude,
                speedMps = if (last.hasSpeed()) last.speed else null,
                hasSpeed = last.hasSpeed(),
                timeMs = last.time,
            )
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
}
