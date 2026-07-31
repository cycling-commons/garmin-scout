package org.cyclingcommons.scout.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MageneBleDecoderTest {
    @Test
    fun emptyRoad() {
        val d = MageneBleDecoder(nowMs = { 1_000L })
        d.feed(hex("57 09 00 30 00 00 00 00 00 00 00"))
        assertTrue(d.snapshot().isEmpty())
    }

    @Test
    fun oneCar() {
        val d = MageneBleDecoder(nowMs = { 1_000L })
        // gist: 570900 30 01 00 29 00 00 04 00
        d.feed(hex("57 09 00 30 01 00 29 00 00 04 00"))
        val t = d.snapshot()
        assertEquals(1, t.size)
        assertTrue(t[0].occupied)
        assertEquals((0x29 and 0x3F) * 312 / 100, t[0].rangeM)
        assertEquals(4 * 3.04f, t[0].closingSpeedMps!!, 0.01f)
    }

    @Test
    fun pageBAddsHigherSlots() {
        val d = MageneBleDecoder(nowMs = { 1_000L })
        d.feed(hex("57 09 00 30 55 00 68 BA B2 44 44"))
        d.feed(hex("57 09 00 31 01 00 2E 00 00 04 00"))
        assertEquals(5, d.snapshot().size)
    }

    private fun hex(s: String): ByteArray =
        s.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.toInt(16).toByte() }.toByteArray()
}
