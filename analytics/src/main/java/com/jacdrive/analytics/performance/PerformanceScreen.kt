package com.jacdrive.analytics.performance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.analytics.R
import com.jacdrive.core.database.PerformanceRunEntity
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.UnitConverter
import com.jacdrive.core.util.Num
import com.jacdrive.ui.chart.RollingLineChart
import com.jacdrive.ui.components.DataModeBanner
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.MetricReadout
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import com.jacdrive.ui.theme.JacTheme
import com.jacdrive.ui.theme.Readout
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PerformanceScreen(onBack: (() -> Unit)?, viewModel: PerformanceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val runs by viewModel.runs.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(100) } }
    val critical = JacTheme.status.critical
    val live = state.snapshot.mode == DataMode.LIVE
    val u = state.speedUnit
    val spd = if (u == SpeedUnit.KMH) "km/h" else "mph"

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.perf_title), onBack = onBack) }
        item {
            JacCard(highlight = critical) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.GppMaybe, null, tint = critical, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.perf_warning_title), style = MaterialTheme.typography.titleMedium, color = critical)
                        Text(stringResource(R.string.perf_warning_body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { viewModel.acknowledge(!state.acknowledged) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(state.acknowledged, viewModel::acknowledge)
                    Text(stringResource(R.string.perf_ack), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { DataModeBanner(state.snapshot.mode) }

        item { SectionHeader(stringResource(R.string.perf_timers)) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                state.timers.forEach { t -> TimerCard(t, now, Modifier.weight(1f)) { viewModel.save(t.type) } }
            }
        }
        item {
            if (state.armed) {
                OutlinedButton(onClick = viewModel::disarm, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(stringResource(R.string.perf_disarm)) }
            } else {
                Button(onClick = viewModel::arm, enabled = live && state.acknowledged, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.Timer, null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.perf_arm))
                }
            }
            if (!live) Text(stringResource(R.string.perf_needs_live), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item { SectionHeader(stringResource(R.string.perf_gforce_title)) }
        item { GForceCard(state) }

        item { SectionHeader(stringResource(R.string.perf_graphs)) }
        item {
            JacCard {
                val c = MaterialTheme.colorScheme.primary
                RollingLineChart(stringResource(R.string.perf_rpm), state.rpm, 0.0, 8000.0, now, c, state.rpm.lastOrNull()?.value?.let(Num::int) ?: "—")
                Spacer(Modifier.height(12.dp))
                RollingLineChart(stringResource(R.string.perf_throttle), state.throttle, 0.0, 100.0, now, c, state.throttle.lastOrNull()?.value?.let { "${Num.int(it)} %" } ?: "—")
                Spacer(Modifier.height(12.dp))
                RollingLineChart(stringResource(R.string.perf_load), state.load, 0.0, 100.0, now, c, state.load.lastOrNull()?.value?.let { "${Num.int(it)} %" } ?: "—")
                Text(stringResource(R.string.perf_graphs_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            SectionHeader(stringResource(R.string.perf_session)) {
                TextButton(onClick = viewModel::resetSession) { Text(stringResource(R.string.perf_reset)) }
            }
        }
        item {
            JacCard {
                Row {
                    MetricReadout(stringResource(R.string.perf_max_speed), state.maxSpeedKmh?.let { Num.int(UnitConverter.speed(it, u)) } ?: "—", spd, Modifier.weight(1f))
                    MetricReadout(stringResource(R.string.perf_avg_speed), state.avgSpeedKmh?.let { Num.int(UnitConverter.speed(it, u)) } ?: "—", spd, Modifier.weight(1f))
                    MetricReadout(stringResource(R.string.perf_gforce), if (state.peakG > 0) Num.two(state.peakG) else "—", "g", Modifier.weight(1f))
                }
            }
        }

        item { SectionHeader(stringResource(R.string.perf_saved)) }
        if (runs.isEmpty()) {
            item { Text(stringResource(R.string.perf_saved_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(runs.take(50), key = { it.id }) { r -> RunRow(r, best = runs.filter { it.type == r.type }.minOf { it.timeS } == r.timeS) { viewModel.deleteRun(r.id) } }
        }
        item { Text(stringResource(R.string.perf_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun label(type: RunType) = when (type) {
    RunType.ZERO_60 -> "0–60"
    RunType.ZERO_100 -> "0–100"
    RunType.SIXTY_120 -> "60–120"
}

@Composable
private fun TimerCard(t: TimerUi, now: Long, modifier: Modifier, onSave: () -> Unit) {
    val s = JacTheme.status
    JacCard(modifier, contentPadding = PaddingValues(12.dp), highlight = if (t.state == TimerState.FINISHED) s.ok else null) {
        Text(label(t.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val value = when {
            t.resultS != null -> String.format(Locale.US, "%.2f", t.resultS)
            t.runningSinceMs != null -> String.format(Locale.US, "%.1f", (now - t.runningSinceMs) / 1000.0)
            else -> "—.—"
        }
        Text(value, style = Readout.Medium, color = if (t.resultS != null) s.ok else MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(
                when (t.state) {
                    TimerState.WAITING_FOR_STOP -> R.string.timer_stop_first
                    TimerState.READY -> R.string.timer_ready
                    TimerState.RUNNING -> R.string.timer_running
                    TimerState.FINISHED -> R.string.timer_done
                    TimerState.ABORTED -> R.string.timer_aborted
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (t.state == TimerState.ABORTED) s.warning else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (t.state == TimerState.FINISHED) {
            TextButton(onClick = onSave, enabled = !t.saved) { Text(stringResource(if (t.saved) R.string.perf_saved_one else R.string.perf_save)) }
        }
    }
}

@Composable
private fun GForceCard(state: PerformanceUiState) {
    val accent = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    JacCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(140.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension / 2
                // Rings at 0.5 g and 1.0 g; axes.
                drawCircle(grid, r, c, style = Stroke(1.dp.toPx()))
                drawCircle(grid, r / 2, c, style = Stroke(1.dp.toPx()))
                drawLine(grid, Offset(c.x, 0f), Offset(c.x, size.height))
                drawLine(grid, Offset(0f, c.y), Offset(size.width, c.y))
                state.g?.let { g ->
                    // Accelerating moves the dot up; braking moves it down. 1 g = outer ring.
                    val p = Offset(c.x + (g.lateral.coerceIn(-1.2, 1.2) * r).toFloat(), c.y - (g.longitudinal.coerceIn(-1.2, 1.2) * r).toFloat())
                    drawCircle(accent.copy(alpha = 0.25f), 14.dp.toPx(), p)
                    drawCircle(accent, 7.dp.toPx(), p)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                when {
                    state.gUnavailable -> Text(stringResource(R.string.g_unavailable), style = MaterialTheme.typography.bodyMedium)
                    state.g == null -> Text(stringResource(R.string.g_mount), style = MaterialTheme.typography.bodyMedium)
                    else -> {
                        MetricReadout(stringResource(R.string.g_longitudinal), Num.two(state.g.longitudinal), "g")
                        Spacer(Modifier.height(6.dp))
                        MetricReadout(stringResource(R.string.g_lateral), Num.two(state.g.lateral), "g")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.g_source), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RunRow(r: PerformanceRunEntity, best: Boolean, onDelete: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val type = runCatching { RunType.valueOf(r.type) }.getOrNull()
    JacCard(contentPadding = PaddingValues(start = 16.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(type?.let { label(it) } ?: r.type, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(8.dp))
                    Text(String.format(Locale.US, "%.2f s", r.timeS), style = Readout.Small, color = if (best) JacTheme.status.ok else MaterialTheme.colorScheme.onSurface)
                    if (best) {
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.perf_best), style = MaterialTheme.typography.labelSmall, color = JacTheme.status.ok)
                    }
                }
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(r.timestampMs)) +
                        (r.peakG?.let { " · ${Num.two(it)} g" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, null) }
        }
    }
}
