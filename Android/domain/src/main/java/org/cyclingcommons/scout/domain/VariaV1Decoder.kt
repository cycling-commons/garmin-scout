package org.cyclingcommons.scout.domain

/**
 * Stateful decoder for Varia-family V1 notifications on characteristic `6a4e3203`.
 *
 * Layout (community docs / bike-radar-docs PROTOCOL.md):
 * - 1 byte, low nibble 0x2: heartbeat
 * - 6 bytes, byte0 == 0x06: sector amplitude (ignored)
 * - 1 + 3N: [seq][vid, dist, flagOrSpeed]*N
 *
 * Present vehicles: vid bit7 set, not 0xFD; dist != 0xFF.
 * Distance = metres. Closing speed: if the third byte is > 1, treat as **kph**
 * (legacy RTL515-style reports); values 0/1 are the RearVue-820 “flag” and mean
 * speed unknown (V2 carries velocity on those devices).
 */
class VariaV1Decoder(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val staleMs: Long = STALE_MS,
) {
    private data class Track(
        val rangeM: Int,
        val closingSpeedMps: Float?,
        val lastSeen: Long,
    )

    private val tracks = HashMap<Int, Track>()

    fun reset() {
        tracks.clear()
    }

    fun feed(payload: ByteArray) {
        val now = nowMs()
        when {
            payload.size >= 4 && (payload.size - 1) % 3 == 0 &&
                (payload[0].toInt() and 0x0F) == 0x02 -> ingestThreat(payload, now)
            else -> Unit // heartbeat / sector / unknown — just age tracks
        }
        pruneStale(now)
    }

    fun snapshot(): List<RadarTarget> {
        pruneStale(nowMs())
        return tracks.values
            .sortedBy { it.rangeM }
            .take(8)
            .map {
                RadarTarget(
                    occupied = true,
                    rangeM = it.rangeM,
                    closingSpeedMps = it.closingSpeedMps,
                )
            }
    }

    private fun ingestThreat(payload: ByteArray, now: Long) {
        var i = 1
        while (i + 2 < payload.size) {
            val vid = payload[i].toInt() and 0xFF
            val dist = payload[i + 1].toInt() and 0xFF
            val third = payload[i + 2].toInt() and 0xFF
            i += 3
            if (vid == 0x00 || vid == 0xFD || vid < 0x80) continue
            if (dist == 0xFF) continue
            val id = vid and 0x7F
            val speedMps =
                if (third > 1) {
                    // Legacy reports often put closing speed in kph here.
                    third / 3.6f
                } else {
                    null
                }
            tracks[id] = Track(rangeM = dist, closingSpeedMps = speedMps, lastSeen = now)
        }
    }

    private fun pruneStale(now: Long) {
        tracks.entries.removeAll { now - it.value.lastSeen > staleMs }
    }

    companion object {
        const val STALE_MS = 2_000L
    }
}
