package org.cyclingcommons.scout

import android.app.Application
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.cyclingcommons.scout.domain.ScoutController
import org.cyclingcommons.scout.domain.ScoutUiState
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.domain.VehicleCounter
import org.cyclingcommons.scout.recording.RideFile
import org.cyclingcommons.scout.recording.RideFiles
import org.cyclingcommons.scout.recording.RideFitSession
import org.cyclingcommons.scout.recording.RideForegroundService
import org.cyclingcommons.scout.R
import org.cyclingcommons.scout.sensors.LocationSampler
import org.cyclingcommons.scout.sensors.radar.RadarCoordinator
import org.cyclingcommons.scout.sensors.radar.RadarDeviceRow
import org.cyclingcommons.scout.sensors.radar.RadarStatus
import org.cyclingcommons.scout.sensors.radar.RadarTransport
import org.cyclingcommons.scout.ui.theme.ThemeMode
import java.io.File
import kotlin.math.roundToInt

enum class Screen {
    INTRO,
    RIDE,
    SETTINGS,
    HELP,
    PAIR_RADAR,
}

data class RideUiModel(
    val screen: Screen = Screen.RIDE,
    val scout: ScoutUiState = ScoutUiState(),
    val elapsedSec: Long = 0L,
    val sampleCount: Long = 0L,
    val pendingTags: Int = 0,
    val hasLocationPermission: Boolean = false,
    /** Formatted last fix, or null while GPS has nothing yet. */
    val fixLabel: String? = null,
    val lastFitPath: String? = null,
    val radar: RadarStatus = RadarStatus(),
    val imperial: Boolean = false,
    val keepScreenOn: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val rides: List<RideFile> = emptyList(),
    /** Transient rider hint (e.g. tapped a tile before recording). */
    val userMessage: String? = null,
)

/**
 * Ride façade for the UI (TECHNICAL §4). Holds no radio logic of its own: the tagging
 * rules live in [ScoutController], the radar link in [RadarCoordinator], the file in
 * [RideFitSession].
 *
 * The tick loop is demand-driven — it samples at ~1 Hz while RUNNING, drops to 250 ms
 * only while a picker or lit tile is on screen, and suspends outright when the app is
 * idle so a backgrounded Scout costs nothing (SPEC §12.1).
 */
class RideViewModel(app: Application) : AndroidViewModel(app) {
    private val controller = ScoutController()
    private val vehicles = VehicleCounter()
    private val location = LocationSampler(app)
    private val radar = RadarCoordinator(app)
    private val appPrefs = AppPrefs(app)
    private val feedback = RideFeedback(app)

    private var fitSession: RideFitSession? = null

    private val _ui = MutableStateFlow(
        RideUiModel(screen = if (appPrefs.introSeen) Screen.RIDE else Screen.INTRO),
    )
    val ui: StateFlow<RideUiModel> = _ui.asStateFlow()

    /** Signals the tick loop to run again; conflated because one wake is enough. */
    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    private var uiVisible = false
    private var lastSampleAt = 0L
    private var sampleCount = 0L
    private var rideStartedAtMs = 0L
    private var elapsedBeforePauseMs = 0L

