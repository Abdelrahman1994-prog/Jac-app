package com.jacdrive.dashboard.dash

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShowChart
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.UnitConverter
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.freshness
import com.jacdrive.core.util.Num
import com.jacdrive.dashboard.R
import com.jacdrive.dashboard.cockpit.barFraction
import com.jacdrive.dashboard.cockpit.display
import com.jacdrive.obd.history.HistoryPoint
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.labelRes
import com.jacdrive.ui.gauge.CircularGauge
import com.jacdrive.ui.theme.JacFonts
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

enum class DashDestination { DTC, LIVE_DATA, SETTINGS, ADAPTER }

@Composable
fun DashboardScreen(
    onOpenDrawer: () -> Unit,
    onNavigate: (DashDestination) -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1_000) } }
    DashboardContent(state, now, onOpenDrawer, onNavigate, viewModel::setSport)
}

/** Stateless dashboard, driven entirely by [state]. */
@Composable
fun DashboardContent(
    state: DashboardUiState,
    now: Long,
    onOpenDrawer: () -> Unit,
    onNavigate: (DashDestination) -> Unit,
    onSport: (Boolean) -> Unit,
) {
    val palette = if (state.cockpit.sportMode) DashPalette.Sport else DashPalette.Normal
    val check = EngineCheckRules.evaluate(state.snapshot, state.sessions, now)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { DashHeader(state, check, palette, onOpenDrawer, { onNavigate(DashDestination.SETTINGS) }, { onNavigate(DashDestination.ADAPTER) }, onSport) }
        item { Hero(state, palette, now) }
        item { MetricGrid(state, palette, now) }
        item {
            BoxWithConstraints {
                if (maxWidth > 600.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TripCard(state, now, Modifier.weight(1f))
                        DtcCard(check, Modifier.weight(1.2f)) { onNavigate(DashDestination.DTC) }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TripCard(state, now, Modifier.fillMaxWidth())
                        DtcCard(check, Modifier.fillMaxWidth()) { onNavigate(DashDestination.DTC) }
                    }
                }
            }
        }
        item { TrendCard(state, palette, now) { onNavigate(DashDestination.LIVE_DATA) } }
    }
}

private val Numeral = TextStyle(fontFamily = JacFonts.Display, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, textDirection = TextDirection.Ltr)

// ---------------------------------------------------------------- header

@Composable
private fun DashHeader(
    state: DashboardUiState,
    check: EngineCheck,
    palette: DashPalette,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
    onAdapter: () -> Unit,
    onSport: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, stringResource(R.string.dash_menu), tint = Color.White) }
            Spacer(Modifier.weight(1f))
            // Vehicle branding from the user's vehicle profile (plain text, no manufacturer logo).
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Row(verticalAlignment = Alignment.Bottom) {
                Text(state.make.uppercase(), style = Numeral.copy(fontSize = 26.sp, letterSpacing = 0.06.em), color = Color.White)
                Spacer(Modifier.width(6.dp))
                Text(state.model.uppercase(), style = Numeral.copy(fontSize = 26.sp, letterSpacing = 0.06.em), color = palette.accent2)
            } }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, stringResource(R.string.dash_settings), tint = Color.White) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectionPill(state, Modifier.weight(1f), onAdapter)
            SportToggle(state.cockpit.sportMode, palette, onSport)
            CheckEnginePill(check)
        }
    }
}

