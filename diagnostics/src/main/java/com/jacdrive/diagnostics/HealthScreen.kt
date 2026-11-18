package com.jacdrive.diagnostics

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.ui.cockpit.CockpitStyles
import com.jacdrive.ui.components.DataModeBanner
import com.jacdrive.ui.components.InfoNote
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import com.jacdrive.ui.components.StatusPill
import com.jacdrive.ui.components.format
import com.jacdrive.ui.components.labelRes
import com.jacdrive.ui.components.unitLabel
import com.jacdrive.ui.gauge.HorizonDial
import com.jacdrive.ui.theme.JacTheme
import com.jacdrive.ui.theme.Readout
import kotlinx.coroutines.delay

@Composable
fun HealthScreen(onBack: () -> Unit, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) { value = System.currentTimeMillis(); delay(1_000) }
    }
    val items = HealthEvaluator.evaluate(state.snapshot, now)
    val score = viewModel.healthScore(state, now)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.health_title), onBack = onBack) }
        item { DataModeBanner(state.snapshot.mode) }
        item {
            JacCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val color = score?.let { scoreColor(it.score) } ?: MaterialTheme.colorScheme.onSurfaceVariant
                    HorizonDial(
                        fraction = score?.score?.div(100f),
                        color = color,
                        value = score?.score?.toString() ?: "—",
                        unit = "/ 100",
                        style = CockpitStyles.Horizon.copy(text = MaterialTheme.colorScheme.onSurface, track = MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(140.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.health_score), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (score == null) stringResource(R.string.health_score_needs)
                            else stringResource(R.string.health_based_on, formatDate(score.scanTimestampMs)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (score != null) {
                    Spacer(Modifier.height(12.dp))
                    if (score.deductions.isEmpty()) {
                        Text(stringResource(R.string.health_no_deductions), style = MaterialTheme.typography.bodyMedium, color = JacTheme.status.ok)
                    } else score.deductions.forEach { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(deductionLabel(d.reason), style = MaterialTheme.typography.bodyMedium)
                                d.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text("−${d.points}", style = Readout.Small, color = JacTheme.status.critical)
                        }
                    }
                    if (!score.liveIncluded) {
                        Text(stringResource(R.string.health_live_excluded), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.health_live_checks)) }
        item {
            JacCard {
                items.forEachIndexed { i, item ->
                    HealthRow(item, state)
                    if (i < items.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        item { SectionHeader(stringResource(R.string.health_method)) }
        item { InfoNote(stringResource(R.string.health_method_body)) }
        item { InfoNote(stringResource(R.string.health_scope_body), color = JacTheme.status.warning) }
    }
}

@Composable
private fun HealthRow(item: HealthItem, state: DiagnosticsUiState) {
    val s = JacTheme.status
    val (label, color) = when (item.status) {
        HealthStatus.NORMAL -> stringResource(R.string.health_normal) to s.ok
        HealthStatus.ATTENTION -> stringResource(R.string.health_attention) to s.warning
        HealthStatus.CRITICAL -> stringResource(R.string.health_critical) to s.critical
        HealthStatus.WARMING_UP -> stringResource(R.string.health_warming) to s.info
        HealthStatus.UNKNOWN -> stringResource(R.string.health_unknown) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(item.metric.labelRes()), style = MaterialTheme.typography.bodyLarge)
            Text(
                item.value?.let { "${item.metric.format(it, state.units)} ${item.metric.unitLabel(state.units)}" } ?: "—",
                style = Readout.Small,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        StatusPill(label, color)
    }
}