    init {
        radar.onStatusChanged = ::wake
        viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                tick(now)
                val delayMs = nextTickDelayMs(now)
                if (delayMs == null) {
                    wakeSignal.receive()
                } else {
                    withTimeoutOrNull(delayMs) { wakeSignal.receive() }
                }
            }
        }
        refreshPermissions()
    }

    private fun tick(nowMs: Long) {
        if (controller.onTick(nowMs)) {
            // Picker commit / timeout — same confirm/undo feedback as a tap.
            controller.takeFeedback()?.let { feedback.confirm(it.undone) }
        }
        if (controller.timer == TimerState.RUNNING && nowMs - lastSampleAt >= SAMPLE_INTERVAL_MS) {
            lastSampleAt = nowMs
            writeSample(nowMs)
            radar.onTick(nowMs)
        }
        publish(nowMs)
    }

    /** Null = nothing pending; park the loop until something wakes it. */
    private fun nextTickDelayMs(nowMs: Long): Long? {
        val animating = uiVisible && controller.needsTick(nowMs)
        if (controller.timer != TimerState.RUNNING) {
            return if (animating) ANIMATION_INTERVAL_MS else null
        }
        if (animating) return ANIMATION_INTERVAL_MS
        return (SAMPLE_INTERVAL_MS - (nowMs - lastSampleAt)).coerceIn(1L, SAMPLE_INTERVAL_MS)
    }

    private fun wake() {
        wakeSignal.trySend(Unit)
    }

    /** Driven by the activity lifecycle: while hidden, only recording keeps ticking. */
    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        if (visible) wake()
    }

    fun refreshPermissions() {
        radar.refreshCapabilities()
        _ui.update {
            it.copy(
                hasLocationPermission = location.hasPermission(),
                radar = radar.status.value,
                imperial = appPrefs.imperial,
                keepScreenOn = appPrefs.keepScreenOn,
                themeMode = appPrefs.themeMode,
            )
        }
    }

    fun dismissIntro() {
        appPrefs.introSeen = true
        show(Screen.RIDE)
    }

    fun replayIntro() {
        appPrefs.introSeen = false
        show(Screen.INTRO)
    }

    fun openSettings() {
        show(Screen.SETTINGS)
        refreshPermissions()
        loadRides()
    }

    fun closeSettings() = show(Screen.RIDE)

    fun openHelp(returnTo: Screen = Screen.SETTINGS) {
        helpReturnScreen = returnTo
        show(Screen.HELP)
    }

    fun closeHelp() = show(helpReturnScreen)

    private var helpReturnScreen = Screen.SETTINGS

    fun openPairRadar() {
        radar.openPairing()
        show(Screen.PAIR_RADAR)
    }

    fun closePairRadar() {
        radar.closePairing(rideIdle = controller.timer == TimerState.IDLE)
        show(Screen.SETTINGS)
        loadRides()
    }

    fun setImperial(value: Boolean) {
        appPrefs.imperial = value
        _ui.update { it.copy(imperial = value, scout = it.scout.copy(imperial = value)) }
    }

    fun setKeepScreenOn(value: Boolean) {
        appPrefs.keepScreenOn = value
        _ui.update { it.copy(keepScreenOn = value) }
    }

    fun setThemeMode(value: ThemeMode) {
        appPrefs.themeMode = value
        _ui.update { it.copy(themeMode = value) }
    }

    fun setTransport(transport: RadarTransport) = radar.setTransport(transport)

    fun startRadarScan() = radar.startScan()

    fun stopRadarScan() = radar.stopScan()

    fun startAntSearch() = radar.startAntSearch()

    fun selectRadar(row: RadarDeviceRow) = radar.select(row)

    fun forgetRadar() = radar.forget()

    fun startRide() {
        controller.start()
        vehicles.resetRide()
        lastSampleAt = 0L
        sampleCount = 0L
        rideStartedAtMs = System.currentTimeMillis()
        elapsedBeforePauseMs = 0L
        fitSession = RideFitSession(getApplication(), viewModelScope)
        if (location.hasPermission()) location.start()
        radar.onRideStart()
        RideForegroundService.sync(getApplication(), TimerState.RUNNING)
        publish()
        wake()
    }

    fun pauseRide() {
        controller.pause()
        elapsedBeforePauseMs += System.currentTimeMillis() - rideStartedAtMs
        location.stop()
        radar.onRideStop()
        fitSession?.flush()
        RideForegroundService.sync(getApplication(), TimerState.PAUSED)
        publish()
    }

    fun resumeRide() {
        controller.resume()
        rideStartedAtMs = System.currentTimeMillis()
        if (location.hasPermission()) location.start()
        radar.onRideStart()
        RideForegroundService.sync(getApplication(), TimerState.RUNNING)
        publish()
        wake()
    }

    fun stopRide() {
        controller.stop()
        vehicles.resetRide()
        location.stop()
        radar.onRideStop()
        elapsedBeforePauseMs = 0L
        sampleCount = 0L
        fitSession?.finish { saved ->
            _ui.update { it.copy(lastFitPath = saved?.absolutePath) }
            loadRides()
        }
        fitSession = null
        RideForegroundService.sync(getApplication(), TimerState.IDLE)
        _ui.update { it.copy(fixLabel = null) }
        publish()
    }

    fun onTileTap(index: Int) {
        if (controller.timer != TimerState.RUNNING) {
            val res = when (controller.timer) {
                TimerState.PAUSED -> R.string.ride_resume_to_tag
                else -> R.string.ride_start_to_tag
            }
            showUserMessage(getApplication<Application>().getString(res))
            return
        }
        val now = System.currentTimeMillis()
        controller.onTileTap(index, now)
        controller.takeFeedback()?.let { feedback.confirm(it.undone) }
        publish(now)
        wake()
    }

    fun clearUserMessage() {
        _ui.update { it.copy(userMessage = null) }
    }

    private fun showUserMessage(message: String) {
        _ui.update { it.copy(userMessage = message) }
    }

    fun endOpenSurface() {
        if (controller.timer != TimerState.RUNNING) {
            val res = when (controller.timer) {
                TimerState.PAUSED -> R.string.ride_resume_to_tag
                else -> R.string.ride_start_to_tag
            }
            showUserMessage(getApplication<Application>().getString(res))
            return
        }
        val now = System.currentTimeMillis()
        controller.endOpenSurface(now)
        controller.takeFeedback()?.let { feedback.confirm(it.undone) }
        publish(now)
        wake()
    }

    fun shareLastFit(): Intent? = _ui.value.lastFitPath?.let { shareFitPath(it) }

    fun shareRide(ride: RideFile): Intent? = shareFitPath(ride.file.absolutePath)

    fun deleteRide(ride: RideFile) {
        val path = ride.file.absolutePath
        viewModelScope.launch {
            withContext(Dispatchers.IO) { RideFiles.delete(ride) }
            _ui.update {
                it.copy(lastFitPath = it.lastFitPath?.takeIf { p -> p != path })
            }
            loadRides()
        }
    }

    private fun show(screen: Screen) {
        _ui.update { it.copy(screen = screen) }
    }

    private fun loadRides() {
        viewModelScope.launch {
            val rides = withContext(Dispatchers.IO) { RideFiles.list(getApplication()) }
            _ui.update { it.copy(rides = rides) }
        }
    }

    private fun writeSample(nowMs: Long) {
        val tag = controller.drainTag()
        val fix = location.latest
        val observation = radar.observation()
        val channels = observation.fitChannels()
        val riderKph = fix?.speedMps?.let { (it * KPH_PER_MPS).toInt() } ?: 0
        vehicles.onSample(
            tracking = observation.tracking,
            occupiedCount = observation.occupiedCount(),
            nearestClosingKph = observation.nearestClosingKph(),
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
        sampleCount++
    }

    private fun publish(nowMs: Long = System.currentTimeMillis()) {
        val timer = controller.timer
        val elapsedMs = when (timer) {
            TimerState.IDLE -> 0L
            TimerState.PAUSED -> elapsedBeforePauseMs
            TimerState.RUNNING -> elapsedBeforePauseMs + (nowMs - rideStartedAtMs)
        }
        _ui.update {
            it.copy(
                scout = withRadar(controller.snapshot(nowMs)),
                elapsedSec = elapsedMs / 1000L,
                sampleCount = sampleCount,
                pendingTags = controller.queueSize(),
                fixLabel = if (timer == TimerState.IDLE) it.fixLabel else fixLabel(),
                radar = radar.status.value,
            )
        }
    }

    private fun withRadar(base: ScoutUiState): ScoutUiState = base.copy(
        radarLive = radar.status.value.tracking,
        carCount = vehicles.carCount,
        lastCarSpeedKph = vehicles.lastCarSpeedKph,
        imperial = appPrefs.imperial,
    )

    /**
     * A rider cannot do anything with raw coordinates at 30 km/h; what they need to know
     * is whether the tag they just dropped will land in the right place.
     */
    private fun fixLabel(): String? {
        val fix = location.latest ?: return null
        val app = getApplication<Application>()
        val accuracy = fix.accuracyM ?: return app.getString(R.string.ride_gps_ready)
        return if (appPrefs.imperial) {
            app.getString(R.string.ride_gps_accuracy_ft, (accuracy * FEET_PER_METRE).roundToInt())
        } else {
            app.getString(R.string.ride_gps_accuracy_m, accuracy.roundToInt())
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

    override fun onCleared() {
        location.stop()
        radar.onRideStop()
        feedback.release()
        super.onCleared()
    }

    private companion object {
        const val SAMPLE_INTERVAL_MS = 1_000L
        const val ANIMATION_INTERVAL_MS = 250L
        const val KPH_PER_MPS = 3.6f
        const val FEET_PER_METRE = 3.28084f
    }
}
