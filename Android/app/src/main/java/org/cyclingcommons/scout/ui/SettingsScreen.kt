package org.cyclingcommons.scout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cyclingcommons.scout.recording.RideFile
import java.text.DateFormat
import java.util.Date

private val Muted = Color(0xFFCCCCCC)

@Composable
fun SettingsScreen(
    imperial: Boolean,
    keepScreenOn: Boolean,
    radarLabel: String,
    rides: List<RideFile>,
    onImperial: (Boolean) -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onPairRadar: () -> Unit,
    onShareRide: (RideFile) -> Unit,
    onDeleteRide: (RideFile) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val versionName = remember(context) {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (_: Exception) {
            null
        } ?: "?"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = Color.White)
            TextButton(onClick = onBack) { Text("Done") }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsBlock(title = "Display") {
                Text("Speed units", color = Muted, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !imperial,
                        onClick = { onImperial(false) },
                        label = { Text("km/h") },
                    )
                    FilterChip(
                        selected = imperial,
                        onClick = { onImperial(true) },
                        label = { Text("mph") },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Keep screen on while recording", color = Color.White, fontSize = 16.sp)
                        Text(
                            "Default off — saves battery. Notification still opens Scout.",
                            color = Muted,
                            fontSize = 15.sp,
                        )
                    }
                    Switch(checked = keepScreenOn, onCheckedChange = onKeepScreenOn)
                }
            }

            SettingsBlock(
                title = "Radar",
                subtitle = "Radar uses more power. Prefer ANT+ when the phone has it.",
            ) {
                Text(radarLabel, color = Color.White, fontSize = 16.sp)
                Button(onClick = onPairRadar) { Text("Pair / change radar") }
            }

            SettingsBlock(
                title = "Rides (FIT)",
                subtitle = "Kept on this phone until you delete them — share anytime.",
            ) {
                if (rides.isEmpty()) {
                    Text("No rides yet", color = Muted, fontSize = 16.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        rides.forEach { ride ->
                            RideRow(
                                ride = ride,
                                onShare = { onShareRide(ride) },
                                onDelete = { onDeleteRide(ride) },
                            )
                        }
                    }
                }
            }

            Text(
                "Version: v$versionName",
                color = Color.White,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun SettingsBlock(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, color = Muted, fontSize = 15.sp)
            }
        }
        content()
    }
}

@Composable
private fun RideRow(
    ride: RideFile,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val whenText = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(ride.modifiedMs))
    val kb = (ride.sizeBytes / 1024.0).let { "%.1f KB".format(it) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                ride.name,
                color = Color.White,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$whenText · $kb",
                color = Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onShare) { Text("Share") }
        TextButton(onClick = onDelete) {
            Text("Delete", color = Color(0xFFEF9A9A))
        }
    }
}
