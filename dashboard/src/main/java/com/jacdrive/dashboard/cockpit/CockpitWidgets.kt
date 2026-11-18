package com.jacdrive.dashboard.cockpit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jacdrive.core.model.DashboardWidget
import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.UnitConverter
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.dashboard.R
import com.jacdrive.ui.cockpit.CockpitStyle
import com.jacdrive.ui.components.labelRes
import com.jacdrive.ui.gauge.BarGauge
import com.jacdrive.ui.gauge.CircularGauge
import com.jacdrive.ui.theme.Readout

private val TileShape = RoundedCornerShape(16.dp)

@Composable
fun CockpitTile(
    widget: DashboardWidget,
    state: CockpitUiState,
    style: CockpitStyle,
    nowMs: Long,
    modifier: Modifier = Modifier,
    severity: Severity? = null,
) {
    val metric = widget.metric ?: return
    val d = state.snapshot.display(metric, state.units, nowMs)
    val accentColor = when (severity) {
        Severity.CRITICAL -> style.critical
        Severity.WARNING -> style.warning
        null -> style.panelBorder
    }
    val fraction = barFraction(metric, d.raw)
    Column(
        modifier
            .clip(TileShape)
            .background(style.panel)
            .border(1.dp, accentColor, TileShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(metric.labelRes()).uppercase(),
                style = Readout.Caption.copy(fontSize = 10.sp),
                color = style.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            FreshnessIndicator(d.freshness, style)
        }
        LtrRow {
            val valueText = when {
                d.text != null -> d.text
                d.unsupported -> stringResource(R.string.cockpit_na)
                else -> "—"
            }
            Text(
                valueText,
                style = Readout.Medium.copy(fontSize = 26.sp),
                color = when {
                    severity == Severity.CRITICAL -> style.critical
                    severity == Severity.WARNING -> style.warning
                    d.freshness == Freshness.STALE -> style.textDim
                    else -> style.text
                },
                maxLines = 1,
            )
            if (d.text != null) {
                Spacer(Modifier.width(4.dp))
                Text(d.unit, style = Readout.Unit.copy(fontSize = 11.sp), color = style.textDim, modifier = Modifier.padding(bottom = 4.dp))
            }
        }
        if (fraction != null || barFraction(metric, 0.0) != null) {
            Spacer(Modifier.height(4.dp))
            BarGauge(
                fraction = fraction,
                segments = 16,
                height = 4.dp,
                trackColor = style.track,
                animate = state.prefs.animationsEnabled,
            ) { t -> if (severity != null) accentColor else lerp(style.accentSecondary, style.accent, t) }
        }
    }
}

@Composable
private fun FreshnessIndicator(freshness: Freshness, style: CockpitStyle) {
    val color = when (freshness) {
        Freshness.LIVE -> style.ok
        Freshness.STALE -> style.warning
        Freshness.MISSING -> style.textDim.copy(alpha = 0.4f)
    }
    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
}

/** Scales a number to the dial it sits in. */
@Composable
private fun DialCenter(
    value: String,
    unit: String,
    caption: String,
    style: CockpitStyle,
    heroFraction: Float = 0.22f,
    secondary: String? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val dial: Dp = if (maxWidth < maxHeight) maxWidth else maxHeight
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value,
                style = Readout.Hero.copy(fontSize = (dial.value * heroFraction).sp, lineHeight = (dial.value * heroFraction).sp),
                color = style.text,
                textAlign = TextAlign.Center,
            )
            Text(unit, style = Readout.Unit, color = style.accent)
            Text(caption.uppercase(), style = Readout.Caption, color = style.textDim)
            if (secondary != null) {
                Spacer(Modifier.height(4.dp))
                Text(secondary, style = Readout.Small, color = style.textDim)
            }
        }
    }
}

@Composable
fun SpeedDial(state: CockpitUiState, style: CockpitStyle, nowMs: Long, modifier: Modifier = Modifier) {
    val d = state.snapshot.display(VehicleMetric.SPEED, state.units, nowMs)
    val max = UnitConverter.speedScaleMax(state.units.speed).toFloat()
    val v = d.raw?.let { UnitConverter.speed(it, state.units.speed).toFloat() }
    CircularGauge(
        value = v, min = 0f, max = max, style = style, modifier = modifier,
        majorStep = 20f, minorDivisions = 4,
        animate = state.prefs.animationsEnabled,
        dimmed = d.freshness == Freshness.STALE,
    ) {
        DialCenter(d.text ?: "—", d.unit, stringResource(com.jacdrive.ui.R.string.metric_speed), style)
    }
}