@Composable
private fun ConnectionPill(state: DashboardUiState, modifier: Modifier, onClick: () -> Unit) {
    val s = state.snapshot
    val (color, title, sub) = when {
        s.mode == DataMode.DEMO -> Triple(DashPalette.Purple, stringResource(R.string.dash_demo), stringResource(R.string.dash_simulated))
        s.connection is ConnectionState.Connected -> Triple(DashPalette.Green, stringResource(R.string.dash_connected), stringResource(R.string.dash_live_data))
        s.connection is ConnectionState.Connecting -> Triple(DashPalette.Amber, stringResource(R.string.dash_connecting), "OBDII")
        s.connection is ConnectionState.Failed -> Triple(DashPalette.Amber, stringResource(R.string.dash_reconnecting), "OBDII")
        else -> Triple(DashPalette.Red, stringResource(R.string.dash_disconnected), stringResource(R.string.dash_tap_connect))
    }
    val pulse by rememberInfiniteTransition(label = "conn").animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.clip(shape).background(Color(0x22102A55)).border(1.dp, color.copy(alpha = 0.35f), shape).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color.copy(alpha = if (color == DashPalette.Green || color == DashPalette.Purple) pulse else 1f)))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("$title · OBDII", style = MaterialTheme.typography.labelLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9FB6D6), maxLines = 1)
        }
    }
}

@Composable
private fun SportToggle(on: Boolean, palette: DashPalette, onChange: (Boolean) -> Unit) {
    val bg by animateColorAsState(if (on) palette.accent.copy(alpha = 0.25f) else Color(0x22102A55), label = "sportBg")
    val fg by animateColorAsState(if (on) DashPalette.Orange else Color(0xFF9FB6D6), label = "sportFg")
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.clip(shape).background(bg).border(1.dp, fg.copy(alpha = 0.6f), shape).clickable { onChange(!on) }.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.SportsScore, null, tint = fg, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.dash_sport), style = MaterialTheme.typography.labelSmall, color = fg)
    }
}

@Composable
private fun CheckEnginePill(check: EngineCheck) {
    val (color, value) = when (check.state) {
        EngineCheckState.OK -> DashPalette.Green to stringResource(R.string.dash_ok)
        EngineCheckState.FAULT -> DashPalette.Red to (check.codeCount?.takeIf { it > 0 }?.let { stringResource(R.string.dash_faults_n, it) } ?: stringResource(R.string.dash_fault))
        EngineCheckState.UNKNOWN -> DashPalette.Amber to stringResource(R.string.dash_unknown)
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.clip(shape).background(color.copy(alpha = 0.12f)).border(1.5.dp, color.copy(alpha = 0.8f), shape).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(EngineIcon, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(stringResource(R.string.dash_check_engine), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- hero

@Composable
private fun Hero(state: DashboardUiState, palette: DashPalette, now: Long) {
    val s = state.snapshot
    val speed = s.display(VehicleMetric.SPEED, state.units, now)
    val rpm = s.display(VehicleMetric.RPM, state.units, now)
    val animate = state.cockpit.animationsEnabled
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gauge = minOf(maxWidth * 0.45f, 260.dp)
        val sceneH = gauge * 1.02f
        Box(Modifier.fillMaxWidth().height(sceneH)) {
            HeroScene(
                speedKmh = (if (s.mode != DataMode.OFFLINE && speed.freshness == Freshness.LIVE) speed.raw ?: 0.0 else 0.0).toFloat(),
                palette = palette, animate = animate,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
            )
            val speedMax = UnitConverter.speedScaleMax(state.units.speed).toFloat()
            CircularGauge(
                value = speed.raw?.let { UnitConverter.speed(it, state.units.speed).toFloat() }, min = 0f, max = speedMax,
                style = palette.speedStyle, modifier = Modifier.size(gauge).align(Alignment.CenterStart),
                majorStep = if (speedMax > 200) 30f else 20f, minorDivisions = 3, animate = animate,
                dimmed = speed.freshness == Freshness.STALE,
            ) { GaugeCenter(speed.text ?: if (speed.unsupported) "N/A" else "—", speed.unit, palette.accent, gauge.value) }
            CircularGauge(
                value = rpm.raw?.toFloat(), min = 0f, max = 8000f,
                style = palette.rpmStyle, modifier = Modifier.size(gauge).align(Alignment.CenterEnd),
                majorStep = 1000f, minorDivisions = 4, labelDivisor = 1000f, dangerFrom = state.cockpit.redlineRpm.toFloat(),
                animate = animate, dimmed = rpm.freshness == Freshness.STALE,
            ) { GaugeCenter(rpm.raw?.let { Num.one(it / 1000) } ?: if (rpm.unsupported) "N/A" else "—", stringResource(R.string.dash_rpm_unit), Color.White, gauge.value) }
        }
    } }
}

@Composable
private fun GaugeCenter(value: String, unit: String, unitColor: Color, gaugeDp: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Numeral.copy(fontSize = (gaugeDp * 0.165f).sp, lineHeight = (gaugeDp * 0.17f).sp), color = Color.White)
        Text(unit, style = MaterialTheme.typography.labelLarge.copy(fontSize = (gaugeDp * 0.05f).sp, textDirection = TextDirection.Ltr), color = unitColor)
    }
}

