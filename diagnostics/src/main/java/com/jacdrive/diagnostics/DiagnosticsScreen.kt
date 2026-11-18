package com.jacdrive.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.diagnostics.data.DiagnosticSession
import com.jacdrive.diagnostics.data.SessionKind
import com.jacdrive.obd.dtc.DtcCodec
import com.jacdrive.obd.dtc.DtcType
import com.jacdrive.ui.components.DataModeBanner
import com.jacdrive.ui.components.EmptyState
import com.jacdrive.ui.components.InfoNote
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import com.jacdrive.ui.components.StatusPill
import com.jacdrive.ui.theme.JacTheme
import com.jacdrive.ui.theme.Readout

@Composable
fun DiagnosticsScreen(
    onBack: (() -> Unit)?,
    onOpenHealth: () -> Unit,
    onOpenCode: (String) -> Unit,
    onOpenSession: (Long) -> Unit,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showClear by rememberSaveable { mutableStateOf(false) }
    val latest = state.latestScan
    val score = viewModel.healthScore(state, System.currentTimeMillis())

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.diag_title), subtitle = stringResource(R.string.diag_subtitle), onBack = onBack) }
        item { DataModeBanner(state.snapshot.mode) }
        item { MilCard(latest) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = viewModel::scan, enabled = state.live && state.busy == null, modifier = Modifier.weight(1f).height(48.dp)) {
                    if (state.busy == Busy.SCANNING) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.Rounded.Radar, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (state.busy == Busy.SCANNING) R.string.diag_scanning else R.string.diag_scan))
                }
                OutlinedButton(
                    onClick = { showClear = true },
                    enabled = state.live && state.busy == null && latest != null && (latest.allCodes.isNotEmpty() || latest.milOn == true),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.height(48.dp),
                ) {
                    if (state.busy == Busy.CLEARING) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.diag_clear))
                }
            }
        }
        if (!state.live) {
            item { Text(stringResource(R.string.diag_scan_requires_adapter), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        state.message?.let { msg -> item { MessageCard(msg, viewModel::dismissMessage) } }

        if (latest != null) {
            if (!latest.complete) {
                item { InfoNote(stringResource(R.string.diag_incomplete_scan), color = JacTheme.status.warning) }
            }
            item { SectionHeader(stringResource(R.string.diag_codes_from, formatDate(latest.timestampMs))) }
            item { CodeGroup(stringResource(R.string.diag_stored), stringResource(R.string.diag_stored_sub), latest.stored, DtcType.STORED in latest.failedReads, false, viewModel, onOpenCode) }
            item { CodeGroup(stringResource(R.string.diag_pending), stringResource(R.string.diag_pending_sub), latest.pending, DtcType.PENDING in latest.failedReads, false, viewModel, onOpenCode) }
            item { CodeGroup(stringResource(R.string.diag_permanent), stringResource(R.string.diag_permanent_sub), latest.permanent.orEmpty(), DtcType.PERMANENT in latest.failedReads, latest.permanent == null, viewModel, onOpenCode) }
            latest.readiness?.takeIf { it.isNotEmpty() }?.let { r ->
                item { SectionHeader(stringResource(R.string.diag_readiness)) }
                item {
                    JacCard {
                        ReadinessGrid(r)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.diag_readiness_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.diag_health)) }
        item {
            JacCard(onClick = onOpenHealth) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MonitorHeart, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.health_title), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.diag_health_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (score != null) Text("${score.score}", style = Readout.Medium, color = scoreColor(score.score))
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item { SectionHeader(stringResource(R.string.diag_lookup)) }
        item { CodeLookup(onOpenCode) }

        item { SectionHeader(stringResource(R.string.diag_history)) }
        if (state.sessions.isEmpty()) {
            item { JacCard { EmptyState(Icons.Rounded.History, stringResource(R.string.diag_history_empty), stringResource(R.string.diag_history_empty_sub)) } }
        } else {
            items(state.sessions.take(20), key = { it.id }) { s -> SessionRow(s) { onOpenSession(s.id) } }
        }
        item { InfoNote(stringResource(R.string.diag_scope_note)) }
    }

    if (showClear) {
        ClearDialog(
            onDismiss = { showClear = false },
            onConfirm = {
                showClear = false
                viewModel.clearConfirmed()
            },
        )
    }
}

@Composable
fun scoreColor(score: Int): Color = when {
    score >= 85 -> JacTheme.status.ok
    score >= 60 -> JacTheme.status.warning
    else -> JacTheme.status.critical
}

@Composable
private fun MilCard(latest: DiagnosticSession?) {
    val s = JacTheme.status
    val (color, title) = when (latest?.milOn) {
        true -> s.critical to stringResource(R.string.diag_mil_on)
        false -> s.ok to stringResource(R.string.diag_mil_off)
        null -> MaterialTheme.colorScheme.onSurfaceVariant to stringResource(R.string.diag_not_read)
    }
    JacCard(highlight = if (latest?.milOn == true) color else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(if (latest?.milOn == false) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber, null, tint = color, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.diag_mil), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(title, style = MaterialTheme.typography.titleLarge, color = if (latest == null) MaterialTheme.colorScheme.onSurface else color)
                Text(
                    if (latest == null) stringResource(R.string.diag_mil_hint)
                    else stringResource(R.string.diag_last_scan, formatDate(latest.timestampMs), latest.reportedDtcCount ?: latest.stored.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CodeGroup(
    title: String, subtitle: String, codes: List<String>, failed: Boolean, unsupported: Boolean,
    viewModel: DiagnosticsViewModel, onOpenCode: (String) -> Unit,
) {
    JacCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                failed -> StatusPill(stringResource(R.string.diag_read_failed), JacTheme.status.warning)
                unsupported -> StatusPill(stringResource(R.string.diag_not_supported), MaterialTheme.colorScheme.onSurfaceVariant)
                codes.isEmpty() -> StatusPill(stringResource(R.string.diag_no_codes), JacTheme.status.ok)
                else -> StatusPill(codes.size.toString(), JacTheme.status.critical)
            }
        }
        if (codes.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            codes.forEachIndexed { i, c ->
                DtcRow(viewModel.info(c)) { onOpenCode(c) }
                if (i < codes.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun MessageCard(msg: DiagMessage, onDismiss: () -> Unit) {
    val s = JacTheme.status
    val (color, text) = when (msg) {
        DiagMessage.ScanSaved -> s.ok to stringResource(R.string.msg_scan_saved)
        DiagMessage.ReportSaved -> s.ok to stringResource(R.string.msg_report_saved)
        DiagMessage.Cleared -> s.ok to stringResource(R.string.msg_cleared)
        DiagMessage.NotConnected -> s.warning to stringResource(R.string.msg_not_connected)
        DiagMessage.EngineRunning -> s.warning to stringResource(R.string.msg_engine_running)
        is DiagMessage.Rejected -> s.warning to stringResource(R.string.msg_rejected, msg.reason)
        is DiagMessage.Error -> s.critical to stringResource(R.string.msg_error, msg.detail)
    }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(color.copy(alpha = 0.12f)).padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
        IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null) }
    }
}

@Composable
private fun CodeLookup(onOpenCode: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val valid = DtcCodec.isValid(text)
    val submit = { if (valid) onOpenCode(text.uppercase()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(5) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("P0301") },
        label = { Text(stringResource(R.string.diag_lookup_hint)) },
        trailingIcon = { IconButton(onClick = submit, enabled = valid) { Icon(Icons.Rounded.Search, null) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { submit() }),
    )
}

@Composable
private fun SessionRow(s: DiagnosticSession, onClick: () -> Unit) {
    val st = JacTheme.status
    JacCard(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(formatDate(s.timestampMs), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (s.kind == SessionKind.CLEAR) stringResource(R.string.session_clear)
                    else stringResource(R.string.session_summary, s.stored.size, s.pending.size),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                s.kind == SessionKind.CLEAR -> StatusPill(stringResource(R.string.session_kind_clear), MaterialTheme.colorScheme.onSurfaceVariant)
                s.milOn == true -> StatusPill("MIL", st.critical)
                s.allCodes.isEmpty() -> StatusPill(stringResource(R.string.diag_no_codes), st.ok)
                else -> StatusPill(s.allCodes.size.toString(), st.warning)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Mode 04 confirmation: the user must acknowledge the consequences explicitly. */
@Composable
private fun ClearDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    var understood by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.clear_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.clear_warn_repair, R.string.clear_warn_return, R.string.clear_warn_readiness, R.string.clear_warn_freeze, R.string.clear_warn_engine).forEach {
                    Row { Text("•  "); Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                }
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { understood = !understood },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = understood, onCheckedChange = { understood = it })
                    Text(stringResource(R.string.clear_understand), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm, enabled = understood,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.clear_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
