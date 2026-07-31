package org.cyclingcommons.scout.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.cyclingcommons.scout.R
import org.cyclingcommons.scout.RideUiModel
import org.cyclingcommons.scout.domain.TimerState
import org.cyclingcommons.scout.domain.UiMode
import kotlin.math.ceil

private val ScreenBg = Color(0xFF0E0E0E)
private val CardBg = Color(0xFF161616)
private val CardShape = RoundedCornerShape(12.dp)
private val Accent = Color(0xFFE30613)
private val Muted = Color(0xFF9A9A9A)
private val BtnShape = RoundedCornerShape(8.dp)

/** [sampleCount] is ~1 Hz while RUNNING — format as ride clock. */
private fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@Composable
fun ScoutRideScreen(
    model: RideUiModel,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onTileTap: (Int) -> Unit,
    onEndOpenSurface: () -> Unit = {},
    onShareFit: () -> Unit = {},
    onSettings: () -> Unit = {},
) {
    val scout = model.scout
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RideChrome(
            timer = scout.timer,
            sampleCount = model.sampleCount,
            hasLocationPermission = model.hasLocationPermission,
            lastFixLabel = model.lastFixLabel,
            lastFitPath = model.lastFitPath,
            bondedRadar = model.bondedRadarName ?: model.bondedRadarAddress,
            onStart = onStart,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
            onShareFit = onShareFit,
            onSettings = onSettings,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(CardShape)
                .background(CardBg),
        ) {
            TagGrid(
                mode = scout.mode,
                tiles = scout.tiles,
                counts = scout.tileCounts,
                flashIdx = scout.flashIdx,
                flashUntilMs = scout.flashUntilMs,
                pendingIdx = scout.pendingIdx,
                pendingUntilMs = scout.pendingUntilMs,
                title = scout.title,
                openSurfaceLabel = scout.openSurfaceLabel,
                onTileTap = onTileTap,
                mod = Modifier.fillMaxSize(),
            )
            RecordingDot(
                running = scout.timer == TimerState.RUNNING,
                mod = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
            )
        }
        val openLabel = scout.openSurfaceLabel
        if (openLabel != null) {
            OpenSurfaceBanner(label = openLabel, onEnd = onEndOpenSurface)
        }
        RadarStrip(
            live = scout.radarLive,
            bonded = model.bondedRadarAddress != null,
            recording = scout.timer == TimerState.RUNNING,
            seeking = model.radarSeeking,
            carCount = scout.carCount,
            speedKph = scout.lastCarSpeedKph,
            imperial = scout.imperial,
        )
    }
}

@Composable
private fun OpenSurfaceBanner(label: String, onEnd: () -> Unit) {
    var visible by remember(label) { mutableStateOf(true) }
    LaunchedEffect(label) {
        while (true) {
            delay(15_000)
            repeat(2) {
                visible = false
                delay(140)
                visible = true
                delay(140)
            }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .alpha(if (visible) 1f else 0f)
            .background(Color(0xFFE30613))
            .clickable(onClick = onEnd)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "surface open: $label  ·  tap here to END",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RideChrome(
    timer: TimerState,
    sampleCount: Long,
    hasLocationPermission: Boolean,
    lastFixLabel: String,
    lastFitPath: String?,
    bondedRadar: String?,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onShareFit: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg, CardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.scout_logo_white),
                contentDescription = "Scout",
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Fit,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (timer) {
                        TimerState.IDLE -> "idle"
                        TimerState.RUNNING -> "recording"
                        TimerState.PAUSED -> "paused"
                    },
                    color = if (timer == TimerState.RUNNING) Accent else Muted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Text(
                    text = when {
                        !hasLocationPermission -> "location permission needed"
                        timer != TimerState.IDLE -> formatElapsed(sampleCount)
                        lastFitPath != null -> lastFitPath.substringAfterLast('/')
                        else -> lastFixLabel
                    },
                    color = Color(0xFFCCCCCC),
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
            when (timer) {
                TimerState.IDLE -> {
                    ChromeBtn("Settings", accent = false, onClick = onSettings)
                    ChromeBtn("Start", accent = true, onClick = onStart)
                }
                TimerState.RUNNING -> {
                    ChromeBtn("Pause", accent = false, onClick = onPause)
                    ChromeBtn("Stop", accent = true, onClick = onStop)
                }
                TimerState.PAUSED -> {
                    ChromeBtn("Resume", accent = true, onClick = onResume)
                    ChromeBtn("Stop", accent = false, onClick = onStop)
                }
            }
        }
        if (timer == TimerState.IDLE) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (bondedRadar != null) "radar: $bondedRadar" else "radar: none",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                if (lastFitPath != null) {
                    ChromeBtn("Share FIT", accent = false, onClick = onShareFit)
                }
            }
        }
    }
}

