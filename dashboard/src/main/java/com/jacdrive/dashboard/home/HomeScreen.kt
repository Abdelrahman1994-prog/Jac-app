package com.jacdrive.dashboard.home

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.automirrored.rounded.ManageSearch
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.dashboard.R
import com.jacdrive.dashboard.cockpit.display
import com.jacdrive.ui.cockpit.CockpitStyles
import com.jacdrive.ui.components.DataModeBanner
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.MetricReadout
import com.jacdrive.ui.components.SectionHeader
import com.jacdrive.ui.components.StatusPill
import com.jacdrive.ui.components.labelRes
import com.jacdrive.ui.gauge.CircularGauge
import com.jacdrive.ui.theme.JacFonts
import com.jacdrive.ui.theme.JacTheme
import com.jacdrive.ui.theme.Readout

enum class HomeDestination { COCKPIT, DIAGNOSTICS, PERFORMANCE, TRIPS, MAINTENANCE, VEHICLE, THEMES, SETTINGS }

@Composable
fun HomeScreen(onNavigate: (HomeDestination) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now = state.snapshot.timestampMs.coerceAtLeast(System.currentTimeMillis() - 1)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HomeHeader(state) }
        if (state.snapshot.mode != DataMode.LIVE) {
            item { DataModeBanner(state.snapshot.mode, onAction = { onNavigate(HomeDestination.SETTINGS) }) }
        }
        item { CockpitHeroCard(state, now) { onNavigate(HomeDestination.COCKPIT) } }
        item {
            JacCard {
                SectionHeader(stringResource(R.string.home_live_overview))
                Spacer(Modifier.height(6.dp))
                listOf(
                    listOf(VehicleMetric.COOLANT_TEMP, VehicleMetric.BATTERY_VOLTAGE),
                    listOf(VehicleMetric.ENGINE_LOAD, VehicleMetric.INTAKE_AIR_TEMP),
                ).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        row.forEach { m ->
                            val d = state.snapshot.display(m, state.units, now)
                            MetricReadout(
                                label = stringResource(m.labelRes()),
                                value = d.text ?: "—",
                                unit = d.unit,
                                freshness = d.freshness,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        item { SectionHeader(stringResource(R.string.home_quick_access)) }
        val tiles = listOf(
            Tile(HomeDestination.DIAGNOSTICS, Icons.AutoMirrored.Rounded.ManageSearch, R.string.nav_diagnostics, R.string.home_diag_sub),
            Tile(HomeDestination.PERFORMANCE, Icons.Rounded.Timer, R.string.nav_performance, R.string.home_perf_sub),
            Tile(HomeDestination.TRIPS, Icons.Rounded.Route, R.string.nav_trips, R.string.home_trips_sub),
            Tile(HomeDestination.MAINTENANCE, Icons.Rounded.Build, R.string.nav_maintenance, R.string.home_maint_sub),
            Tile(HomeDestination.VEHICLE, Icons.Rounded.DirectionsCar, R.string.nav_vehicle, R.string.home_vehicle_sub),
            Tile(HomeDestination.THEMES, Icons.Rounded.Palette, R.string.home_themes, R.string.home_themes_sub),
            Tile(HomeDestination.SETTINGS, Icons.Rounded.Settings, R.string.nav_settings, R.string.home_settings_sub),
        )
        tiles.chunked(2).forEach { row ->
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { t -> QuickTile(t, Modifier.weight(1f)) { onNavigate(t.destination) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private data class Tile(val destination: HomeDestination, val icon: ImageVector, val title: Int, val subtitle: Int)

@Composable
private fun HomeHeader(state: HomeUiState) {
    val status = JacTheme.status
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "JAC DRIVE",
                fontFamily = JacFonts.Display, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.3.em,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(state.vehicle?.name ?: "JAC J7", style = MaterialTheme.typography.headlineMedium)
            state.vehicle?.let {
                Text("${it.modelYear} · ${it.trim}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (state.snapshot.mode) {
            DataMode.LIVE -> StatusPill(stringResource(R.string.cockpit_mode_live), status.ok)
            DataMode.DEMO -> StatusPill(stringResource(R.string.home_demo), status.demo)
            DataMode.OFFLINE -> StatusPill(stringResource(R.string.home_offline), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CockpitHeroCard(state: HomeUiState, now: Long, onLaunch: () -> Unit) {
    val style = remember(state.cockpit) { CockpitStyles.resolve(state.cockpit) }
    val speed = state.snapshot.display(VehicleMetric.SPEED, state.units, now)
    val rpm = state.snapshot.display(VehicleMetric.RPM, state.units, now)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(style.backgroundTop, style.backgroundBottom)))
            .background(Brush.radialGradient(listOf(style.accent.copy(alpha = 0.18f), androidx.compose.ui.graphics.Color.Transparent)))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularGauge(
                value = rpm.raw?.toFloat(), min = 0f, max = 8000f, style = style,
                modifier = Modifier.size(150.dp),
                majorStep = 1000f, minorDivisions = 2, labelDivisor = 1000f,
                dangerFrom = state.cockpit.redlineRpm.toFloat(),
                animate = state.cockpit.animationsEnabled,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(speed.text ?: "—", style = Readout.Large.copy(fontSize = 40.sp), color = style.text)
                    Text(speed.unit, style = Readout.Unit, color = style.accent)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_cockpit_title), style = MaterialTheme.typography.titleLarge, color = style.text)
                Text(
                    stringResource(R.string.home_cockpit_sub, stringResource(style.id.labelRes())),
                    style = MaterialTheme.typography.bodySmall,
                    color = style.textDim,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onLaunch,
                    colors = ButtonDefaults.buttonColors(containerColor = style.accent, contentColor = androidx.compose.ui.graphics.Color.Black),
                ) {
                    Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_launch))
                }
            }
        }
    }
}

@Composable
private fun QuickTile(tile: Tile, modifier: Modifier, onClick: () -> Unit) {
    JacCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(16.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) { Icon(tile.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(tile.title), style = MaterialTheme.typography.titleSmall)
        Text(
            stringResource(tile.subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
        )
    }
}

