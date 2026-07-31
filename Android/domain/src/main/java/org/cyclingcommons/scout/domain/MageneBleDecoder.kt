package org.cyclingcommons.scout.domain

/**
 * Magene L508-family proprietary BLE radar stream (char `8ce5cc02`).
 *
 * After writing enable `57 09 01`, notifications look like:
 * `57 09 00 | page | threats | side | range0..2 | speed0..1`
 * page `0x30` = targets 1–4, `0x31` = targets 5–8.
 * Community decode (gist antokne/1c4ce6e4…); display-only → normalized [RadarTarget].
 */
class MageneBleDecoder(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val staleMs: Long = STALE_MS,
) {
    private data class Track(
        val rangeM: Int,
        val closingSpeedMps: Float?,
        val lastSeen: Long,
    )

    private val slots = arrayOfNulls<Track>(8)

    fun reset() {
        slots.fill(null)
    }

    fun feed(payload: ByteArray) {
        if (payload.size < 11) return
        if ((payload[0].toInt() and 0xFF) != 0x57) return
        if ((payload[1].toInt() and 0xFF) != 0x09) return
        val page = payload[3].toInt() and 0xFF
        val base =
            when (page) {
                0x30 -> 0
                0x31 -> 4
                else -> return
            }
        val now = nowMs()
        val threats = payload[4].toInt() and 0xFF
        val r0 = payload[6].toInt() and 0xFF
        val r1 = payload[7].toInt() and 0xFF
        val r2 = payload[8].toInt() and 0xFF
        val s0 = payload[9].toInt() and 0xFF
        val s1 = payload[10].toInt() and 0xFF

        val ranges = intArrayOf(
            (r0 and 0x3F) * RANGE_STEP_CM / 100,
            (((r0 shr 6) and 0x03) or ((r1 and 0x0F) shl 2)) * RANGE_STEP_CM / 100,
            (((r1 shr 4) and 0x0F) or ((r2 and 0x03) shl 4)) * RANGE_STEP_CM / 100,
            ((r2 shr 2) and 0x3F) * RANGE_STEP_CM / 100,
        )
        val speeds = intArrayOf(
            s0 and 0x0F,
            (s0 shr 4) and 0x0F,
            s1 and 0x0F,
            (s1 shr 4) and 0x0F,
        )

        for (i in 0 until 4) {
            val threat = (threats shr (i * 2)) and 0x03
            val slot = base + i
            if (threat == 0) {
                slots[slot] = null
                continue
            }
            val rangeM = ranges[i].coerceIn(0, 254)
            val speedNibble = speeds[i]
            val mps =
                if (speedNibble > 0) speedNibble * SPEED_STEP_MPS else null
            slots[slot] = Track(rangeM = rangeM, closingSpeedMps = mps, lastSeen = now)
        }
        pruneStale(now)
    }

    fun snapshot(): List<RadarTarget> {
        pruneStale(nowMs())
        return slots.mapNotNull { t ->
            t?.let {
                RadarTarget(
                    occupied = true,
                    rangeM = it.rangeM,
                    closingSpeedMps = it.closingSpeedMps,
                )
            }
        }.sortedBy { it.rangeM }.take(8)
    }

    private fun pruneStale(now: Long) {
        for (i in slots.indices) {
            val t = slots[i] ?: continue
            if (now - t.lastSeen > staleMs) slots[i] = null
        }
    }

    companion object {
        const val STALE_MS = 2_000L
        /** Range nibble step ≈ 3.125 m */
        private const val RANGE_STEP_CM = 312 // 3.12 m ≈ 3.125
        private const val SPEED_STEP_MPS = 3.04f

        val ENABLE_RADAR: ByteArray = byteArrayOf(0x57, 0x09, 0x01)
        val DISABLE_RADAR: ByteArray = byteArrayOf(0x57, 0x09, 0x00)
    }
}
