package org.cyclingcommons.scout

import android.app.Application
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.cyclingcommons.scout.domain.RadarLinkState
import org.cyclingcommons.scout.domain.ScoutController
import org.cyclingcommons.scout.domain.ScoutUiState
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.domain.VehicleCounter
import org.cyclingcommons.scout.recording.RideFile
import org.cyclingcommons.scout.recording.RideFiles
import org.cyclingcommons.scout.recording.RideFitSession
import org.cyclingcommons.scout.recording.RideForegroundService
import org.cyclingcommons.scout.sensors.LocationSampler
import org.cyclingcommons.scout.sensors.radar.CompositeRadarSession
import org.cyclingcommons.scout.sensors.radar.RadarPrefs
import org.cyclingcommons.scout.sensors.radar.RadarTransport
import org.cyclingcommons.scout.ui.RadarDeviceRow
import java.io.File

data class RideUiModel(
    val scout: ScoutUiState = ScoutUiState(),
    val sampleCount: Long = 0L,
    val pendingTags: Int = 0,
    val hasLocationPermission: Boolean = false,
    val lastFitPath: String? = null,
    val lastFixLabel: String = "no fix",
    val showPairRadar: Boolean = false,
    val showSettings: Boolean = false,
    val showIntro: Boolean = true,
    val radarState: RadarLinkState = RadarLinkState.ABSENT,
    val radarDevices: List<RadarDeviceRow> = emptyList(),
    val bondedRadarName: String? = null,
    val bondedRadarAddress: String? = null,
    val bluetoothOk: Boolean = false,
    val bluetoothPermissionOk: Boolean = false,
    val antAvailable: Boolean = false,
    val transport: RadarTransport = RadarTransport.AUTO,
    val antDeviceNumber: Int? = null,
    val imperial: Boolean = false,
    val keepScreenOn: Boolean = false,
    val rides: List<RideFile> = emptyList(),
)

class RideViewModel(app: Application) : AndroidViewModel(app) {
    private val controller = ScoutController()
    private val vehicles = VehicleCounter()
    private val location = LocationSampler(app)
    private val radar = CompositeRadarSession(app)
    private val radarPrefs = RadarPrefs(app)
    private val appPrefs = AppPrefs(app)

    private var fitSession: RideFitSession? = null
    private val foundDevices = LinkedHashMap<String, RadarDeviceRow>()

    private val _ui = MutableStateFlow(RideUiModel())
    val ui: StateFlow<RideUiModel> = _ui.asStateFlow()

    private var lastSampleAt = 0L
    private var lastRadarRetryAt = 0L