// ---------------------------------------------------------------- metric cards

private data class MetricSpec(val metric: VehicleMetric, val icon: ImageVector, val identity: Color)

@Composable
private fun MetricGrid(state: DashboardUiState, palette: DashPalette, now: Long) {
    val specs = listOf(
        MetricSpec(VehicleMetric.COOLANT_TEMP, Icons.Rounded.DeviceThermostat, DashPalette.Cyan),
        MetricSpec(VehicleMetric.BATTERY_VOLTAGE, Icons.Rounded.BatteryChargingFull, DashPalette.Blue),
        MetricSpec(VehicleMetric.FUEL_LEVEL, Icons.Rounded.LocalGasStation, DashPalette.Amber),
        MetricSpec(VehicleMetric.ENGINE_LOAD, Icons.Rounded.Scale, DashPalette.Purple),
        MetricSpec(VehicleMetric.THROTTLE_POSITION, Icons.Rounded.Tune, DashPalette.Green),
        MetricSpec(VehicleMetric.INTAKE_AIR_TEMP, Icons.Rounded.Air, DashPalette.Teal),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        specs.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { MetricCard(it, state, palette, now, Modifier.weight(1f)) }
            }
        }
    }
}

/** Warning colour for a value, or null when normal. Uses the user's alert thresholds where they exist. */
private fun warning(state: DashboardUiState, metric: VehicleMetric, v: Double, now: Long): Color? {
    val rpm = state.snapshot[VehicleMetric.RPM]?.takeIf { it.freshness(VehicleMetric.RPM, now) == Freshness.LIVE }?.value ?: 0.0
    return when (metric) {
        VehicleMetric.COOLANT_TEMP -> when {
            v >= state.alerts.coolantThresholdC -> DashPalette.Red
            v >= state.alerts.coolantThresholdC - 5 -> DashPalette.Orange
            else -> null
        }
        VehicleMetric.BATTERY_VOLTAGE -> when {
            v < 11.8 || v > state.alerts.batteryHighV + 0.8 -> DashPalette.Red
            v > state.alerts.batteryHighV || (rpm > 400 && v < state.alerts.batteryLowRunningV) || (rpm <= 400 && v < 12.0) -> DashPalette.Orange
            else -> null
        }
        VehicleMetric.FUEL_LEVEL -> when {
            v < 10 -> DashPalette.Red
            v < 20 -> DashPalette.Orange
            else -> null
        }
        VehicleMetric.INTAKE_AIR_TEMP -> if (v > 60) DashPalette.Orange else null
        else -> null
    }
}

