package org.cyclingcommons.scout.domain

/**
 * Live vehicle-count mirror of the parser rule (display only; FIT stays raw).
 * Call [onSample] once per second with the occupied target count while TRACKING,
 * or pass null when not tracking.
 */
class VehicleCounter {
    var carCount: Int = 0
        private set
    private var prevCount: Int = 0
    private var pendingRise: Int = 0
    var lastCarSpeedKph: Int = -1
        private set
    var radarLive: Boolean = false
        private set

    fun onSample(
        tracking: Boolean,
        occupiedCount: Int,
        nearestClosingKph: Int,
        riderKph: Int,
    ) {
        if (!tracking) {
            prevCount = 0
            pendingRise = 0
            radarLive = false
            return
        }
        radarLive = true
        val count = occupiedCount.coerceAtLeast(0)
        if (pendingRise > 0 && count > 0) {
            carCount += pendingRise
        }
        val rise = if (count > prevCount) count - prevCount else 0
        if (count > 0 && prevCount > 0 && nearestClosingKph >= 0) {
            lastCarSpeedKph = nearestClosingKph + riderKph
        }
        pendingRise = rise
        prevCount = count
    }

    fun resetRide() {
        carCount = 0
        prevCount = 0
        pendingRise = 0
        lastCarSpeedKph = -1
        radarLive = false
    }
}

fun clampRadarByte(v: Int): Int = v.coerceIn(0, 254)
