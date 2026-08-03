package org.cyclingcommons.scout.domain

/** Normalized radar link state — TECHNICAL §7 / SPEC §8.3 */
enum class RadarLinkState {
    ABSENT,
    SCANNING,
    CONNECTING,
    TRACKING,
    DISCONNECTED,
}

data class RadarTarget(
    val occupied: Boolean,
    /** Metres behind rider. */
    val rangeM: Int,
    /** Closing speed m/s when known; null if the stream didn't provide it. */
    val closingSpeedMps: Float? = null,
)

/**
 * Snapshot for one sample tick. Only [RadarLinkState.TRACKING] writes real FIT values;
 * everything else → 255.
 */
data class RadarObservation(
    val state: RadarLinkState = RadarLinkState.ABSENT,
    val targets: List<RadarTarget> = emptyList(),
) {
    val tracking: Boolean get() = state == RadarLinkState.TRACKING

    /** FIT (radar_count, radar_near, radar_speed) — SPEC §4.3 / §8.4 */
    fun fitChannels(): IntArray {
        if (!tracking) {
            return intArrayOf(RADAR_NA, RADAR_NA, RADAR_NA)
        }
        var count = 0
        var near = Int.MAX_VALUE
        var nearClosingKph = -1
        for (t in targets) {
            if (!t.occupied) continue
            count++
            if (t.rangeM < near) {
                near = t.rangeM
                nearClosingKph =
                    if (t.closingSpeedMps != null) {
                        (t.closingSpeedMps * 3.6f).toInt()
                    } else {
                        -1
                    }
            }
        }
        val fitNear = if (count > 0) clampRadarByte(near) else RADAR_NA
        val fitSpeed =
            if (count > 0 && nearClosingKph >= 0) clampRadarByte(nearClosingKph) else RADAR_NA
        return intArrayOf(count.coerceAtMost(8), fitNear, fitSpeed)
    }

    fun occupiedCount(): Int = targets.count { it.occupied }

    fun nearestRangeM(): Int {
        var near = Int.MAX_VALUE
        var found = false
        for (t in targets) {
            if (!t.occupied) continue
            found = true
            if (t.rangeM < near) near = t.rangeM
        }
        return if (found) near else -1
    }

    fun nearestClosingKph(): Int {
        var near = Int.MAX_VALUE
        var kph = -1
        for (t in targets) {
            if (!t.occupied) continue
            if (t.rangeM < near) {
                near = t.rangeM
                kph =
                    if (t.closingSpeedMps != null) {
                        (t.closingSpeedMps * 3.6f).toInt()
                    } else {
                        -1
                    }
            }
        }
        return kph
    }
}