    init {
        radar.onBleDeviceFound = { row ->
            val remembered = radarPrefs.rememberedName(row.address)
            val withName = if (row.name.isNullOrBlank() && remembered != null) {
                row.copy(name = remembered, likelyRadar = true)
            } else {
                row
            }
            val prev = foundDevices[withName.address]
            val merged =
                if (prev == null) {
                    withName
                } else {
                    withName.copy(
                        name = withName.name?.takeIf { it.isNotBlank() } ?: prev.name,
                        rssi = maxOf(withName.rssi, prev.rssi),
                        likelyRadar = withName.likelyRadar || prev.likelyRadar,
                    )
                }
            foundDevices[merged.address] = merged
            publishDeviceList()
        }
        radar.ble.onNameResolved = { address, name ->
            radarPrefs.rememberName(address, name)
            if (radarPrefs.address.equals(address, ignoreCase = true)) {
                radarPrefs.name = name
            }
            val prev = foundDevices[address]
            foundDevices[address] = (prev ?: RadarDeviceRow(address, name)).copy(
                name = name,
                likelyRadar = true,
            )
            _ui.update {
                it.copy(
                    bondedRadarName = if (it.bondedRadarAddress.equals(address, true)) {
                        name
                    } else {
                        it.bondedRadarName
                    },
                )
            }
            publishDeviceList()
        }
        radar.onAntDeviceFound = { num ->
            _ui.update {
                it.copy(
                    antDeviceNumber = num,
                    bondedRadarName = "ANT+ radar #$num",
                    bondedRadarAddress = "ANT+$num",
                )
            }
        }
        radar.onStateChanged = { st ->
            _ui.update {
                it.copy(
                    radarState = st,
                    scout = mergeRadar(it.scout),
                )
            }
        }
        viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                if (controller.onTick(now)) {
                    // Picker commit / timeout — same confirm/undo feedback as a tap.
                    controller.takeFeedback()?.let { confirmTap(it.undone) }
                }
                if (controller.timer == TimerState.RUNNING && now - lastSampleAt >= 1000L) {
                    lastSampleAt = now
                    writeSample(now)
                    // Magene/BLE can drop — retry reconnect while recording.
                    val st = radar.state()
                    if ((st == RadarLinkState.DISCONNECTED || st == RadarLinkState.ABSENT) &&
                        radarPrefs.address != null &&
                        now - lastRadarRetryAt >= 5_000L
                    ) {
                        lastRadarRetryAt = now
                        radar.connectForRide()
                    }
                }
                publish(now)
                delay(250)
            }
        }
        refreshPermissions()
    }

    private fun publishDeviceList() {
        _ui.update {
            it.copy(
                radarDevices = foundDevices.values.sortedWith(
                    compareByDescending<RadarDeviceRow> { d -> d.likelyRadar }
                        .thenByDescending { d -> d.rssi }
                        .thenBy { d -> d.name ?: "~" },
                ),
            )
        }
    }

    fun refreshPermissions() {
        _ui.update {
            it.copy(
                hasLocationPermission = location.hasPermission(),
                bluetoothPermissionOk = radar.ble.hasBluetoothPermission(),
                bluetoothOk = radar.ble.bluetoothEnabled(),
                antAvailable = radar.antAvailable(),
                transport = radarPrefs.transport,
                bondedRadarName = radarPrefs.name,
                bondedRadarAddress = radarPrefs.address
                    ?: radarPrefs.antDeviceNumber?.let { "ANT+$it" },
                antDeviceNumber = radarPrefs.antDeviceNumber,
                radarState = radar.state(),
                lastFixLabel = fixLabel(),
                imperial = appPrefs.imperial,
                keepScreenOn = appPrefs.keepScreenOn,
                rides = RideFiles.list(getApplication()),
            )
        }
    }

    fun dismissIntro() {
        _ui.update { it.copy(showIntro = false) }
    }

    fun openSettings() {
        _ui.update {
            it.copy(
                showSettings = true,
                showPairRadar = false,
                rides = RideFiles.list(getApplication()),
                imperial = appPrefs.imperial,
                keepScreenOn = appPrefs.keepScreenOn,
            )
        }
        refreshPermissions()
    }

    fun closeSettings() {
        _ui.update { it.copy(showSettings = false) }
    }

    fun setImperial(value: Boolean) {
        appPrefs.imperial = value
        _ui.update { it.copy(imperial = value, scout = it.scout.copy(imperial = value)) }
    }

    fun setKeepScreenOn(value: Boolean) {
        appPrefs.keepScreenOn = value
        _ui.update { it.copy(keepScreenOn = value) }
    }

    fun openPairRadar() {
        foundDevices.clear()
        _ui.update {
            it.copy(
                showPairRadar = true,
                showSettings = false,
                radarDevices = emptyList(),
            )
        }
        refreshPermissions()
    }

    fun closePairRadar() {
        radar.stopBleScan()
        if (controller.timer == TimerState.IDLE) {
            radar.disconnect()
        }
        _ui.update {
            it.copy(
                showPairRadar = false,
                showSettings = true, // return to settings after pair
                rides = RideFiles.list(getApplication()),
            )
        }
    }

    fun setTransport(t: RadarTransport) {
        radar.setTransportPreference(t)
        refreshPermissions()
    }

    fun startRadarScan() {
        foundDevices.clear()
        _ui.update { it.copy(radarDevices = emptyList()) }
        radar.startBleScan()
        refreshPermissions()
    }

    fun stopRadarScan() {
        radar.stopBleScan()
        refreshPermissions()
    }

    fun startAntSearch() {
        radar.startAntSearch()
        refreshPermissions()
    }

    fun selectRadar(row: RadarDeviceRow) {
        radar.selectBle(row)
        refreshPermissions()
    }

    fun forgetRadar() {
        radar.forget()
        _ui.update {
            it.copy(
                bondedRadarAddress = null,
                bondedRadarName = null,
                antDeviceNumber = null,
                radarState = RadarLinkState.ABSENT,
            )
        }
    }

    fun startRide() {
        val app = getApplication<Application>()
        controller.start()
        vehicles.resetRide()
        lastSampleAt = 0L
        fitSession = RideFitSession(app)
        if (location.hasPermission()) {
            location.start()
        }
        radar.connectForRide()
        RideForegroundService.sync(app, TimerState.RUNNING)
        publish()
    }

    fun pauseRide() {
        controller.pause()
        location.stop()
        radar.disconnect()
        fitSession?.flush()
        RideForegroundService.sync(getApplication(), TimerState.PAUSED)
        publish()
    }

    fun resumeRide() {
        controller.resume()
        if (location.hasPermission()) {
            location.start()
        }
        radar.connectForRide()
        RideForegroundService.sync(getApplication(), TimerState.RUNNING)
        publish()
    }

    fun stopRide() {
        controller.stop()
        vehicles.resetRide()
        location.stop()
        radar.disconnect()
        val finished = fitSession?.takeIf { it.recordCount > 0 }?.finish()
        fitSession = null
        RideForegroundService.sync(getApplication(), TimerState.IDLE)
        _ui.update {
            it.copy(
                scout = mergeRadar(controller.snapshot()),
                sampleCount = 0,
                pendingTags = 0,
                hasLocationPermission = location.hasPermission(),
                lastFitPath = finished?.absolutePath,
                lastFixLabel = "no fix",
                radarState = radar.state(),
                bondedRadarName = radarPrefs.name,
                bondedRadarAddress = radarPrefs.address
                    ?: radarPrefs.antDeviceNumber?.let { n -> "ANT+$n" },
                antDeviceNumber = radarPrefs.antDeviceNumber,
                antAvailable = radar.antAvailable(),
                transport = radarPrefs.transport,
                imperial = appPrefs.imperial,
                keepScreenOn = appPrefs.keepScreenOn,
                rides = RideFiles.list(getApplication()),
            )
        }
    }

    fun shareLastFit(): Intent? =
        _ui.value.lastFitPath?.let { shareFitPath(it) }

    fun shareRide(ride: RideFile): Intent? = shareFitPath(ride.file.absolutePath)

    fun deleteRide(ride: RideFile) {
        val path = ride.file.absolutePath
        RideFiles.delete(ride)
        _ui.update {
            it.copy(
                rides = RideFiles.list(getApplication()),
                lastFitPath = if (it.lastFitPath == path) null else it.lastFitPath,
            )
        }
    }

    private fun shareFitPath(path: String): Intent? {
        val file = File(path)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(
            getApplication(),
            "${getApplication<Application>().packageName}.fileprovider",
            file,
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            putExtra(Intent.EXTRA_TITLE, file.name)
        }
    }

    fun onTileTap(index: Int) {
        val now = System.currentTimeMillis()
        controller.onTileTap(index, now)
        controller.takeFeedback()?.let { confirmTap(it.undone) }
        publish(now)
    }

    fun endOpenSurface() {
        val now = System.currentTimeMillis()
        controller.endOpenSurface(now)
        controller.takeFeedback()?.let { confirmTap(it.undone) }
        publish(now)
    }

    private fun writeSample(nowMs: Long) {
        val tag = controller.drainTag()
        val fix = location.latest
        val obs = radar.observation()
        val channels = obs.fitChannels()
        val riderKph =
            if (fix != null && fix.hasSpeed && fix.speedMps != null) {
                (fix.speedMps * 3.6f).toInt()
            } else {
                0
            }
        vehicles.onSample(
            tracking = obs.tracking,
            occupiedCount = obs.occupiedCount(),
            nearestClosingKph = obs.nearestClosingKph(),
            riderKph = riderKph,
        )
        fitSession?.appendSample(
            nowMs = nowMs,
            fix = fix,
            poiType = tag?.type ?: 0,
            poiDetail = tag?.detail ?: 0,
            radarCount = channels[0],
            radarNear = channels[1],
            radarSpeed = channels[2],
        )
        _ui.update {
            it.copy(
                sampleCount = it.sampleCount + 1,
                lastFixLabel = fixLabel(),
            )
        }
    }

    private fun fixLabel(): String {
        val f = location.latest ?: return "no fix"
        return "%.5f, %.5f".format(f.latitude, f.longitude)
    }

    private fun publish(nowMs: Long = System.currentTimeMillis()) {
        _ui.update {
            it.copy(
                scout = mergeRadar(controller.snapshot(nowMs)),
                pendingTags = controller.queueSize(),
                hasLocationPermission = location.hasPermission(),
                bluetoothPermissionOk = radar.ble.hasBluetoothPermission(),
                bluetoothOk = radar.ble.bluetoothEnabled(),
                antAvailable = radar.antAvailable(),
                transport = radarPrefs.transport,
                radarState = radar.state(),
                bondedRadarName = radarPrefs.name,
                bondedRadarAddress = radarPrefs.address
                    ?: radarPrefs.antDeviceNumber?.let { n -> "ANT+$n" },
                antDeviceNumber = radarPrefs.antDeviceNumber,
                lastFixLabel = if (controller.timer == TimerState.IDLE) {
                    it.lastFixLabel
                } else {
                    fixLabel()
                },
                imperial = appPrefs.imperial,
                keepScreenOn = appPrefs.keepScreenOn,
            )
        }
    }

    private fun mergeRadar(base: ScoutUiState): ScoutUiState {
        val tracking = radar.state() == RadarLinkState.TRACKING
        return base.copy(
            radarLive = tracking,
            carCount = vehicles.carCount,
            lastCarSpeedKph = vehicles.lastCarSpeedKph,
            imperial = appPrefs.imperial,
        )
    }

    /**
     * Eyes-on-road confirm: haptic when available, plus two distinct tones
     * (Garmin TONE_KEY vs TONE_RESET). Failure must not block tagging.
     */
    private fun confirmTap(undone: Boolean) {
        try {
            vibrate(undone)
        } catch (_: Exception) {
        }
        try {
            playTone(undone)
        } catch (_: Exception) {
        }
    }

    private fun vibrate(undone: Boolean) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getApplication<Application>()
                .getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getApplication<Application>().getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect =
                if (undone) {
                    VibrationEffect.createWaveform(longArrayOf(0, 90, 60, 90), -1)
                } else {
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                }
            vibrator.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            if (undone) vibrator.vibrate(longArrayOf(0, 90, 60, 90), -1)
            else vibrator.vibrate(120)
        }
    }

    private fun playTone(undone: Boolean) {
        // STREAM_MUSIC so it is audible over a bar-mount phone speaker/BT.
        val tone =
            if (undone) ToneGenerator.TONE_CDMA_CONFIRM // lower / reset-like
            else ToneGenerator.TONE_PROP_BEEP // short key click
        val durationMs = if (undone) 220 else 120
        val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
        tg.startTone(tone, durationMs)
        viewModelScope.launch {
            delay(durationMs.toLong() + 40L)
            tg.release()
        }
    }

    override fun onCleared() {
        location.stop()
        radar.disconnect()
        super.onCleared()
    }
}
