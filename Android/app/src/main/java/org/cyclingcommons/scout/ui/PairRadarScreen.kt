package org.cyclingcommons.scout.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.cyclingcommons.scout.R
import org.cyclingcommons.scout.domain.RadarLinkState
import org.cyclingcommons.scout.sensors.radar.RadarDeviceRow
import org.cyclingcommons.scout.sensors.radar.RadarStatus
import org.cyclingcommons.scout.sensors.radar.RadarTransport
import org.cyclingcommons.scout.ui.components.ScoutButton
import org.cyclingcommons.scout.ui.components.ScoutPage
import org.cyclingcommons.scout.ui.components.ScoutSection
import org.cyclingcommons.scout.ui.theme.ScoutColors
import org.cyclingcommons.scout.ui.theme.ScoutSpacing

@Composable
fun PairRadarScreen(
    status: RadarStatus,
    onTransport: (RadarTransport) -> Unit,
    onStartBleScan: () -> Unit,
    onStopBleScan: () -> Unit,
    onStartAntSearch: () -> Unit,
    onSelect: (RadarDeviceRow) -> Unit,
    onForget: () -> Unit,
    onBack: () -> Unit,
) {
    val scanning = status.link == RadarLinkState.SCANNING
    ScoutPage(
        title = stringResource(R.string.pair_title),
        onBack = {
            onStopBleScan()
            onBack()
        },
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(
                    start = ScoutSpacing.lg,
                    end = ScoutSpacing.lg,
                    bottom = ScoutSpacing.xxl,
                ),
            verticalArrangement = Arrangement.spacedBy(ScoutSpacing.xl),
        ) {
            ScoutSection(
                title = stringResource(R.string.pair_transport),
                subtitle = statusHint(status),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.sm)) {
                    TransportChip(
                        label = stringResource(R.string.pair_transport_auto),
                        selected = status.transport == RadarTransport.AUTO,
                        onClick = { onTransport(RadarTransport.AUTO) },
                    )
                    TransportChip(
                        label = stringResource(R.string.pair_transport_ant),
                        selected = status.transport == RadarTransport.ANT_PLUS,
                        enabled = status.antAvailable,
                        onClick = { onTransport(RadarTransport.ANT_PLUS) },
                    )
                    TransportChip(
                        label = stringResource(R.string.pair_transport_ble),
                        selected = status.transport == RadarTransport.BLE,
                        onClick = { onTransport(RadarTransport.BLE) },
                    )
                }
                Text(
                    text = stringResource(
                        if (status.antAvailable) {
                            R.string.pair_ant_available
                        } else {
                            R.string.pair_ant_missing
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ScoutColors.TextSecondary,
                )
                if (status.savedAddress != null) {
                    HorizontalDivider(color = ScoutColors.Outline)
                    Text(
                        text = stringResource(
                            R.string.pair_saved,
                            status.savedName ?: stringResource(R.string.radar_label),
                            status.savedAddress,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ScoutColors.TextPrimary,
                    )
                    ScoutButton(
                        label = stringResource(R.string.pair_forget),
                        onClick = onForget,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            ScoutSection(
                title = stringResource(R.string.pair_devices),
                subtitle = stringResource(R.string.pair_exclusive_hint),
            ) {
                // No ANT hardware means no ANT button at all — a dead control reads as a bug.
                if (status.transport != RadarTransport.BLE && status.antAvailable) {
                    ScoutButton(
                        label = stringResource(
                            if (status.link == RadarLinkState.CONNECTING) {
                                R.string.pair_ant_searching
                            } else {
                                R.string.pair_ant_search
                            },
                        ),
                        onClick = onStartAntSearch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (status.transport != RadarTransport.ANT_PLUS) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ScoutSpacing.sm)) {
                        ScoutButton(
                            label = stringResource(
                                if (scanning) R.string.pair_ble_scanning else R.string.pair_ble_scan,
                            ),
                            onClick = onStartBleScan,
                            primary = !scanning,
                            enabled = status.bluetoothPermission && status.bluetoothOn,
                            modifier = Modifier.weight(1f),
                        )
                        if (scanning) {
                            ScoutButton(
                                label = stringResource(R.string.pair_ble_stop),
                                onClick = onStopBleScan,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                if (status.devices.isEmpty()) {
                    Text(
                        text = stringResource(R.string.pair_devices_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ScoutColors.TextSecondary,
                    )
                } else {
                    Column {
                        status.devices.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(color = ScoutColors.Outline)
                            DeviceRow(row = row, onClick = { onSelect(row) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun statusHint(status: RadarStatus): String = when {
    !status.bluetoothPermission && status.transport != RadarTransport.ANT_PLUS ->
        stringResource(R.string.pair_need_bluetooth_permission)
    !status.bluetoothOn && status.transport == RadarTransport.BLE ->
        stringResource(R.string.pair_need_bluetooth_on)
    !status.antAvailable && status.transport == RadarTransport.ANT_PLUS ->
        stringResource(R.string.pair_need_ant_service)
    status.savedAddress != null &&
        (status.link == RadarLinkState.DISCONNECTED || status.link == RadarLinkState.ABSENT) ->
        stringResource(R.string.pair_saved_ready)
    else -> stringResource(
        when (status.link) {
            RadarLinkState.ABSENT -> R.string.pair_state_absent
            RadarLinkState.SCANNING -> R.string.pair_state_scanning
            RadarLinkState.CONNECTING -> R.string.pair_state_connecting
            RadarLinkState.TRACKING -> R.string.pair_state_tracking
            RadarLinkState.DISCONNECTED -> R.string.pair_state_disconnected
        },
    )
}

@Composable
private fun DeviceRow(row: RadarDeviceRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = ScoutSpacing.md),
        verticalArrangement = Arrangement.spacedBy(ScoutSpacing.xs),
    ) {
        Text(
            text = row.name?.takeIf { it.isNotBlank() } ?: stringResource(
                if (row.likelyRadar) R.string.pair_possible_radar else R.string.pair_unknown_device,
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = if (row.likelyRadar) ScoutColors.Brand else ScoutColors.TextPrimary,
        )
        Text(
            text = "${row.address}  ·  " + if (row.rssi > RadarDeviceRow.RSSI_UNKNOWN) {
                stringResource(R.string.pair_signal_dbm, row.rssi)
            } else {
                stringResource(R.string.pair_signal_paired)
            },
            style = MaterialTheme.typography.bodySmall,
            color = ScoutColors.TextSecondary,
        )
    }
}

@Composable
private fun TransportChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = ScoutColors.Brand,
            selectedLabelColor = ScoutColors.TextOnBrand,
            labelColor = ScoutColors.TextSecondary,
        ),
    )
}