@Composable
fun RpmDial(
    state: CockpitUiState,
    style: CockpitStyle,
    nowMs: Long,
    modifier: Modifier = Modifier,
    showSpeedInCenter: Boolean = false,
) {
    val rpm = state.snapshot.display(VehicleMetric.RPM, state.units, nowMs)
    val center = if (showSpeedInCenter) state.snapshot.display(VehicleMetric.SPEED, state.units, nowMs) else null
    CircularGauge(
        value = rpm.raw?.toFloat(), min = 0f, max = 8000f, style = style, modifier = modifier,
        majorStep = 1000f, minorDivisions = 5, labelDivisor = 1000f,
        dangerFrom = state.prefs.redlineRpm.toFloat(),
        animate = state.prefs.animationsEnabled,
        dimmed = rpm.freshness == Freshness.STALE,
    ) {
        if (center != null) {
            DialCenter(
                value = center.text ?: "—",
                unit = center.unit,
                caption = stringResource(com.jacdrive.ui.R.string.metric_speed),
                style = style,
                heroFraction = 0.25f,
                secondary = rpm.text?.let { "$it rpm" },
            )
        } else {
            DialCenter(rpm.text ?: "—", stringResource(R.string.cockpit_rpm_unit), stringResource(com.jacdrive.ui.R.string.metric_rpm), style, heroFraction = 0.18f)
        }
    }
}

/** Shift-light style RPM bar for the digital layout. */
@Composable
fun RpmBar(state: CockpitUiState, style: CockpitStyle, nowMs: Long, modifier: Modifier = Modifier) {
    val rpm = state.snapshot.display(VehicleMetric.RPM, state.units, nowMs)
    val redFraction = state.prefs.redlineRpm / 8000f
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BarGauge(
            fraction = rpm.raw?.let { (it / 8000).toFloat() },
            modifier = Modifier.weight(1f),
            segments = 40,
            height = 18.dp,
            trackColor = style.track,
            animate = state.prefs.animationsEnabled,
        ) { t ->
            when {
                t >= redFraction -> style.critical
                t >= redFraction - 0.15f -> style.warning
                else -> lerp(style.accentSecondary, style.accent, t / redFraction)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(rpm.text ?: "—", style = Readout.Medium, color = style.text)
            Text("RPM", style = Readout.Caption, color = style.textDim)
        }
    }
}

@Composable
fun HeroSpeed(state: CockpitUiState, style: CockpitStyle, nowMs: Long, modifier: Modifier = Modifier) {
    val d = state.snapshot.display(VehicleMetric.SPEED, state.units, nowMs)
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val size = (if (maxHeight < maxWidth * 0.6f) maxHeight.value * 0.62f else maxWidth.value * 0.36f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                d.text ?: "—",
                style = Readout.Hero.copy(fontSize = size.sp, lineHeight = size.sp),
                color = if (d.freshness == Freshness.STALE) style.textDim else style.text,
            )
            Text(
                if (state.units.speed == SpeedUnit.KMH) "KM/H" else "MPH",
                style = Readout.Unit.copy(fontSize = 16.sp),
                color = style.accent,
            )
        }
    }
}

@Composable
fun TelltaleRow(warnings: Map<Telltale, Severity>, style: CockpitStyle, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Telltale.entries.forEach { t ->
            val sev = warnings[t]
            val color = when (sev) {
                Severity.CRITICAL -> style.critical
                Severity.WARNING -> style.warning
                null -> style.textDim.copy(alpha = 0.22f)
            }
            val icon = when (t) {
                Telltale.COOLANT_HOT -> Icons.Rounded.Thermostat
                Telltale.BATTERY_VOLTAGE -> Icons.Rounded.BatteryAlert
                Telltale.OVER_REV -> Icons.Rounded.Speed
            }
            val desc = stringResource(
                when (t) {
                    Telltale.COOLANT_HOT -> R.string.telltale_coolant
                    Telltale.BATTERY_VOLTAGE -> R.string.telltale_battery
                    Telltale.OVER_REV -> R.string.telltale_overrev
                },
            )
            Icon(icon, contentDescription = if (sev != null) desc else null, tint = color, modifier = Modifier.size(22.dp))
        }
    }
}

/** Lays tiles out in a grid with [columns] columns that fills the available height. */
@Composable
fun TileGrid(
    widgets: List<DashboardWidget>,
    columns: Int,
    state: CockpitUiState,
    style: CockpitStyle,
    nowMs: Long,
    warnings: Map<Telltale, Severity>,
    modifier: Modifier = Modifier,
) {
    if (widgets.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        widgets.chunked(columns).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { w ->
                    CockpitTile(w, state, style, nowMs, Modifier.weight(1f).fillMaxSize(), severity = severityFor(w, warnings))
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

fun severityFor(widget: DashboardWidget, warnings: Map<Telltale, Severity>): Severity? = when (widget) {
    DashboardWidget.COOLANT_TEMP -> warnings[Telltale.COOLANT_HOT]
    DashboardWidget.BATTERY_VOLTAGE -> warnings[Telltale.BATTERY_VOLTAGE]
    else -> null
}


/** Value + unit pairs read left-to-right in both languages ("41 %", "77 °C"). */
@Composable
internal fun LtrRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
    ) {
        Row(verticalAlignment = Alignment.Bottom, content = content)
    }
}
