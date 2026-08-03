package org.cyclingcommons.scout.sensors.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleRadarSessionTest {
    @Test
    fun nameLooksLikeRadar_matchesKnownBrands() {
        assertTrue(BleRadarSession.nameLooksLikeRadar("Garmin Varia RTL515"))
        assertTrue(BleRadarSession.nameLooksLikeRadar("Magene L508"))
        assertTrue(BleRadarSession.nameLooksLikeRadar("CARBACK radar"))
        assertFalse(BleRadarSession.nameLooksLikeRadar("Pixel 8"))
        assertFalse(BleRadarSession.nameLooksLikeRadar(null))
    }

    @Test
    fun parseLocalName_readsShortAndCompleteNames() {
        val shortName = byteArrayOf(
            0x02, 0x01, 0x06,
            0x06, 0x09,
            'V'.code.toByte(), 'a'.code.toByte(), 'r'.code.toByte(),
            'i'.code.toByte(), 'a'.code.toByte(),
        )
        assertEquals("Varia", BleRadarSession.parseLocalName(shortName))

        val completeName = byteArrayOf(
            0x0A, 0x08,
            'M'.code.toByte(), 'a'.code.toByte(), 'g'.code.toByte(),
            'e'.code.toByte(), 'n'.code.toByte(), 'e'.code.toByte(),
            ' '.code.toByte(), 'L'.code.toByte(), '5'.code.toByte(),
        )
        assertEquals("Magene L5", BleRadarSession.parseLocalName(completeName))
        assertNull(BleRadarSession.parseLocalName(null))
        assertNull(BleRadarSession.parseLocalName(byteArrayOf()))
    }
}
