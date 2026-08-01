package org.cyclingcommons.scout.sensors.radar

import android.content.Context
import org.cyclingcommons.scout.domain.RadarLinkState
import org.cyclingcommons.scout.domain.RadarObservation

enum class RadarTransport {
    AUTO,
    ANT_PLUS,
    BLE,
}

/**
 * SPEC §8.2: ANT+ if usable, else BLE. User may force a transport.
 * Scan/search only from the pair UI; ride path connects only.
 */
class CompositeRadarSession(context: Context) {
    private val app = context.applicationContext
    val ble = BleRadarSession(app)
    val ant = AntPlusRadarSession(app)
    private val prefs = RadarPrefs(app)

    var onStateChanged: ((RadarLinkState) -> Unit)? = null
        set(value) {
            field = value
            ble.onStateChanged = { if (activeIsBle()) value?.invoke(it) }
            ant.onStateChanged = { if (activeIsAnt()) value?.invoke(it) }
        }

    var onBleDeviceFound: ((RadarDeviceRow) -> Unit)? = null
        set(value) {
            field = value
            ble.onDeviceFound = value
        }

    var onAntDeviceFound: ((deviceNumber: Int) -> Unit)? = null
        set(value) {
            field = value
            ant.onDeviceFound = { num ->
                prefs.antDeviceNumber = num
                prefs.name = "ANT+ radar #$num"
                value?.invoke(num)
            }
        }

    private var active: RadarTransport? = null

    fun antAvailable(): Boolean = ant.isHardwareAvailable()

    fun state(): RadarLinkState =
        when (active) {
            RadarTransport.ANT_PLUS -> ant.state()
            RadarTransport.BLE -> ble.state()
            RadarTransport.AUTO, null -> {
                val a = ant.state()
                val b = ble.state()
                when {
                    a == RadarLinkState.TRACKING -> a
                    b == RadarLinkState.TRACKING -> b
                    a == RadarLinkState.CONNECTING || a == RadarLinkState.SCANNING -> a
                    b == RadarLinkState.CONNECTING || b == RadarLinkState.SCANNING -> b
                    a != RadarLinkState.ABSENT -> a
                    else -> b
                }
            }
        }

    fun observation(): RadarObservation =
        when (active) {
            RadarTransport.ANT_PLUS -> ant.observation()
            RadarTransport.BLE -> ble.observation()
            else -> {
                val a = ant.observation()
                if (a.tracking) a else ble.observation()
            }
        }

    /** Start/reconnect for a ride — no scan. */
    fun connectForRide() {
        // Prefer a clean BLE reconnect path (async close → reopen) over
        // disconnect-then-immediate-connect, which races Magene's exclusive link.
        when (resolveTransport()) {
            RadarTransport.ANT_PLUS -> {
                ant.disconnect()
                ble.disconnect()
                active = RadarTransport.ANT_PLUS
                ant.connect(prefs.antDeviceNumber)
            }
            RadarTransport.BLE -> {
                ant.disconnect()
                active = RadarTransport.BLE
                ble.connectBonded(prefs.address)
            }
            RadarTransport.AUTO -> {
                // resolveTransport never returns AUTO
            }
        }
    }

    fun disconnect() {
        disconnectAll()
        active = null
    }

    fun startBleScan() {
        disconnectAll()
        active = RadarTransport.BLE
        ble.startScan()
    }

    fun stopBleScan() = ble.stopScan()

    fun selectBle(row: RadarDeviceRow) {
        prefs.transport = RadarTransport.BLE
        prefs.address = row.address
        prefs.name = row.name
        prefs.antDeviceNumber = null
        active = RadarTransport.BLE
        ble.stopScan()
        ble.connect(row.address)
    }

    fun startAntSearch() {
        disconnectAll()
        prefs.transport = RadarTransport.ANT_PLUS
        active = RadarTransport.ANT_PLUS
        ant.connect(null) // wildcard search
    }

    fun forget() {
        disconnect()
        prefs.clear()
    }

    fun setTransportPreference(t: RadarTransport) {
        prefs.transport = t
    }

    fun transportPreference(): RadarTransport = prefs.transport

    private fun resolveTransport(): RadarTransport {
        return when (prefs.transport) {
            RadarTransport.BLE -> RadarTransport.BLE
            RadarTransport.ANT_PLUS ->
                if (antAvailable()) RadarTransport.ANT_PLUS else RadarTransport.BLE
            RadarTransport.AUTO ->
                if (antAvailable() && (prefs.antDeviceNumber != null || prefs.address == null)) {
                    RadarTransport.ANT_PLUS
                } else if (prefs.address != null) {
                    RadarTransport.BLE
                } else if (antAvailable()) {
                    RadarTransport.ANT_PLUS
                } else {
                    RadarTransport.BLE
                }
        }
    }

    private fun disconnectAll() {
        ble.disconnect()
        ant.disconnect()
    }

    private fun activeIsBle() = active == RadarTransport.BLE
    private fun activeIsAnt() = active == RadarTransport.ANT_PLUS
}
