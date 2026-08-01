package org.cyclingcommons.scout.sensors.radar

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.dsi.ant.AntService
import com.dsi.ant.channel.AntChannel
import com.dsi.ant.channel.AntChannelProvider
import com.dsi.ant.channel.AntCommandFailedException
import com.dsi.ant.channel.ChannelNotAvailableException
import com.dsi.ant.channel.IAntChannelEventHandler
import com.dsi.ant.channel.NetworkKey
import com.dsi.ant.channel.UnsupportedFeatureException
import com.dsi.ant.message.ChannelId
import com.dsi.ant.message.ChannelType
import com.dsi.ant.message.EventCode
import com.dsi.ant.message.fromant.BroadcastDataMessage
import com.dsi.ant.message.fromant.ChannelEventMessage
import com.dsi.ant.message.fromant.MessageFromAntType
import com.dsi.ant.message.ipc.AntMessageParcel
import org.cyclingcommons.scout.domain.AntPlusBikeRadarDecoder
import org.cyclingcommons.scout.domain.RadarLinkState
import org.cyclingcommons.scout.domain.RadarObservation
import org.cyclingcommons.scout.domain.RadarTarget
import java.util.concurrent.atomic.AtomicReference

/**
 * ANT+ Bike Radar (device type 40) via AntLib + ANT Radio Service.
 * Connect only while RUNNING (or during an explicit pair/search).
 */
class AntPlusRadarSession(context: Context) {
    private val app = context.applicationContext

    private var antService: AntService? = null
    private var provider: AntChannelProvider? = null
    private var channel: AntChannel? = null
    private var bound = false

    private val stateRef = AtomicReference(RadarLinkState.ABSENT)

    // Written on the ANT event thread, read on the ride tick.
    @Volatile
    private var pageA: ByteArray? = null

    @Volatile
    private var pageB: ByteArray? = null
    private var lockedDeviceNumber: Int = ChannelId.ANY_DEVICE_NUMBER

    var onStateChanged: ((RadarLinkState) -> Unit)? = null
    var onDeviceFound: ((deviceNumber: Int) -> Unit)? = null

    fun state(): RadarLinkState = stateRef.get()

    fun observation(): RadarObservation {
        val st = stateRef.get()
        if (st != RadarLinkState.TRACKING) {
            return RadarObservation(st, emptyList())
        }
        val targets: List<RadarTarget> = AntPlusBikeRadarDecoder.decodePages(pageA, pageB)
        return RadarObservation(st, targets)
    }

