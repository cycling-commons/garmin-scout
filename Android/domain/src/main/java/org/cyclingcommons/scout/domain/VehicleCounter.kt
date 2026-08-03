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

    fun onSample(
        tracking: Boolean,
        occupiedCount: Int,
        nearestClosingKph: Int,
        riderKph: Int,
    ) {
        if (!tracking) {
            // SPEC §8.5.5: never credit an arrival across a coverage gap.
            prevCount = 0
            pendingRise = 0
            return
        }
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
    }

    fun restore(carCount: Int, lastCarSpeedKph: Int) {
        this.carCount = carCount.coerceAtLeast(0)
        this.lastCarSpeedKph = lastCarSpeedKph
        prevCount = 0
        pendingRise = 0
    }
}

fun clampRadarByte(v: Int): Int = v.coerceIn(0, 254)