@Composable
private fun MetricCard(spec: MetricSpec, state: DashboardUiState, palette: DashPalette, now: Long, modifier: Modifier) {
    val d = state.snapshot.display(spec.metric, state.units, now)
    val warn = d.raw?.let { warning(state, spec.metric, it, now) }
    val color by animateColorAsState(warn ?: spec.identity, tween(400), label = "metricColor")
    val fraction by animateFloatAsState(barFraction(spec.metric, d.raw) ?: 0f, tween(500), label = "metricBar")
    val stale = d.freshness == Freshness.STALE
    val value = when {
        d.unsupported -> "N/A"
        d.text == null -> "—"
        else -> d.text
    }
    JacCard(modifier, contentPadding = PaddingValues(12.dp), highlight = color.copy(alpha = if (warn != null) 1f else 0.75f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(spec.icon, null, tint = color, modifier = Modifier.size(26.dp))
            Spacer(Modifier.weight(1f))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = Numeral.copy(fontSize = 24.sp), color = if (warn != null) color else if (stale) Color(0xFF9FB6D6) else Color.White, maxLines = 1)
                if (d.text != null) Text(" ${d.unit}", style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr), color = Color(0xFFCFE3FF), modifier = Modifier.padding(bottom = 3.dp))
            } }
        }
        Text(
            stringResource(spec.metric.labelRes()), style = MaterialTheme.typography.labelMedium, color = Color(0xFFCFE3FF),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0x22FFFFFF))) {
            if (d.raw != null) {
                Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(5.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.6f), color))))
            }
        }
    }
}

// ---------------------------------------------------------------- trip & DTC

@Composable
private fun TripCard(state: DashboardUiState, now: Long, modifier: Modifier) {
    val trip = state.snapshot.display(VehicleMetric.TRIP_DISTANCE, state.units, now)
    val avg = state.snapshot.display(VehicleMetric.AVG_FUEL_CONSUMPTION, state.units, now)
    JacCard(modifier, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Route, null, tint = DashPalette.Cyan, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.dash_trip_info), style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text(if (trip.text != null) "${trip.text} ${trip.unit}" else if (trip.unsupported) "N/A" else "—", style = Numeral.copy(fontSize = 22.sp, fontStyle = FontStyle.Normal), color = Color.White)
                Text(stringResource(R.string.dash_trip_distance), style = MaterialTheme.typography.labelMedium, color = Color(0xFF9FB6D6))
            }
            Box(Modifier.width(1.dp).height(40.dp).background(Color(0x33FFFFFF)))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (avg.text != null) "${avg.text} ${avg.unit}" else if (avg.unsupported) "N/A" else "—", style = Numeral.copy(fontSize = 22.sp, fontStyle = FontStyle.Normal), color = Color.White)
                Text(stringResource(R.string.dash_avg_fuel), style = MaterialTheme.typography.labelMedium, color = Color(0xFF9FB6D6))
            }
        }
    }
}

