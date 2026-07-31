package org.cyclingcommons.scout.sensors.radar

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import org.cyclingcommons.scout.domain.MageneBleDecoder
import org.cyclingcommons.scout.domain.RadarLinkState
import org.cyclingcommons.scout.domain.RadarObservation
import org.cyclingcommons.scout.domain.RadarTarget
import org.cyclingcommons.scout.domain.VariaV1Decoder
import org.cyclingcommons.scout.ui.RadarDeviceRow
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * BLE bike-radar session.
 * - Varia-family: service `6a4e3200` / V1 `6a4e3203`
 * - Magene L508-family: proprietary `8ce5cc01` / enable+notify on `8ce5cc02`
 *
 * Magene Utility often shows a name that is **not** in the BLE advertisement
 * (only after GATT). We read GAP Device Name on connect and report it via
 * [onNameResolved] so the pair list can remember it.
 */
class BleRadarSession(context: Context) {
    private val app = context.applicationContext
    private val btManager = app.getSystemService(BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? get() = btManager?.adapter
    private val prefs = RadarPrefs(app)

    private val varia = VariaV1Decoder()
    private val magene = MageneBleDecoder()
    private var active: ActiveProtocol = ActiveProtocol.NONE

    private val stateRef = AtomicReference(RadarLinkState.ABSENT)
    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private var connectingAddress: String? = null
    /** Address waiting while an existing GATT tears down cleanly. */
    private var pendingConnectAddress: String? = null
    private var gattClosing = false
    private var lastCloseAtMs = 0L
    /** Pending Magene step after a GATT write finishes (one op at a time). */
    private var magenePending: MageneSetupStep = MageneSetupStep.IDLE
    private val mainHandler = Handler(Looper.getMainLooper())
    private val closeFallback = Runnable {
        val g = gatt ?: return@Runnable
        finishClose(g)
    }
    private val connectTimeout = Runnable {
        if (stateRef.get() != RadarLinkState.CONNECTING) return@Runnable
        // Device absent / unreachable — release the attempt so the ride can give up.
        val g = gatt
        if (g != null) {
            beginDisconnect(g, disableMagene = false)
        } else {
            pendingConnectAddress = null
            connectingAddress = null
            setState(RadarLinkState.DISCONNECTED)
        }
    }

    var onStateChanged: ((RadarLinkState) -> Unit)? = null
    var onDeviceFound: ((RadarDeviceRow) -> Unit)? = null
    var onNameResolved: ((address: String, name: String) -> Unit)? = null

    fun state(): RadarLinkState = stateRef.get()

    fun observation(): RadarObservation {
        val st = stateRef.get()
        if (st != RadarLinkState.TRACKING) {
            return RadarObservation(st, emptyList())
        }
        val targets: List<RadarTarget> =
            when (active) {
                ActiveProtocol.VARIA -> varia.snapshot()
                ActiveProtocol.MAGENE -> magene.snapshot()
                ActiveProtocol.NONE -> emptyList()
            }
        return RadarObservation(st, targets)
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            granted(Manifest.permission.BLUETOOTH_SCAN) &&
                granted(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun bluetoothEnabled(): Boolean = adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasBluetoothPermission() || adapter == null || !bluetoothEnabled()) {
            setState(RadarLinkState.ABSENT)
            return
        }
        stopScan()
        setState(RadarLinkState.SCANNING)
        emitBondedRadarLike()
        val scanner = adapter?.bluetoothLeScanner ?: run {
            setState(RadarLinkState.ABSENT)
            return
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()
        scanning = true
        // null filters = all advertisements + scan responses (active scan)
        scanner.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun emitBondedRadarLike() {
        val bonded = try {
            adapter?.bondedDevices
        } catch (_: SecurityException) {
            null
        } ?: return
        for (device in bonded) {
            val name = resolveDeviceName(device, advertised = null)
            if (!nameLooksLikeRadar(name) && prefs.rememberedName(device.address) == null) {
                continue
            }
            emitFound(
                address = device.address,
                name = name,
                rssi = RadarDeviceRow.RSSI_UNKNOWN,
                likely = true,
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (_: Exception) {
            // ignore
        }
        if (stateRef.get() == RadarLinkState.SCANNING) {
            setState(RadarLinkState.DISCONNECTED)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        if (!hasBluetoothPermission() || adapter == null) {
            setState(RadarLinkState.ABSENT)
            return
        }
        stopScan()
        varia.reset()
        magene.reset()
        active = ActiveProtocol.NONE
        magenePending = MageneSetupStep.IDLE
        connectingAddress = address
        setState(RadarLinkState.CONNECTING)
        val existing = gatt
        if (existing != null) {
            // Tear down first — Magene stays exclusive if we openGatt while still linked.
            pendingConnectAddress = address
            beginDisconnect(existing, disableMagene = true)
            return
        }
        openGatt(address)
    }

    fun connectBonded(address: String?) {
        if (address.isNullOrBlank()) {
            setState(RadarLinkState.ABSENT)
            return
        }
        connect(address)
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        stopScan()
        mainHandler.removeCallbacks(closeFallback)
        pendingConnectAddress = null
        connectingAddress = null
        varia.reset()
        magene.reset()
        active = ActiveProtocol.NONE
        magenePending = MageneSetupStep.IDLE
        val g = gatt
        if (g == null) {
            if (stateRef.get() != RadarLinkState.ABSENT) {
                setState(RadarLinkState.DISCONNECTED)
            }
            return
        }
        beginDisconnect(g, disableMagene = true)
    }

    /**
     * Disconnect without immediately close(). Closing too early leaves Magene
     * thinking the phone is still connected until power-cycle.
     */
    @SuppressLint("MissingPermission")
    private fun beginDisconnect(g: BluetoothGatt, disableMagene: Boolean) {
        if (gattClosing && gatt === g) return
        gattClosing = true
        // Drop Magene setup / reconnect timers so they can't race tear-down.
        mainHandler.removeCallbacksAndMessages(null)
        val doDisconnect = Runnable {
            try {
                g.disconnect()
            } catch (_: Exception) {
                finishClose(g)
                return@Runnable
            }
            mainHandler.removeCallbacks(closeFallback)
            mainHandler.postDelayed(closeFallback, 1_200L)
        }
        if (disableMagene && active == ActiveProtocol.MAGENE) {
            try {
                val ch = g.getService(MAGENE_SERVICE)?.getCharacteristic(MAGENE_DATA_CHAR)
                if (ch != null) {
                    writeBytes(g, ch, MageneBleDecoder.DISABLE_RADAR, noResponse = true)
                    mainHandler.postDelayed(doDisconnect, 80L)
                    return
                }
            } catch (_: Exception) {
                // fall through
            }
        }
        doDisconnect.run()
    }

    @SuppressLint("MissingPermission")
    private fun finishClose(g: BluetoothGatt) {
        mainHandler.removeCallbacks(closeFallback)
        mainHandler.removeCallbacks(connectTimeout)
        try {
            g.close()
        } catch (_: Exception) {
            // ignore
        }
        if (gatt === g) {
            gatt = null
        }
        gattClosing = false
        lastCloseAtMs = System.currentTimeMillis()
        varia.reset()
        magene.reset()
        active = ActiveProtocol.NONE
        magenePending = MageneSetupStep.IDLE

        val next = pendingConnectAddress
        pendingConnectAddress = null
        if (next != null) {
            connectingAddress = next
            setState(RadarLinkState.CONNECTING)
            mainHandler.postDelayed({
                if (gatt == null && connectingAddress == next) {
                    openGattNow(next)
                }
            }, RECONNECT_SETTLE_MS)
        } else {
            connectingAddress = null
            if (stateRef.get() != RadarLinkState.ABSENT) {
                setState(RadarLinkState.DISCONNECTED)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openGatt(address: String) {
        val sinceClose = System.currentTimeMillis() - lastCloseAtMs
        if (lastCloseAtMs > 0L && sinceClose < RECONNECT_SETTLE_MS) {
            pendingConnectAddress = address
            mainHandler.postDelayed({
                val next = pendingConnectAddress ?: return@postDelayed
                if (gatt != null) return@postDelayed
                pendingConnectAddress = null
                openGattNow(next)
            }, RECONNECT_SETTLE_MS - sinceClose)
            return
        }
        openGattNow(address)
    }

    @SuppressLint("MissingPermission")
    private fun openGattNow(address: String) {
        val device = try {
            adapter!!.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            setState(RadarLinkState.DISCONNECTED)
            return
        }
        gattClosing = false
        gatt = if (Build.VERSION.SDK_INT >= 23) {
            device.connectGatt(app, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            @Suppress("DEPRECATION")
            device.connectGatt(app, false, gattCallback)
        }
        mainHandler.removeCallbacks(connectTimeout)
        mainHandler.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS)
    }

    private fun setState(s: RadarLinkState) {
        if (s == RadarLinkState.TRACKING) {
            mainHandler.removeCallbacks(connectTimeout)
        }
        stateRef.set(s)
        onStateChanged?.invoke(s)
    }

    private fun granted(p: String): Boolean =
        ContextCompat.checkSelfPermission(app, p) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun resolveDeviceName(device: BluetoothDevice, advertised: String?): String? {
        val alias =
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    device.alias
                } catch (_: SecurityException) {
                    null
                }
            } else {
                null
            }
        val bondedName = try {
            device.name
        } catch (_: SecurityException) {
            null
        }
        return listOf(
            advertised,
            alias,
            bondedName,
            prefs.rememberedName(device.address),
        ).firstOrNull { !it.isNullOrBlank() }
    }

    private fun emitFound(address: String, name: String?, rssi: Int, likely: Boolean) {
        val remembered = prefs.rememberedName(address)
        val display = name?.takeIf { it.isNotBlank() } ?: remembered
        onDeviceFound?.invoke(
            RadarDeviceRow(
                address = address,
                name = display,
                rssi = rssi,
                likelyRadar = likely || nameLooksLikeRadar(display),
            ),
        )
    }

    private fun publishResolvedName(address: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        prefs.rememberName(address, trimmed)
        onNameResolved?.invoke(address, trimmed)
        emitFound(address, trimmed, RadarDeviceRow.RSSI_UNKNOWN, likely = true)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val record = result.scanRecord
            val advertised = record?.deviceName
                ?: parseLocalName(record?.bytes)
            val name = resolveDeviceName(device, advertised)
            val rssi = result.rssi
            val uuidHit = record?.serviceUuids.orEmpty().any { parcel ->
                val u = parcel.uuid
                u == MAGENE_SERVICE || u == RADAR_SERVICE || u == GARMIN_MEMBER_SERVICE
            }
            val likely = uuidHit || nameLooksLikeRadar(name) ||
                prefs.rememberedName(device.address) != null
            if (!likely && rssi < -70) return
            if (!likely && name.isNullOrBlank() && rssi < -55) return
            emitFound(device.address, name, rssi, likely)
        }

        override fun onScanFailed(errorCode: Int) {
            setState(RadarLinkState.DISCONNECTED)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (gattClosing || gatt !== g) {
                    try {
                        g.disconnect()
                    } catch (_: Exception) {
                        // ignore
                    }
                    return
                }
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                finishClose(g)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                setState(RadarLinkState.DISCONNECTED)
                return
            }
            startRadarProtocol(g)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value
            handleNameRead(characteristic.uuid, value, status, g.device.address)
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleNameRead(characteristic.uuid, value, status, g.device.address)
        }

        private fun handleNameRead(
            uuid: UUID,
            value: ByteArray?,
            status: Int,
            fallbackAddress: String,
        ) {
            if (uuid != GAP_DEVICE_NAME || status != BluetoothGatt.GATT_SUCCESS || value == null) {
                return
            }
            val name = value.toString(Charsets.UTF_8).trim('\u0000', ' ', '\n', '\r')
            val addr = connectingAddress ?: fallbackAddress
            if (name.isNotEmpty()) {
                publishResolvedName(addr, name)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (characteristic.uuid != MAGENE_DATA_CHAR || active != ActiveProtocol.MAGENE) {
                return
            }
            if (magenePending == MageneSetupStep.WAIT_ENABLE_WRITE) {
                // Enable wrote (DEFAULT path). Next: CCCD notify.
                magenePending = MageneSetupStep.WAIT_CCCD
                if (!enableNotify(g, characteristic)) {
                    magenePending = MageneSetupStep.IDLE
                    setState(RadarLinkState.TRACKING)
                    scheduleGapNameRead(g)
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Deprecated("Deprecated in Java")
        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.uuid != CCCD || active != ActiveProtocol.MAGENE) return
            if (magenePending != MageneSetupStep.WAIT_CCCD) return
            magenePending = MageneSetupStep.IDLE
            setState(RadarLinkState.TRACKING)
            // Optional secondary notify char — after primary is live.
            val alt = g.getService(MAGENE_SERVICE)?.getCharacteristic(MAGENE_NOTIFY_ALT)
            if (alt != null && status == BluetoothGatt.GATT_SUCCESS) {
                mainHandler.postDelayed({
                    if (gatt !== g || active != ActiveProtocol.MAGENE) return@postDelayed
                    enableNotify(g, alt)
                }, 80L)
            }
            scheduleGapNameRead(g)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            onNotify(characteristic.uuid, value)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            onNotify(characteristic.uuid, value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRadarProtocol(g: BluetoothGatt) {
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
            }
        } catch (_: Exception) {
            // ignore
        }
        // Prefer Magene proprietary when present — some Magene units also expose a
        // non-functional Varia-looking service that would otherwise win first.
        val mageneService = g.getService(MAGENE_SERVICE)
        val mageneChar = mageneService?.getCharacteristic(MAGENE_DATA_CHAR)
        if (mageneChar != null) {
            active = ActiveProtocol.MAGENE
            // Magene: write enable first, then CCCD notify (one GATT op at a time).
            magenePending = MageneSetupStep.WAIT_ENABLE_WRITE
            val wrote = writeBytes(g, mageneChar, MageneBleDecoder.ENABLE_RADAR, noResponse = true)
            if (!wrote) {
                writeBytes(g, mageneChar, MageneBleDecoder.ENABLE_RADAR, noResponse = false)
            } else {
                // NO_RESPONSE usually skips onCharacteristicWrite — advance after settle.
                mainHandler.postDelayed({
                    if (gatt !== g || active != ActiveProtocol.MAGENE) return@postDelayed
                    if (magenePending != MageneSetupStep.WAIT_ENABLE_WRITE) return@postDelayed
                    magenePending = MageneSetupStep.WAIT_CCCD
                    if (!enableNotify(g, mageneChar)) {
                        magenePending = MageneSetupStep.IDLE
                        setState(RadarLinkState.TRACKING)
                        scheduleGapNameRead(g)
                    }
                }, 120L)
            }
            return
        }
        val variaChar =
            g.getService(RADAR_SERVICE)?.getCharacteristic(V1_CHARACTERISTIC)
        if (variaChar != null) {
            active = ActiveProtocol.VARIA
            enableNotify(g, variaChar)
            setState(RadarLinkState.TRACKING)
            scheduleGapNameRead(g)
            return
        }
        setState(RadarLinkState.DISCONNECTED)
    }

    @SuppressLint("MissingPermission")
    private fun scheduleGapNameRead(g: BluetoothGatt) {
        mainHandler.postDelayed({
            if (gatt !== g) return@postDelayed
            val gapNameChar =
                g.getService(GAP_SERVICE)?.getCharacteristic(GAP_DEVICE_NAME) ?: return@postDelayed
            try {
                g.readCharacteristic(gapNameChar)
            } catch (_: Exception) {
                // ignore
            }
        }, 400L)
    }

    private fun onNotify(uuid: UUID, value: ByteArray) {
        when (uuid) {
            V1_CHARACTERISTIC -> varia.feed(value)
            MAGENE_DATA_CHAR, MAGENE_NOTIFY_ALT -> magene.feed(value)
            else -> Unit
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotify(g: BluetoothGatt, char: BluetoothGattCharacteristic): Boolean {
        g.setCharacteristicNotification(char, true)
        val cccd = char.getDescriptor(CCCD) ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                    BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeBytes(
        g: BluetoothGatt,
        char: BluetoothGattCharacteristic,
        bytes: ByteArray,
        noResponse: Boolean = false,
    ): Boolean {
        val type =
            if (noResponse) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
        return try {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(char, bytes, type) == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = bytes
                @Suppress("DEPRECATION")
                char.writeType = type
                @Suppress("DEPRECATION")
                g.writeCharacteristic(char)
            }
        } catch (_: Exception) {
            false
        }
    }

    private enum class ActiveProtocol { NONE, VARIA, MAGENE }

    private enum class MageneSetupStep { IDLE, WAIT_ENABLE_WRITE, WAIT_CCCD }

    companion object {
        /** Magene often rejects a new GATT until the previous link fully drops. */
        private const val RECONNECT_SETTLE_MS = 450L
        /** Abort a single connectGatt attempt if the peripheral never answers. */
        private const val CONNECT_TIMEOUT_MS = 20_000L

        fun nameLooksLikeRadar(name: String?): Boolean {
            val n = name?.lowercase() ?: return false
            return listOf(
                "magene", "varia", "garmin", "l508", "gardia", "bryton",
                "radar", "rtl515", "rtl516", "rct715", "sr30", "carback",
            ).any { n.contains(it) }
        }

        fun parseLocalName(bytes: ByteArray?): String? {
            if (bytes == null) return null
            var i = 0
            while (i + 1 < bytes.size) {
                val len = bytes[i].toInt() and 0xFF
                if (len == 0) break
                if (i + len >= bytes.size) break
                val type = bytes[i + 1].toInt() and 0xFF
                if (type == 0x08 || type == 0x09) {
                    val start = i + 2
                    val end = (i + 1 + len).coerceAtMost(bytes.size)
                    if (start < end) {
                        return String(bytes, start, end - start, Charsets.UTF_8).trim()
                            .takeIf { it.isNotEmpty() }
                    }
                }
                i += 1 + len
            }
            return null
        }

        val GAP_SERVICE: UUID =
            UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
        val GAP_DEVICE_NAME: UUID =
            UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
        val GARMIN_MEMBER_SERVICE: UUID =
            UUID.fromString("0000fe1f-0000-1000-8000-00805f9b34fb")
        val RADAR_SERVICE: UUID =
            UUID.fromString("6a4e3200-667b-11e3-949a-0800200c9a66")
        val V1_CHARACTERISTIC: UUID =
            UUID.fromString("6a4e3203-667b-11e3-949a-0800200c9a66")
        val CCCD: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val MAGENE_SERVICE: UUID =
            UUID.fromString("8ce5cc01-0a4d-11e9-ab14-d663bd873d93")
        val MAGENE_DATA_CHAR: UUID =
            UUID.fromString("8ce5cc02-0a4d-11e9-ab14-d663bd873d93")
        /** Some Magene firmwares notify on this secondary char. */
        val MAGENE_NOTIFY_ALT: UUID =
            UUID.fromString("8ce5cc03-0a4d-11e9-ab14-d663bd873d93")
    }
}