@Composable
private fun ChromeBtn(label: String, accent: Boolean, onClick: () -> Unit) {
    val colors =
        if (accent) {
            ButtonDefaults.buttonColors(
                containerColor = Accent,
                contentColor = Color.White,
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2A2A2A),
                contentColor = Color.White,
            )
        }
    Button(
        onClick = onClick,
        colors = colors,
        shape = BtnShape,
        contentPadding = ButtonDefaults.TextButtonContentPadding,
        modifier = Modifier.height(40.dp),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecordingDot(running: Boolean, mod: Modifier = Modifier) {
    Box(
        modifier = mod
            .size(12.dp)
            .background(
                // Green = recording; red = idle/paused (not writing samples).
                color = if (running) Color(0xFF2E8B57) else Color(0xFFE30613),
                shape = CircleShape,
            ),
    )
}

@Composable
private fun RadarStrip(
    live: Boolean,
    bonded: Boolean,
    recording: Boolean,
    seeking: Boolean,
    carCount: Int,
    speedKph: Int,
    imperial: Boolean,
) {
    val text =
        when {
            live -> buildString {
                append("$carCount cars")
                if (speedKph >= 0) {
                    if (imperial) {
                        val mph = (speedKph * 0.621371).toInt()
                        append("   $mph ±3 mph")
                    } else {
                        append("   $speedKph ±5 kph")
                    }
                }
            }
            bonded && !recording -> "radar ready · Start to connect"
            bonded && recording && seeking -> "connecting radar…"
            else -> "no radar"
        }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(CardShape)
            .background(CardBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (live) Color.White else Color(0xFFB0B0B0),
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
private fun TagGrid(
    mode: UiMode,
    tiles: List<org.cyclingcommons.scout.domain.Tile>,
    counts: List<Int>,
    flashIdx: Int,
    flashUntilMs: Long,
    pendingIdx: Int,
    pendingUntilMs: Long,
    title: String?,
    openSurfaceLabel: String?,
    onTileTap: (Int) -> Unit,
    mod: Modifier = Modifier,
) {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val needsTick =
        (flashIdx >= 0 && flashUntilMs > nowMs) ||
            (pendingIdx >= 0 && pendingUntilMs > nowMs)
    LaunchedEffect(needsTick, flashUntilMs, pendingUntilMs) {
        while (needsTick) {
            nowMs = System.currentTimeMillis()
            if (nowMs >= flashUntilMs && (pendingUntilMs == 0L || nowMs >= pendingUntilMs)) {
                break
            }
            delay(100)
        }
        nowMs = System.currentTimeMillis()
    }
    val cols = 2
    val rows = ceil(tiles.size / 2.0).toInt().coerceAtLeast(1)
    Column(modifier = mod.padding(6.dp)) {
        if (title != null) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
        repeat(rows) { row ->
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                repeat(cols) { col ->
                    val i = row * cols + col
                    if (i < tiles.size) {
                        val tile = tiles[i]
                        val count = counts.getOrElse(i) { 0 }
                        val lit = i == flashIdx || (mode != UiMode.GRID && i == pendingIdx)
                        val surfaceActive =
                            mode == UiMode.GRID &&
                                tile.code == org.cyclingcommons.scout.domain.PoiType.SURFACE &&
                                openSurfaceLabel != null
                        val untilMs =
                            when {
                                i == flashIdx && flashUntilMs > nowMs -> flashUntilMs
                                mode != UiMode.GRID &&
                                    i == pendingIdx &&
                                    pendingUntilMs > nowMs -> pendingUntilMs
                                else -> 0L
                            }
                        val countdownSec =
                            if (untilMs > 0L) {
                                ((untilMs - nowMs + 999L) / 1000L).toInt().coerceAtLeast(1)
                            } else {
                                0
                            }
                        TagTile(
                            label = if (surfaceActive) "SURFACE · $openSurfaceLabel" else tile.label,
                            count = count,
                            countdownSec = countdownSec,
                            rgb = tile.rgb,
                            filled = lit || surfaceActive,
                            onClick = { onTileTap(i) },
                            mod = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(4.dp),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TagTile(
    label: String,
    count: Int,
    countdownSec: Int,
    rgb: Int,
    filled: Boolean,
    onClick: () -> Unit,
    mod: Modifier = Modifier,
) {
    val color = Color((0xFF000000.toInt()) or (rgb and 0xFFFFFF))
    val appearance =
        if (filled) {
            Modifier.background(color, RoundedCornerShape(10.dp))
        } else {
            Modifier.border(width = 3.dp, color = color, shape = RoundedCornerShape(10.dp))
        }
    val fg = Color.White
    Box(
        modifier = mod.then(appearance).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (countdownSec > 0) {
                Text(
                    text = "${countdownSec}s",
                    color = Color(0xFFFFF3B0),
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = label,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
            if (count > 0) {
                Text(
                    text = count.toString(),
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
