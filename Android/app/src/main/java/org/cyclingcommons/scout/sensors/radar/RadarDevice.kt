package org.cyclingcommons.scout.sensors.radar

/** One row in the pair list: a discovered or remembered radar peripheral. */
data class RadarDeviceRow(
    val address: String,
    val name: String?,
    /** Higher = closer. Bonded-only seeds use [RSSI_UNKNOWN]. */
    val rssi: Int = RSSI_UNKNOWN,
    /** Name or advertised service UUID looks like a bike radar. */
    val likelyRadar: Boolean = false,
) {
    companion object {
        const val RSSI_UNKNOWN = -999
    }
}
