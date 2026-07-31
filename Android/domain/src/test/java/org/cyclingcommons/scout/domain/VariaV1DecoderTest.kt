package org.cyclingcommons.scout.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VariaV1DecoderTest {
    @Test
    fun parsesThreatTriplet() {
        val dec = VariaV1Decoder(nowMs = { 1_000L })
        // seq=0x02, vid=0x81 (id 1), dist=40m, speed=28 kph (legacy)
        dec.feed(byteArrayOf(0x02, 0x81.toByte(), 40, 28))
        val snap = dec.snapshot()
        assertEquals(1, snap.size)
        assertEquals(40, snap[0].rangeM)
        assertTrue(snap[0].closingSpeedMps != null)
        assertEquals(28, (snap[0].closingSpeedMps!! * 3.6f).toInt())
    }

    @Test
    fun flagByteMeansNoSpeed() {
        val dec = VariaV1Decoder(nowMs = { 1_000L })
        dec.feed(byteArrayOf(0x02, 0x81.toByte(), 25, 1))
        assertNull(dec.snapshot()[0].closingSpeedMps)
    }

    @Test
    fun skipsAbsentVid() {
        val dec = VariaV1Decoder(nowMs = { 1_000L })
        dec.feed(byteArrayOf(0x02, 0x01, 10, 20)) // bit7 clear
        assertTrue(dec.snapshot().isEmpty())
    }

    @Test
    fun fitChannelsWhileTracking() {
        val obs = RadarObservation(
            state = RadarLinkState.TRACKING,
            targets = listOf(
                RadarTarget(true, 40, 28 / 3.6f),
                RadarTarget(true, 60, 40 / 3.6f),
            ),
        )
        val ch = obs.fitChannels()
        assertEquals(2, ch[0])
        assertEquals(40, ch[1])
        assertEquals(28, ch[2])
    }

    @Test
    fun fitChannelsNotTrackingAreNa() {
        val ch = RadarObservation(RadarLinkState.DISCONNECTED).fitChannels()
        assertEquals(255, ch[0])
        assertEquals(255, ch[1])
        assertEquals(255, ch[2])
    }

    @Test
    fun emptyRoadWhileTracking() {
        val ch = RadarObservation(RadarLinkState.TRACKING, emptyList()).fitChannels()
        assertEquals(0, ch[0])
        assertEquals(255, ch[1])
        assertEquals(255, ch[2])
    }
}
