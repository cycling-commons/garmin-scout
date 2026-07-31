package org.cyclingcommons.scout.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AntPlusBikeRadarDecoderTest {
    @Test
    fun decodesSingleTargetOnPageA() {
        // threat0 = 1 (bits 1:0), range0 = 8 → 25 m, speed0 = 4 → 12.16 m/s
        val page = byteArrayOf(
            48, // page A
            0x01, // threat levels
            0x00, // sides
            0x08, 0x00, 0x00, // ranges
            0x04, 0x00, // speeds
        )
        val targets = AntPlusBikeRadarDecoder.decodePage(page)
        assertEquals(1, targets.size)
        assertTrue(targets[0].occupied)
        assertEquals(25, targets[0].rangeM)
        assertEquals(43, (targets[0].closingSpeedMps!! * 3.6f).toInt())
    }

    @Test
    fun emptyThreatsYieldNoTargets() {
        val page = byteArrayOf(48, 0, 0, 0, 0, 0, 0, 0)
        assertTrue(AntPlusBikeRadarDecoder.decodePage(page).isEmpty())
    }

    @Test
    fun pageBMapsToHigherSlots() {
        val pageB = byteArrayOf(
            49,
            0x01,
            0x00,
            0x04, 0x00, 0x00, // range 4 * 3.125 = 12.5 → 12
            0x02, 0x00,
        )
        val targets = AntPlusBikeRadarDecoder.decodePage(pageB)
        assertEquals(1, targets.size)
        assertEquals(12, targets[0].rangeM)
    }
}
