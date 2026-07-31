package org.cyclingcommons.scout

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.ui.IntroScreen
import org.cyclingcommons.scout.ui.PairRadarScreen
import org.cyclingcommons.scout.ui.ScoutRideScreen
import org.cyclingcommons.scout.ui.SettingsScreen
import org.cyclingcommons.scout.ui.theme.ScoutTheme

class MainActivity : ComponentActivity() {
    private val rideVm: RideViewModel by viewModels()

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            rideVm.refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNeededPermissions()
        setContent {
            ScoutTheme {
                val model by rideVm.ui.collectAsStateWithLifecycle()
                LaunchedEffect(model.keepScreenOn, model.scout.timer) {
                    val hold =
                        model.keepScreenOn && model.scout.timer == TimerState.RUNNING
                    if (hold) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0E0E0E))
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    when {
                        model.showIntro -> {
                            IntroScreen(onContinue = rideVm::dismissIntro)
                        }
                        model.showPairRadar -> {
                            PairRadarScreen(
                                state = model.radarState,
                                bluetoothOk = model.bluetoothOk,
                                permissionOk = model.bluetoothPermissionOk,
                                antAvailable = model.antAvailable,
                                transport = model.transport,
                                bondedName = model.bondedRadarName,
                                bondedAddress = model.bondedRadarAddress,
                                devices = model.radarDevices,
                                onTransport = rideVm::setTransport,
                                onStartBleScan = {
                                    requestNeededPermissions()
                                    rideVm.startRadarScan()
                                },
                                onStopBleScan = rideVm::stopRadarScan,
                                onStartAntSearch = rideVm::startAntSearch,
                                onSelect = rideVm::selectRadar,
                                onForget = rideVm::forgetRadar,
                                onBack = rideVm::closePairRadar,
                            )
                        }
                        model.showSettings -> {
                            val radarLabel =
                                model.bondedRadarName ?: model.bondedRadarAddress ?: "none"
                            SettingsScreen(
                                imperial = model.imperial,
                                keepScreenOn = model.keepScreenOn,
                                radarLabel = "Preferred: $radarLabel · ${model.transport}",
                                rides = model.rides,
                                onImperial = rideVm::setImperial,
                                onKeepScreenOn = rideVm::setKeepScreenOn,
                                onPairRadar = {
                                    requestNeededPermissions()
                                    rideVm.openPairRadar()
                                },
                                onShareRide = { ride ->
                                    rideVm.shareRide(ride)?.let {
                                        startActivity(Intent.createChooser(it, "Share FIT"))
                                    }
                                },
                                onDeleteRide = rideVm::deleteRide,
                                onBack = rideVm::closeSettings,
                            )
                        }
                        else -> {
                            ScoutRideScreen(
                                model = model,
                                onStart = {
                                    requestNeededPermissions()
                                    rideVm.startRide()
                                },
                                onPause = rideVm::pauseRide,
                                onResume = {
                                    requestNeededPermissions()
                                    rideVm.resumeRide()
                                },
                                onStop = rideVm::stopRide,
                                onTileTap = rideVm::onTileTap,
                                onEndOpenSurface = rideVm::endOpenSurface,
                                onShareFit = {
                                    rideVm.shareLastFit()?.let {
                                        startActivity(Intent.createChooser(it, "Share FIT"))
                                    }
                                },
                                onSettings = rideVm::openSettings,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rideVm.refreshPermissions()
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (!granted(Manifest.permission.POST_NOTIFICATIONS)) {
                needed += Manifest.permission.POST_NOTIFICATIONS
            }
        }
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            needed += Manifest.permission.ACCESS_FINE_LOCATION
            needed += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 31) {
            if (!granted(Manifest.permission.BLUETOOTH_SCAN)) {
                needed += Manifest.permission.BLUETOOTH_SCAN
            }
            if (!granted(Manifest.permission.BLUETOOTH_CONNECT)) {
                needed += Manifest.permission.BLUETOOTH_CONNECT
            }
        }
        if (needed.isNotEmpty()) {
            permissions.launch(needed.toTypedArray())
        } else {
            rideVm.refreshPermissions()
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
