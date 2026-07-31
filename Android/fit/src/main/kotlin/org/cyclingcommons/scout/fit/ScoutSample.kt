package org.cyclingcommons.scout.fit

/**
 * One Scout `record` (~1 s while RUNNING) — SPEC §4.2 only:
 * timestamp, lat/lon (when available), speed (when available), five Scout channels.
 * Nothing else (privacy). Radar uses [RADAR_NA] when not tracking.
 */
data class ScoutSample(
    /** FIT epoch seconds (since 1989-12-31). */
    val timestampFit: Long,
    /** Semicircles; null → FIT sint32 invalid. */
    val latSemi: Int? = null,
    val lonSemi: Int? = null,
    /** m/s × 1000; null → FIT uint16 invalid. */
    val speedMmPerS: Int? = null,
    val poiType: Int = 0,
    val poiDetail: Int = 0,
    val radarCount: Int = RADAR_NA,
    val radarNear: Int = RADAR_NA,
    val radarSpeed: Int = RADAR_NA,
) {
    companion object {
        const val RADAR_NA = 255
        const val POSITION_INVALID = 0x7FFFFFFF
        const val SPEED_INVALID = 0xFFFF

        /** Degrees → FIT semicircles. */
        fun degreesToSemi(degrees: Double): Int =
            (degrees * (1L shl 31) / 180.0).toLong().toInt()

        /** Unix epoch millis → FIT timestamp (seconds since 1989-12-31). */
        fun unixMsToFit(unixMs: Long): Long =
            (unixMs / 1000L) - FIT_EPOCH_OFFSET_S

        /** Seconds between Unix epoch and FIT epoch (1989-12-31 00:00:00 UTC). */
        const val FIT_EPOCH_OFFSET_S = 631065600L
    }
}