@Composable
private fun DtcCard(check: EngineCheck, modifier: Modifier, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val (color, icon, text) = when (check.state) {
        EngineCheckState.OK -> Triple(DashPalette.Green, Icons.Rounded.CheckCircle, stringResource(R.string.dash_dtc_none))
        EngineCheckState.FAULT -> Triple(
            DashPalette.Red, Icons.Rounded.WarningAmber,
            check.codeCount?.takeIf { it > 0 }?.let { stringResource(R.string.dash_dtc_count, it) } ?: stringResource(R.string.dash_dtc_mil_on),
        )
        EngineCheckState.UNKNOWN -> Triple(DashPalette.Amber, Icons.Rounded.HelpOutline, stringResource(R.string.dash_dtc_unknown))
    }
    JacCard(modifier, onClick = onClick, contentPadding = PaddingValues(14.dp), highlight = if (check.state == EngineCheckState.FAULT) color else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WarningAmber, null, tint = DashPalette.Red, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(stringResource(R.string.dash_dtc_title), style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color(0xFF9FB6D6))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(text, style = MaterialTheme.typography.titleSmall, color = color)
                check.lastScanMs?.let {
                    Text(
                        stringResource(R.string.dash_last_scan, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, locale).format(Date(it))),
                        style = MaterialTheme.typography.labelSmall, color = Color(0xFF9FB6D6),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- trend

@Composable
private fun TrendCard(state: DashboardUiState, palette: DashPalette, now: Long, onOpen: () -> Unit) {
    var range by rememberSaveable { mutableIntStateOf(1) }
    val windowMs = range * 60_000L
    val series = listOf(
        Triple(VehicleMetric.SPEED, DashPalette.Cyan, 0.0 to UnitConverter.speedScaleMax(state.units.speed)),
        Triple(VehicleMetric.RPM, DashPalette.Orange, 0.0 to 8000.0),
        Triple(VehicleMetric.COOLANT_TEMP, DashPalette.Green, 40.0 to 130.0),
    )
    JacCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ShowChart, null, tint = palette.accent, modifier = Modifier.size(22.dp).clickable(onClick = onOpen))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.dash_trend), style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f).clickable(onClick = onOpen))
            RangeSelector(range, palette) { range = it }
        }
        val pts = series.map { (m, c, scale) ->
            val raw = state.history.series[m].orEmpty().filter { now - it.tMs <= windowMs }
            val conv = if (m == VehicleMetric.SPEED) raw.map { HistoryPoint(it.tMs, UnitConverter.speed(it.value, state.units.speed)) } else raw
            Triple(conv, c, scale)
        }
        Spacer(Modifier.height(8.dp))
        Row {
            Column(Modifier.width(118.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                series.forEach { (m, c, _) ->
                    val d = state.snapshot.display(m, state.units, now)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text(stringResource(m.labelRes()), style = MaterialTheme.typography.labelSmall, color = Color(0xFFCFE3FF), maxLines = 1)
                            Text(d.text?.let { "$it ${d.unit}" } ?: "—", style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Ltr), color = Color.White)
                        }
                    }
                }
            }
            TrendChart(pts, now, windowMs, Modifier.weight(1f).height(110.dp))
        }
        val empty = pts.all { it.first.size < 2 }
        Text(
            stringResource(if (empty) R.string.dash_trend_empty else if (state.history.mode == DataMode.DEMO) R.string.dash_trend_demo else R.string.dash_trend_note),
            style = MaterialTheme.typography.labelSmall, color = Color(0xFF9FB6D6), modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun RangeSelector(selected: Int, palette: DashPalette, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(CircleShape).background(Color(0x22102A55)).border(1.dp, Color(0x336FA8FF), CircleShape)) {
        listOf(1, 5, 10).forEach { m ->
            val on = m == selected
            Text(
                stringResource(R.string.dash_minutes, m),
                style = MaterialTheme.typography.labelMedium,
                color = if (on) Color.White else Color(0xFF9FB6D6),
                modifier = Modifier.clip(CircleShape).background(if (on) palette.accent2.copy(alpha = 0.7f) else Color.Transparent)
                    .clickable { onSelect(m) }.padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/**
 * Three series on one chart, each scaled to its own range (0–100 % of scale), so lines with
 * different units can share a time axis without a misleading second value axis.
 * Time runs left → right also in RTL.
 */
@Composable
private fun TrendChart(series: List<Triple<List<HistoryPoint>, Color, Pair<Double, Double>>>, now: Long, windowMs: Long, modifier: Modifier) {
    Canvas(modifier) {
        val grid = Color(0x226FA8FF)
        for (i in 0..4) drawLine(grid, Offset(0f, size.height * i / 4), Offset(size.width, size.height * i / 4), 1f)
        for (i in 0..5) drawLine(grid, Offset(size.width * i / 5, 0f), Offset(size.width * i / 5, size.height), 1f)
        series.forEach { (pts, color, scale) ->
            if (pts.size < 2) return@forEach
            val path = Path()
            var started = false
            var lastT = 0L
            pts.forEach { p ->
                val x = (1f - (now - p.tMs).toFloat() / windowMs) * size.width
                val y = (size.height - ((p.value - scale.first) / (scale.second - scale.first)).coerceIn(0.0, 1.0) * size.height).toFloat()
                if (!started || p.tMs - lastT > 5_000) path.moveTo(x, y) else path.lineTo(x, y) // gaps stay gaps
                started = true
                lastT = p.tMs
            }
            drawPath(path, color.copy(alpha = 0.25f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
