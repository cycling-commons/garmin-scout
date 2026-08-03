package org.cyclingcommons.scout.domain

/**
 * Live vehicle-count mirror of the parser rule (display only; FIT stays raw).
 * Call [onSample] once per second with the occupied target count while TRACKING.
 *
 * Count and speed commit together on a **leave** only when that leave looks like
 * a finished overtake: stretch ≥2 s, valid closing speed, and during the stretch
 * the nearest target got within [PASS_CONFIRM_M] (a car that turns away farther
 * out never reaches that, so it must not inflate the tally — including when
 * other cars are still behind).
 */
class VehicleCounter {
    var carCount: Int = 0
        private set
    private var prevCount: Int = 0
    private var heldClosingKph: Int = -1
    private var heldRangeM: Int = -1
    /** Closest nearest-range seen while the current stretch has been occupied. */
    private var minRangeInStretchM: Int = Int.MAX_VALUE
    private var stretchLen: Int = 0
    var lastCarSpeedKph: Int = -1
        private set

    fun onSample(
        tracking: Boolean,
        occupiedCount: Int,
        nearestClosingKph: Int,
        nearestRangeM: Int,
        riderKph: Int,
    ) {
        if (!tracking) {
            clearStretch()
            prevCount = 0
            return
        }
        val count = occupiedCount.coerceAtLeast(0)
        val speedValid = nearestClosingKph >= 0
        val rangeValid = nearestRangeM >= 0

        // Leave: only a real pass if the nearest target got close at some point.
        // Mid-range turn-away (even with cars still behind) never confirms.
        val passLeave =
            count < prevCount &&
                heldClosingKph >= 0 &&
                stretchLen >= 2 &&
                minRangeInStretchM <= PASS_CONFIRM_M &&
                heldRangeM in 0..PASS_LEAVE_MAX_M
        if (passLeave) {
            carCount += prevCount - count
            lastCarSpeedKph = heldClosingKph + riderKph
        }

        if (count == 0) {
            clearStretch()
        } else {
            stretchLen = if (prevCount == 0) 1 else stretchLen + 1
            if (speedValid) {
                heldClosingKph = nearestClosingKph
            }
            if (rangeValid) {
                heldRangeM = nearestRangeM
                if (count < prevCount) {
                    // New nearest after a departure — don't inherit the departed
                    // car's closeness (that would make the next car count early).
                    minRangeInStretchM = nearestRangeM
                } else if (nearestRangeM < minRangeInStretchM) {
                    minRangeInStretchM = nearestRangeM
                }
            }
            // Keep stretchLen across turn-aways: still continuously occupied.
        }

        prevCount = count
    }

    private fun clearStretch() {
        heldClosingKph = -1
        heldRangeM = -1
        minRangeInStretchM = Int.MAX_VALUE
        stretchLen = 0
    }

    fun resetRide() {
        carCount = 0
        prevCount = 0
        clearStretch()
        lastCarSpeedKph = -1
    }

    fun restore(carCount: Int, lastCarSpeedKph: Int) {
        this.carCount = carCount.coerceAtLeast(0)
        this.lastCarSpeedKph = lastCarSpeedKph
        prevCount = 0
        clearStretch()
    }

    companion object {
        /**
         * Nearest range must have reached this (metres) during the stretch
         * before a leave counts as a pass. Turn-aways farther out never confirm.
         */
        const val PASS_CONFIRM_M: Int = 10

        /**
         * Previous-second nearest range must still be within this on the leave
         * tick (last reading before overtake / drop).
         */
        const val PASS_LEAVE_MAX_M: Int = 20
    }
}

fun clampRadarByte(v: Int): Int = v.coerceIn(0, 254)
