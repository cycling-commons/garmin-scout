package org.cyclingcommons.scout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.cyclingcommons.scout.domain.RadarLinkState
import org.cyclingcommons.scout.sensors.radar.RadarTransport

data class RadarDeviceRow(
    val address: String,
    val name: String?,
    /** Higher = closer. Bonded-only seeds use [RSSI_UNKNOWN]. */
    val rssi: Int = RSSI_UNKNOWN,
    /** Name or advertised service UUID looks like a bike radar. */
    val likelyRadar: Boolean = false,
) {
    companion object {
        const val RSSI_UNKNOWN = -999
    }
}

@Composable
fun PairRadarScreen(
    state: RadarLinkState,
    bluetoothOk: Boolean,
    permissionOk: Boolean,
    antAvailable: Boolean,
    transport: RadarTransport,
    bondedName: String?,
    bondedAddress: String?,
    devices: List<RadarDeviceRow>,
    onTransport: (RadarTransport) -> Unit,
    onStartBleScan: () -> Unit,
    onStopBleScan: () -> Unit,
    onStartAntSearch: () -> Unit,
    onSelect: (RadarDeviceRow) -> Unit,
    onForget: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Pair radar", style = MaterialTheme.typography.titleLarge, color = Color.White)
            TextButton(onClick = {
                onStopBleScan()
                onBack()
            }) {
                Text("Done")
            }
        }
        Text(
            text = "ANT+ hardware: " + if (antAvailable) "available" else "not found",
            color = Color(0xFFCCCCCC),
        )
        Text("Transport preference", color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportChip("Auto", transport == RadarTransport.AUTO) {
                onTransport(RadarTransport.AUTO)
            }
            TransportChip("ANT+", transport == RadarTransport.ANT_PLUS, enabled = antAvailable) {
                onTransport(RadarTransport.ANT_PLUS)
            }
            TransportChip("BLE", transport == RadarTransport.BLE) {
                onTransport(RadarTransport.BLE)
            }
        }
        Text(
            text = when {
                !permissionOk && transport != RadarTransport.ANT_PLUS ->
                    "Bluetooth permission needed for BLE"
                !bluetoothOk && transport == RadarTransport.BLE -> "Turn on Bluetooth"
                !antAvailable && transport == RadarTransport.ANT_PLUS ->
                    "Install ANT Radio Service / use a USB ANT stick"
                bondedAddress != null &&
                    (state == RadarLinkState.DISCONNECTED || state == RadarLinkState.ABSENT) ->
                    "Saved — connects when you Start recording"
                else -> "State: $state"
            },
            color = Color(0xFFCCCCCC),
        )
        if (bondedAddress != null) {
            Text(
                text = "Saved: ${bondedName ?: "radar"} ($bondedAddress)",
                color = Color.White,
            )
            Button(onClick = onForget) { Text("Forget") }
        }
        if (transport != RadarTransport.BLE) {
            Button(
                onClick = onStartAntSearch,
                enabled = antAvailable,
            ) {
                Text(
                    if (state == RadarLinkState.CONNECTING || state == RadarLinkState.TRACKING) {
                        "ANT+ searching / tracking…"
                    } else {
                        "Search ANT+ radar"
                    },
                )
            }
        }
        if (transport != RadarTransport.ANT_PLUS) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartBleScan,
                    enabled = permissionOk && bluetoothOk,
                ) {
                    Text(if (state == RadarLinkState.SCANNING) "Scanning BLE…" else "Scan BLE")
                }
                if (state == RadarLinkState.SCANNING) {
                    Button(onClick = onStopBleScan) { Text("Stop") }
                }
            }
        }
        Text(
            "Disconnect the radar from its brand app (e.g. Magene Utility) before pairing here.",
            color = Color(0xFFCCCCCC),
            fontSize = 16.sp,
        )
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(devices, key = { it.address }) { row ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(row) }
                        .padding(vertical = 12.dp),
                ) {
                    val title =
                        row.name?.takeIf { it.isNotBlank() }
                            ?: if (row.likelyRadar) "Possible radar" else "Nearby device"
                    Text(
                        text = title,
                        color = if (row.likelyRadar) Color(0xFFE8C9A0) else Color.White,
                        fontSize = 16.sp,
                    )
                    val signal =
                        if (row.rssi > RadarDeviceRow.RSSI_UNKNOWN) "${row.rssi} dBm" else "paired"
                    Text(
                        text = "${row.address}  ·  $signal",
                        color = Color(0xFFAAAAAA),
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransportChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
    )
}
