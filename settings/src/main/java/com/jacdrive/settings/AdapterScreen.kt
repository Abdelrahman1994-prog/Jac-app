package com.jacdrive.settings

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.obd.bluetooth.AdapterDevice
import com.jacdrive.obd.bluetooth.BluetoothPermissions
import com.jacdrive.obd.elm.ObdFailure
import com.jacdrive.obd.pid.ObdPid
import com.jacdrive.obd.session.InitStep
import com.jacdrive.obd.session.PidStatus
import com.jacdrive.ui.components.EmptyState
import com.jacdrive.ui.components.InfoNote
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import com.jacdrive.ui.components.StatusPill
import com.jacdrive.ui.components.format
import com.jacdrive.ui.components.labelRes
import com.jacdrive.ui.components.unitLabel
import com.jacdrive.ui.theme.JacTheme
import com.jacdrive.ui.theme.Readout
import java.util.Locale

@Composable
fun AdapterScreen(onBack: () -> Unit, viewModel: AdapterViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refresh() }
    val enableLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refresh() }
    val env = state.env
    val link = state.link
    val connected = link.connection is ConnectionState.Connected

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.adapter_title), subtitle = stringResource(R.string.adapter_subtitle), onBack = onBack) }
        item { StatusCard(state, viewModel::disconnect, viewModel::connectSaved) }

        when {
            !env.supported -> item {
                JacCard { EmptyState(Icons.Rounded.BluetoothDisabled, stringResource(R.string.adapter_no_bt), stringResource(R.string.adapter_no_bt_sub)) }
            }
            !env.permissionsGranted -> item {
                JacCard {
                    EmptyState(Icons.Rounded.Lock, stringResource(R.string.adapter_perm_title), stringResource(R.string.adapter_perm_body)) {
                        Button(onClick = { permissionLauncher.launch(BluetoothPermissions.required + BluetoothPermissions.notification) }) {
                            Text(stringResource(R.string.adapter_perm_grant))
                        }
                    }
                }
            }
            !env.enabled -> item {
                JacCard {
                    EmptyState(Icons.Rounded.BluetoothDisabled, stringResource(R.string.adapter_bt_off), stringResource(R.string.adapter_bt_off_sub)) {
                        Button(onClick = {
                            runCatching { enableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                        }) { Text(stringResource(R.string.adapter_bt_turn_on)) }
                    }
                }
            }
            else -> {
                if (connected) {
                    item { SectionHeader(stringResource(R.string.adapter_info)) }
                    item { AdapterInfoCard(state) }
                    item { SectionHeader(stringResource(R.string.adapter_live_pids)) }
                    item { LivePidsCard(state) }
                }
                item { SectionHeader(stringResource(R.string.adapter_paired)) }
                if (env.paired.isEmpty()) {
                    item { Text(stringResource(R.string.adapter_paired_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    item {
                        JacCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                            env.paired.forEachIndexed { i, d ->
                                DeviceRow(d, isCurrent = link.active && link.device?.address == d.address, actionLabel = stringResource(R.string.adapter_connect)) {
                                    viewModel.connect(d)
                                }
                                if (i < env.paired.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
                item {
                    SectionHeader(stringResource(R.string.adapter_nearby)) {
                        if (env.scanning) {
                            TextButton(onClick = viewModel::stopScan) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.adapter_stop))
                            }
                        } else {
                            TextButton(onClick = viewModel::scan) {
                                Icon(Icons.Rounded.BluetoothSearching, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.adapter_scan))
                            }
                        }
                    }
                }
                if (env.discovered.isNotEmpty()) {
                    item {
                        JacCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                            env.discovered.forEachIndexed { i, d ->
                                DeviceRow(d, isCurrent = false, actionLabel = stringResource(R.string.adapter_pair)) { viewModel.pair(d) }
                                if (i < env.discovered.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.adapter_options)) }
        item {
            JacCard {
                SwitchRow(stringResource(R.string.adapter_auto), state.autoReconnect, viewModel::setAutoReconnect, subtitle = stringResource(R.string.adapter_auto_sub))
                if (state.savedAddress != null) {
                    RowDivider()
                    SettingRow(
                        stringResource(R.string.adapter_forget),
                        subtitle = state.savedName ?: state.savedAddress,
                        onClick = { viewModel.forget() },
                    )
                }
            }
        }
        item { InfoNote(stringResource(R.string.adapter_tips)) }
    }
}

@Composable
private fun StatusCard(state: AdapterUiState, onDisconnect: () -> Unit, onConnectSaved: () -> Unit) {
    val s = JacTheme.status
    val link = state.link
    val (icon, color, title, subtitle) = when (val c = link.connection) {
        is ConnectionState.Connected -> Quad(Icons.Rounded.BluetoothConnected, s.ok, stringResource(R.string.adapter_state_connected, c.adapterName), c.protocol ?: "")
        is ConnectionState.Connecting -> Quad(Icons.Rounded.Bluetooth, s.info, stringResource(R.string.adapter_state_connecting), stepText(link.step))
        is ConnectionState.Failed -> Quad(
            Icons.Rounded.ErrorOutline,
            if (link.active) s.warning else s.critical,
            if (link.active) stringResource(R.string.adapter_state_retrying, link.reconnectAttempt) else stringResource(R.string.adapter_state_failed),
            failureText(link.lastFailure),
        )
        ConnectionState.Disconnected -> Quad(
            Icons.Rounded.BluetoothDisabled, MaterialTheme.colorScheme.onSurfaceVariant,
            stringResource(R.string.adapter_state_disconnected),
            state.savedName?.let { stringResource(R.string.adapter_saved, it) } ?: stringResource(R.string.adapter_state_disconnected_sub),
        )
    }
    JacCard(highlight = if (link.connection is ConnectionState.Connected) color else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                if (link.connection is ConnectionState.Connecting) CircularProgressIndicator(Modifier.size(28.dp), color = color, strokeWidth = 3.dp)
                else Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            link.active -> OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.adapter_disconnect)) }
            state.savedAddress != null && state.env.permissionsGranted && state.env.enabled ->
                Button(onClick = onConnectSaved, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Link, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.adapter_connect_saved, state.savedName ?: state.savedAddress))
                }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun stepText(step: InitStep?): String = stringResource(
    when (step) {
        null -> R.string.step_bluetooth
        InitStep.RESET -> R.string.step_reset
        InitStep.CONFIGURE -> R.string.step_configure
        InitStep.SEARCH_PROTOCOL -> R.string.step_search
        InitStep.READ_INFO -> R.string.step_info
        InitStep.DISCOVER_PIDS -> R.string.step_pids
    },
)

@Composable
private fun failureText(f: ObdFailure?): String = stringResource(
    when (f) {
        ObdFailure.BLUETOOTH_UNAVAILABLE -> R.string.fail_no_bt
        ObdFailure.BLUETOOTH_OFF -> R.string.fail_bt_off
        ObdFailure.PERMISSION_MISSING -> R.string.fail_permission
        ObdFailure.SOCKET_FAILED -> R.string.fail_socket
        ObdFailure.ADAPTER_NOT_RESPONDING -> R.string.fail_adapter
        ObdFailure.ECU_NOT_RESPONDING -> R.string.fail_ecu
        ObdFailure.LINK_LOST, null -> R.string.fail_link
        ObdFailure.UNSUPPORTED_TRANSPORT -> R.string.fail_transport
    },
)

@Composable
private fun DeviceRow(device: AdapterDevice, isCurrent: Boolean, actionLabel: String, onClick: () -> Unit) {
    SettingRow(
        title = device.name ?: stringResource(R.string.adapter_unnamed),
        subtitle = device.address,
        icon = Icons.Rounded.Bluetooth,
        onClick = onClick,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (device.looksLikeObd) StatusPill("OBD", MaterialTheme.colorScheme.primary)
            if (isCurrent) StatusPill(stringResource(R.string.adapter_current), JacTheme.status.ok)
            else Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AdapterInfoCard(state: AdapterUiState) {
    val link = state.link
    val a = link.adapter
    val stats = link.stats
    val dash = "—"
    JacCard {
        InfoRow(stringResource(R.string.info_device), link.device?.name ?: dash)
        InfoRow(stringResource(R.string.info_mac), link.device?.address ?: dash)
        InfoRow(stringResource(R.string.info_elm), a?.elmVersion ?: dash)
        InfoRow(stringResource(R.string.info_protocol), a?.protocolName ?: dash)
        InfoRow(stringResource(R.string.info_voltage), a?.adapterVoltage?.let { "%.1f V".format(Locale.US, it) } ?: dash)
        InfoRow(stringResource(R.string.info_latency), stats?.let { "%.0f ms".format(Locale.US, it.avgLatencyMs) } ?: dash)
        InfoRow(stringResource(R.string.info_success), stats?.let { "%.0f %%".format(Locale.US, it.successRate * 100) } ?: dash)
        InfoRow(stringResource(R.string.info_pids), link.supportedPids.count { it % 0x20 != 0 }.toString(), last = true)
    }
}

@Composable
private fun InfoRow(label: String, value: String, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
    if (!last) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun LivePidsCard(state: AdapterUiState) {
    val link = state.link
    val pidStatus = link.stats?.pidStatus.orEmpty()
    val s = JacTheme.status
    JacCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
        ObdPid.entries.forEachIndexed { i, pid ->
            val status = pidStatus[pid] ?: if (pid.code in link.supportedPids) PidStatus.ACTIVE else PidStatus.UNSUPPORTED
            val metric = pid.metric
            val value = metric?.let { link.snapshot?.get(it) }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("01%02X".format(pid.code), style = Readout.Caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
                Text(
                    metric?.let { stringResource(it.labelRes()) } ?: stringResource(if (pid == ObdPid.MAF) R.string.pid_maf else R.string.pid_fuel_rate),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                when {
                    status == PidStatus.UNSUPPORTED -> Text(stringResource(R.string.pid_unsupported), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    status == PidStatus.NOT_RESPONDING -> StatusPill(stringResource(R.string.pid_not_responding), s.warning)
                    metric != null && value != null -> Text("${metric.format(value.value, state.units)} ${metric.unitLabel(state.units)}", style = Readout.Small)
                    else -> StatusPill(stringResource(R.string.pid_supported), s.ok)
                }
            }
            if (i < ObdPid.entries.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}
