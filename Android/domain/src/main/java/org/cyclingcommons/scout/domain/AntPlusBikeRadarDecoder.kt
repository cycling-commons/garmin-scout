package org.cyclingcommons.scout.domain

/**
 * Decodes ANT+ Bike Radar data pages 48 / 49 (device type 40).
 * Layout matches public community references (range LSB 3.125 m, speed LSB 3.04 m/s).
 */
object AntPlusBikeRadarDecoder {
    const val DEVICE_TYPE = 40
    const val CHANNEL_PERIOD = 4084
    const val RF_FREQUENCY = 57 // 2457 MHz ANT+
    const val PAGE_TARGETS_A = 48
    const val PAGE_TARGETS_B = 49

    private const val RANGE_LSB_M = 3.125
    private const val SPEED_LSB_MPS = 3.04

    /**
     * Merge page A (slots 0–3) and/or page B (slots 4–7) into up to 8 targets.
     * Pass null for a page that hasn't arrived yet — previous slots for that half stay empty.
     */
    fun decodePages(pageA: ByteArray?, pageB: ByteArray?): List<RadarTarget> {
        val out = Array(8) { RadarTarget(occupied = false, rangeM = 0, closingSpeedMps = null) }
        if (pageA != null && pageA.isNotEmpty() && (pageA[0].toInt() and 0xFF) == PAGE_TARGETS_A) {
            decodeHalf(pageA, 0, out)
        }
        if (pageB != null && pageB.isNotEmpty() && (pageB[0].toInt() and 0xFF) == PAGE_TARGETS_B) {
            decodeHalf(pageB, 4, out)
        }
        return out.filter { it.occupied }
    }

    /** Decode a single 8-byte broadcast payload; returns targets for that half only. */
    fun decodePage(payload: ByteArray): List<RadarTarget> {
        if (payload.isEmpty()) return emptyList()
        return when (payload[0].toInt() and 0xFF) {
            PAGE_TARGETS_A -> decodePages(payload, null)
            PAGE_TARGETS_B -> decodePages(null, payload)
            else -> emptyList()
        }
    }

    private fun decodeHalf(page: ByteArray, slotBase: Int, out: Array<RadarTarget>) {
        if (page.size < 8) return
        val levelBits = page[1].toInt() and 0xFF
        val rangeBits =
            (page[3].toInt() and 0xFF) or
                ((page[4].toInt() and 0xFF) shl 8) or
                ((page[5].toInt() and 0xFF) shl 16)
        val speedBits =
            (page[6].toInt() and 0xFF) or
                ((page[7].toInt() and 0xFF) shl 8)
        for (i in 0 until 4) {
            val threat = (levelBits shr (i * 2)) and 0x03
            if (threat == 0) {
                out[slotBase + i] = RadarTarget(false, 0, null)
                continue
            }
            val rangeRaw = (rangeBits shr (i * 6)) and 0x3F
            val speedRaw = (speedBits shr (i * 4)) and 0x0F
            val rangeM = (rangeRaw * RANGE_LSB_M).toInt().coerceAtLeast(0)
            val closingMps = (speedRaw * SPEED_LSB_MPS).toFloat()
            out[slotBase + i] =
                RadarTarget(occupied = true, rangeM = rangeM, closingSpeedMps = closingMps)
        }
    }
}
