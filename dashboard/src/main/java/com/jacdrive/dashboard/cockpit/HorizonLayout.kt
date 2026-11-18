package com.jacdrive.dashboard.cockpit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Battery5Bar
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jacdrive.core.model.DashboardWidget
import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.UnitConverter
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.util.Num
import com.jacdrive.dashboard.R
import com.jacdrive.ui.cockpit.CockpitStyle
import com.jacdrive.ui.gauge.HorizonDial
import com.jacdrive.ui.gauge.RoadView
import com.jacdrive.ui.gauge.SubGauge
import com.jacdrive.ui.theme.Readout

/**
 * Minimal twin-ring cluster with a central road view. Every number comes from [CockpitUiState].
 * The instrument panel is never mirrored (speed left, engine speed right, gauges fill left→right),
 * matching real clusters in Arabic-market vehicles; labels inside remain translated.
 */
@Composable
internal fun HorizonLayout(state: CockpitUiState, style: CockpitStyle, nowMs: Long, warnings: Map<Telltale, Severity>) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) { HorizonCluster(state, style, nowMs, warnings) }
}

@Composable
private fun HorizonCluster(state: CockpitUiState, style: CockpitStyle, nowMs: Long, warnings: Map<Telltale, Severity>) {
    val snap = state.snapshot
    val speed = snap.display(VehicleMetric.SPEED, state.units, nowMs)
    val rpm = snap.display(VehicleMetric.RPM, state.units, nowMs)
    val fuel = snap.display(VehicleMetric.FUEL_LEVEL, state.units, nowMs)
    val coolant = snap.display(VehicleMetric.COOLANT_TEMP, state.units, nowMs)
    val animate = state.prefs.animationsEnabled

    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (state.visible(DashboardWidget.SPEED)) {
                HorizonDial(
                    fraction = speed.raw?.let { (UnitConverter.speed(it, state.units.speed) / UnitConverter.speedScaleMax(state.units.speed)).toFloat() },
                    color = style.accent,
                    value = speed.text ?: "—",
                    unit = speed.unit,
                    style = style,
                    animate = animate,
                    dimmed = speed.freshness == Freshness.STALE,
                    sub = if (state.visible(DashboardWidget.FUEL_LEVEL)) {
                        SubGauge(
                            fraction = fuel.raw?.let { (it / 100).toFloat() },
                            startLabel = "E", endLabel = "F",
                            icon = Icons.Rounded.LocalGasStation,
                            caption = fuel.text?.let { "$it%" } ?: if (fuel.unsupported) stringResource(R.string.cockpit_na) else null,
                            alert = (fuel.raw ?: 100.0) < 10,
                        )
                    } else null,
                )
            }
        }

        Column(Modifier.weight(0.95f).fillMaxHeight().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TripChip(state, style, nowMs)
            RoadView(
                speedKmh = speed.raw?.toFloat() ?: 0f,
                style = style,
                animate = animate,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp),
            )
            BottomBar(state, style, nowMs, warnings)
        }

        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (state.visible(DashboardWidget.RPM)) {
                HorizonDial(
                    fraction = rpm.raw?.let { (it / 8000).toFloat() },
                    color = if (warnings[Telltale.OVER_REV] != null) style.critical else style.accentSecondary,
                    value = rpm.raw?.let { Num.one(it / 1000) } ?: "—",
                    unit = stringResource(R.string.cockpit_rpm_x1000),
                    style = style,
                    animate = animate,
                    dimmed = rpm.freshness == Freshness.STALE,
                    sub = if (state.visible(DashboardWidget.COOLANT_TEMP)) {
                        SubGauge(
                            fraction = coolant.raw?.let { ((it - 50) / 80).toFloat() },
                            startLabel = "C", endLabel = "H",
                            icon = Icons.Rounded.Thermostat,
                            caption = coolant.text?.let { "$it${coolant.unit}" } ?: if (coolant.unsupported) stringResource(R.string.cockpit_na) else null,
                            alert = warnings[Telltale.COOLANT_HOT] != null,
                        )
                    } else null,
                )
            }
        }
    }
}

@Composable
private fun TripChip(state: CockpitUiState, style: CockpitStyle, nowMs: Long) {
    if (!state.visible(DashboardWidget.AVG_FUEL) && !state.visible(DashboardWidget.TRIP_DISTANCE)) return
    val trip = state.snapshot.display(VehicleMetric.TRIP_DISTANCE, state.units, nowMs)
    val avg = state.snapshot.display(VehicleMetric.AVG_FUEL_CONSUMPTION, state.units, nowMs)
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier.clip(shape).background(style.panel).border(1.dp, style.panelBorder, shape).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Route, null, tint = style.accent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("${trip.text ?: "—"} ${trip.unit}", style = Readout.Small.copy(fontSize = 15.sp), color = style.text)
        if (state.visible(DashboardWidget.AVG_FUEL)) {
            Text("  ·  ", style = Readout.Small.copy(fontSize = 15.sp), color = style.textDim)
            Text("${avg.text ?: "—"} ${avg.unit}", style = Readout.Small.copy(fontSize = 15.sp), color = style.text)
        }
    }
}

@Composable
private fun BottomBar(state: CockpitUiState, style: CockpitStyle, nowMs: Long, warnings: Map<Telltale, Severity>) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(style.panel).border(1.dp, style.panelBorder, shape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        if (state.visible(DashboardWidget.AMBIENT_TEMP)) {
            BarValue(Icons.Rounded.DeviceThermostat, state.snapshot.display(VehicleMetric.AMBIENT_TEMP, state.units, nowMs), style, null)
        } else Spacer(Modifier.width(1.dp))
        TelltaleRow(warnings, style)
        if (state.visible(DashboardWidget.BATTERY_VOLTAGE)) {
            BarValue(Icons.Rounded.Battery5Bar, state.snapshot.display(VehicleMetric.BATTERY_VOLTAGE, state.units, nowMs), style, warnings[Telltale.BATTERY_VOLTAGE])
        } else Spacer(Modifier.width(1.dp))
    }
}

@Composable
private fun BarValue(icon: ImageVector, d: MetricDisplay, style: CockpitStyle, severity: Severity?) {
    val color = when (severity) {
        Severity.CRITICAL -> style.critical
        Severity.WARNING -> style.warning
        null -> style.text
    }
    LtrRow {
        Icon(icon, null, tint = style.textDim, modifier = Modifier.size(16.dp).align(Alignment.CenterVertically))
        Spacer(Modifier.width(4.dp))
        Text(d.text ?: "—", style = Readout.Small.copy(fontSize = 17.sp), color = color)
        if (d.text != null) Text(d.unit, style = Readout.Unit.copy(fontSize = 12.sp), color = style.textDim, modifier = Modifier.padding(start = 2.dp, bottom = 2.dp))
    }
}
