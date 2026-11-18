package com.jacdrive.analytics.trips

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.analytics.R
import com.jacdrive.core.database.TripEntity
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.UnitConverter
import com.jacdrive.core.util.Num
import com.jacdrive.ui.chart.BarChart
import com.jacdrive.ui.chart.BarDatum
import com.jacdrive.ui.components.EmptyState
import com.jacdrive.ui.components.InfoNote
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.MetricReadout
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import com.jacdrive.ui.components.StatusPill
import com.jacdrive.ui.theme.JacTheme
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun TripsScreen(onBack: (() -> Unit)?, onOpenTrip: (Long) -> Unit, viewModel: TripsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(viewModel::export) }
    val u = state.speedUnit
    val distUnit = if (u == SpeedUnit.KMH) "km" else "mi"
    val speedUnit = if (u == SpeedUnit.KMH) "km/h" else "mph"
    val locale = LocalConfiguration.current.locales[0]

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ScreenHeader(
                stringResource(R.string.trips_title), subtitle = stringResource(R.string.trips_subtitle), onBack = onBack,
                actions = {
                    IconButton(onClick = { exporter.launch("jacdrive-trips.csv") }, enabled = state.trips.isNotEmpty()) {
                        Icon(Icons.Rounded.FileDownload, stringResource(R.string.trips_export))
                    }
                },
            )
        }
        if (state.recording) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(stringResource(R.string.trips_recording), JacTheme.status.critical, icon = Icons.Rounded.FiberManualRecord)
                }
            }
        }
        state.exportResult?.let { ok ->
            item {
                InfoNote(stringResource(if (ok) R.string.trips_export_ok else R.string.trips_export_fail), color = if (ok) JacTheme.status.ok else JacTheme.status.critical)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(StatsPeriod.DAYS_7 to R.string.trips_7d, StatsPeriod.DAYS_30 to R.string.trips_30d, StatsPeriod.MONTHS_12 to R.string.trips_12m).forEach { (p, l) ->
                    FilterChip(state.period == p, { viewModel.setPeriod(p) }, label = { Text(stringResource(l)) })
                }
            }
        }
        state.summary?.let { s ->
            item {
                JacCard {
                    Row {
                        MetricReadout(stringResource(R.string.trips_distance), Num.one(UnitConverter.distance(s.distanceKm, u)), distUnit, Modifier.weight(1f))
                        MetricReadout(stringResource(R.string.trips_time), Num.one(s.durationS / 3600.0), "h", Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        MetricReadout(stringResource(R.string.trips_avg_speed), s.avgSpeed?.let { Num.int(UnitConverter.speed(it, u)) } ?: "—", speedUnit, Modifier.weight(1f))
                        MetricReadout(stringResource(R.string.trips_consumption), s.consumption?.let(Num::one) ?: "—", "L/100km", Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            JacCard {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(StatsMetric.DISTANCE to R.string.trips_distance, StatsMetric.AVG_SPEED to R.string.trips_avg_speed, StatsMetric.CONSUMPTION to R.string.trips_consumption).forEach { (m, l) ->
                        FilterChip(state.metric == m, { viewModel.setMetric(m) }, label = { Text(stringResource(l), style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                val labelFmt = SimpleDateFormat(if (state.period == StatsPeriod.MONTHS_12) "MMM" else "d", locale)
                val data = state.buckets.map { b ->
                    val v = b.value(state.metric)?.let {
                        when (state.metric) {
                            StatsMetric.DISTANCE -> UnitConverter.distance(it, u)
                            StatsMetric.AVG_SPEED -> UnitConverter.speed(it, u)
                            StatsMetric.CONSUMPTION -> it
                        }
                    }
                    BarDatum(labelFmt.format(Date(b.startMs)), v)
                }
                val unitLabel = when (state.metric) {
                    StatsMetric.DISTANCE -> distUnit
                    StatsMetric.AVG_SPEED -> speedUnit
                    StatsMetric.CONSUMPTION -> "L/100km"
                }
                BarChart(
                    data = data,
                    color = MaterialTheme.colorScheme.primary,
                    formatValue = { "${Num.one(it)} $unitLabel" },
                    labelEvery = if (state.period == StatsPeriod.DAYS_30) 5 else 1,
                )
                if (state.metric == StatsMetric.CONSUMPTION) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.trips_consumption_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionHeader(stringResource(R.string.trips_history)) }
        if (state.trips.isEmpty()) {
            item { JacCard { EmptyState(Icons.Rounded.Route, stringResource(R.string.trips_empty), stringResource(R.string.trips_empty_sub)) } }
        } else {
            items(state.trips.take(100), key = { it.id }) { t -> TripRow(t, u) { onOpenTrip(t.id) } }
        }
    }
}

@Composable
private fun TripRow(t: TripEntity, u: SpeedUnit, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    JacCard(onClick = onClick, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(t.startMs)), style = MaterialTheme.typography.titleSmall)
                Text(
                    "${Num.one(UnitConverter.distance(t.distanceKm, u))} ${if (u == SpeedUnit.KMH) "km" else "mi"} · ${t.durationS / 60} min" +
                        (t.consumptionL100?.let { " · ${Num.one(it)} L/100km" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!t.completed) StatusPill(stringResource(R.string.trips_in_progress), JacTheme.status.critical)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        }
    }
    Spacer(Modifier.width(0.dp).fillMaxWidth())
}