    fun isHardwareAvailable(): Boolean {
        if (AntService.getVersionCode(app) == AntService.SERVICE_VERSION_CODE_NOT_INSTALLED) {
            return false
        }
        return try {
            @Suppress("DEPRECATION")
            app.packageManager.getPackageInfo(ANT_RADIO_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            AntService.getVersionCode(app) != AntService.SERVICE_VERSION_CODE_NOT_INSTALLED
        }
    }

    fun connect(deviceNumber: Int? = null) {
        if (!isHardwareAvailable()) {
            setState(RadarLinkState.ABSENT)
            return
        }
        lockedDeviceNumber = deviceNumber ?: ChannelId.ANY_DEVICE_NUMBER
        pageA = null
        pageB = null
        setState(RadarLinkState.CONNECTING)
        bindAndOpen()
    }

    fun disconnect() {
        releaseChannel()
        unbind()
        pageA = null
        pageB = null
        if (stateRef.get() != RadarLinkState.ABSENT) {
            setState(RadarLinkState.DISCONNECTED)
        }
    }

    private fun bindAndOpen() {
        if (bound && provider != null) {
            openChannel()
            return
        }
        bound = AntService.bindService(app, connection)
        if (!bound) {
            setState(RadarLinkState.ABSENT)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                antService = AntService(service)
                provider = antService?.channelProvider
                openChannel()
            } catch (e: RemoteException) {
                Log.w(TAG, "ANT service connected but provider failed", e)
                setState(RadarLinkState.DISCONNECTED)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            provider = null
            antService = null
            channel = null
            setState(RadarLinkState.DISCONNECTED)
        }
    }

    private fun openChannel() {
        val p = provider ?: return
        releaseChannel()
        try {
            // ANT+ managed network key (adopter key; required for bike-radar profile).
            val antChannel = p.acquireChannelOnPrivateNetwork(app, ANT_PLUS_NETWORK_KEY)
            channel = antChannel
            antChannel.setChannelEventHandler(eventHandler)
            antChannel.assign(ChannelType.BIDIRECTIONAL_SLAVE)
            antChannel.setChannelId(
                ChannelId(
                    lockedDeviceNumber,
                    AntPlusBikeRadarDecoder.DEVICE_TYPE,
                    ChannelId.ANY_TRANSMISSION_TYPE,
                ),
            )
            antChannel.setPeriod(AntPlusBikeRadarDecoder.CHANNEL_PERIOD)
            antChannel.setRfFrequency(AntPlusBikeRadarDecoder.RF_FREQUENCY)
            antChannel.open()
            setState(RadarLinkState.CONNECTING)
        } catch (e: ChannelNotAvailableException) {
            Log.w(TAG, "No ANT channel", e)
            setState(RadarLinkState.ABSENT)
        } catch (e: UnsupportedFeatureException) {
            Log.w(TAG, "ANT+ network unsupported on this adapter", e)
            setState(RadarLinkState.ABSENT)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open ANT+ radar channel", e)
            setState(RadarLinkState.DISCONNECTED)
        }
    }

    private val eventHandler = object : IAntChannelEventHandler {
        override fun onChannelDeath() {
            setState(RadarLinkState.DISCONNECTED)
        }

        override fun onReceiveMessage(messageType: MessageFromAntType, antParcel: AntMessageParcel) {
            when (messageType) {
                MessageFromAntType.BROADCAST_DATA -> {
                    val payload = BroadcastDataMessage(antParcel).payload
                    onPayload(payload)
                }
                MessageFromAntType.CHANNEL_EVENT -> {
                    val event = ChannelEventMessage(antParcel)
                    when (event.eventCode) {
                        EventCode.RX_SEARCH_TIMEOUT,
                        EventCode.CHANNEL_CLOSED,
                        -> setState(RadarLinkState.DISCONNECTED)
                        else -> Unit
                    }
                }
                else -> Unit
            }
        }
    }

    private fun onPayload(payload: ByteArray) {
        when (payload.firstOrNull()?.toInt()?.and(0xFF)) {
            AntPlusBikeRadarDecoder.PAGE_TARGETS_A -> pageA = payload.copyOf()
            AntPlusBikeRadarDecoder.PAGE_TARGETS_B -> pageB = payload.copyOf()
            else -> return
        }
        if (stateRef.get() != RadarLinkState.TRACKING) {
            setState(RadarLinkState.TRACKING)
            try {
                val idMsg = channel?.requestChannelId()
                val num = idMsg?.channelId?.deviceNumber
                if (num != null && num != 0) {
                    lockedDeviceNumber = num
                    onDeviceFound?.invoke(num)
                }
            } catch (_: Exception) {
                // optional lock
            }
        }
    }

    private fun releaseChannel() {
        try {
            channel?.clearChannelEventHandler()
            channel?.close()
        } catch (_: AntCommandFailedException) {
            // ignore
        } catch (_: RemoteException) {
            // ignore
        }
        try {
            channel?.release()
        } catch (_: Exception) {
            // ignore
        }
        channel = null
    }

    private fun unbind() {
        if (!bound) return
        try {
            app.unbindService(connection)
        } catch (_: Exception) {
            // ignore
        }
        bound = false
        provider = null
        antService = null
    }

    private fun setState(s: RadarLinkState) {
        stateRef.set(s)
        onStateChanged?.invoke(s)
    }

    companion object {
        private const val TAG = "AntPlusRadar"
        private const val ANT_RADIO_PACKAGE = "com.dsi.ant.service.socket"

        /**
         * ANT+ managed network key (thisisant adopter key). Required to hear
         * certified bike-radar sensors; do not use for non-ANT+ traffic.
         */
        private val ANT_PLUS_NETWORK_KEY = NetworkKey(
            byteArrayOf(
                0xB9.toByte(), 0xA5.toByte(), 0x21.toByte(), 0xF4.toByte(),
                0xBD.toByte(), 0x72.toByte(), 0xC3.toByte(), 0x45.toByte(),
            ),
        )
    }
}
