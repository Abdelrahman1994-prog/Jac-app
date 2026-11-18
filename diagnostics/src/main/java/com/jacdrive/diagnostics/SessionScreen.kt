package com.jacdrive.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.diagnostics.data.DiagnosticSession
import com.jacdrive.diagnostics.data.SessionKind
import com.jacdrive.diagnostics.data.SessionMapper
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import com.jacdrive.ui.components.StatusPill
import com.jacdrive.ui.theme.JacTheme

@Composable
fun SessionScreen(id: Long, onBack: () -> Unit, onOpenCode: (String) -> Unit, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session = state.sessions.firstOrNull { it.id == id }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val arabic = isArabic()
    if (session == null) {
        ScreenHeader(stringResource(R.string.session_title), onBack = onBack, modifier = Modifier.padding(horizontal = 16.dp))
        return
    }
    // Previous scan (older) for comparison.
    val previous = remember(state.sessions, id) {
        state.sessions.dropWhile { it.id != id }.drop(1).firstOrNull { it.kind == SessionKind.SCAN }
    }

    val exporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/html"),
    ) { uri -> uri?.let { viewModel.exportReport(session, previous, arabic, it) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                formatDate(session.timestampMs),
                subtitle = stringResource(if (session.kind == SessionKind.CLEAR) R.string.session_kind_clear else R.string.session_kind_scan),
                onBack = onBack,
                actions = {
                    if (session.kind == SessionKind.SCAN) {
                        IconButton(onClick = { exporter.launch("jacdrive-report-${session.id}.html") }) {
                            Icon(androidx.compose.material.icons.Icons.Rounded.IosShare, stringResource(R.string.session_export))
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.session_delete)) }
                },
            )
        }
        state.message?.takeIf { it == DiagMessage.ReportSaved }?.let { item { com.jacdrive.ui.components.InfoNote(stringResource(R.string.msg_report_saved), color = JacTheme.status.ok) } }
        item { SessionFacts(session) }
        if (session.kind == SessionKind.SCAN) {
            listOf(
                R.string.diag_stored to session.stored,
                R.string.diag_pending to session.pending,
                R.string.diag_permanent to session.permanent,
            ).forEach { (title, codes) ->
                item {
                    JacCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            when {
                                codes == null -> StatusPill(stringResource(R.string.diag_not_supported), MaterialTheme.colorScheme.onSurfaceVariant)
                                codes.isEmpty() -> StatusPill(stringResource(R.string.diag_no_codes), JacTheme.status.ok)
                                else -> StatusPill(codes.size.toString(), JacTheme.status.critical)
                            }
                        }
                        codes.orEmpty().forEachIndexed { i, c ->
                            DtcRow(viewModel.info(c)) { onOpenCode(c) }
                            if (i < codes!!.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            session.readiness?.takeIf { it.isNotEmpty() }?.let { r ->
                item { SectionHeader(stringResource(R.string.diag_readiness)) }
                item { JacCard { ReadinessGrid(r) } }
            }
            item { SectionHeader(stringResource(R.string.session_compare)) }
            item {
                JacCard {
                    if (previous == null) {
                        Text(stringResource(R.string.session_no_previous), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val cmp = SessionMapper.compare(session, previous)
                        Text(stringResource(R.string.session_compared_with, formatDate(previous.timestampMs)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        CompareRow(stringResource(R.string.session_new), cmp.newCodes, JacTheme.status.critical, onOpenCode)
                        CompareRow(stringResource(R.string.session_resolved), cmp.resolved, JacTheme.status.ok, onOpenCode)
                        CompareRow(stringResource(R.string.session_persisting), cmp.persisting, JacTheme.status.warning, onOpenCode)
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.session_delete)) },
            text = { Text(stringResource(R.string.session_delete_body)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(id); onBack() }) { Text(stringResource(R.string.session_delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SessionFacts(s: DiagnosticSession) {
    JacCard {
        Fact(stringResource(R.string.diag_mil), when (s.milOn) { true -> stringResource(R.string.diag_mil_on); false -> stringResource(R.string.diag_mil_off); null -> "—" })
        Fact(stringResource(R.string.session_protocol), s.protocol ?: "—")
        Fact(stringResource(R.string.session_adapter), s.adapterName ?: "—")
        Fact(stringResource(R.string.session_mileage), s.mileageKm?.let { "$it km" } ?: "—")
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun CompareRow(label: String, codes: List<String>, color: androidx.compose.ui.graphics.Color, onOpenCode: (String) -> Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = color)
        if (codes.isEmpty()) Text("—", style = MaterialTheme.typography.bodyMedium)
        else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            codes.take(6).forEach { c ->
                TextButton(onClick = { onOpenCode(c) }) { Text(c, color = color) }
            }
        }
    }
}
