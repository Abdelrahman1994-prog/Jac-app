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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Icon
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
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { HeaderBar(state, palette, onOpenDrawer, { onNavigate(DashDestination.SETTINGS) }, { onNavigate(DashDestination.ADAPTER) }, onSport) }
        item { CheckEngineBanner(check) { onNavigate(DashDestination.DTC) } }
        item { GaugesAndRoad(state, palette, now) }
        item { MetricGrid(state, palette, now) }
        item { TripBar(state, palette, now) }
        item { TrendCard(state, palette, now) { onNavigate(DashDestination.LIVE_DATA) } }
    }
}

private val Numeral = TextStyle(fontFamily = JacFonts.Display, fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr)
private val TextCyan = Color(0xFF6ECDFF)

/** Glass panel with a glowing gradient border; optional tinted fill for critical states. */
private fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape, edge: Color, tint: Color? = null, glow: Boolean = false): Modifier =
    this
        .then(if (glow) Modifier.shadow(14.dp, shape, ambientColor = edge, spotColor = edge) else Modifier)
        .clip(shape)
        .background(Brush.verticalGradient(listOf((tint ?: Color(0xFF102A54)).copy(alpha = if (tint != null) 0.55f else 0.40f), Color(0xE6040A18))))
        .border(1.5.dp, Brush.verticalGradient(listOf(edge.copy(alpha = 0.95f), edge.copy(alpha = 0.45f))), shape)

// ---------------------------------------------------------------- header

@Composable
private fun HeaderBar(
    state: DashboardUiState,
    palette: DashPalette,
    onMenu: () -> Unit,
    onSettings: () -> Unit,
    onAdapter: () -> Unit,
    onSport: (Boolean) -> Unit,
) {
    val s = state.snapshot
    val (dot, status) = when {
        s.mode == DataMode.DEMO -> DashPalette.Purple to stringResource(R.string.dash_hdr_demo)
        s.connection is ConnectionState.Connected -> DashPalette.Green to stringResource(R.string.dash_hdr_connected)
        s.connection is ConnectionState.Connecting -> DashPalette.Amber to stringResource(R.string.dash_hdr_connecting)
        s.connection is ConnectionState.Failed -> DashPalette.Amber to stringResource(R.string.dash_hdr_lost)
        else -> DashPalette.Red to stringResource(R.string.dash_hdr_disconnected)
    }
    val pulse by rememberInfiniteTransition(label = "conn").animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val shape = RoundedCornerShape(50)
    val edge = palette.accent
    Row(
        Modifier.fillMaxWidth().height(54.dp).glass(shape, edge.copy(alpha = 0.7f)).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Menu, stringResource(R.string.dash_menu), tint = Color.White) }
        Row(Modifier.weight(1.45f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onAdapter).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).drawBehind {
                drawCircle(Brush.radialGradient(listOf(dot.copy(alpha = 0.7f * pulse), Color.Transparent)), radius = size.minDimension * 1.2f)
                drawCircle(dot.copy(alpha = if (dot == DashPalette.Green || dot == DashPalette.Purple) 0.6f + 0.4f * pulse else 1f))
            })
            Spacer(Modifier.width(7.dp))
            Text(status.uppercase(), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.02.em), color = Color.White, maxLines = 2, lineHeight = 13.sp, overflow = TextOverflow.Ellipsis)
        }
        VDivider(edge)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(state.make.uppercase(), style = Numeral.copy(fontSize = 22.sp, fontStyle = FontStyle.Italic, letterSpacing = 0.04.em), color = Color.White)
                Spacer(Modifier.width(6.dp))
                Text(state.model.uppercase(), style = Numeral.copy(fontSize = 22.sp, fontStyle = FontStyle.Italic, letterSpacing = 0.04.em), color = DashPalette.Red)
            }
        }
        VDivider(edge)
        Row(Modifier.weight(0.75f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            val sportOn = state.cockpit.sportMode
            val sportTint by animateColorAsState(if (sportOn) DashPalette.Orange else Color(0xFF8AA0BF), label = "sport")
            IconButton(onClick = { onSport(!sportOn) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.SportsScore, stringResource(R.string.dash_sport), tint = sportTint)
            }
            IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) { Icon(Icons.Rounded.Settings, stringResource(R.string.dash_settings), tint = Color.White) }
        }
    }
}

@Composable
private fun VDivider(color: Color) = Box(Modifier.width(1.dp).height(26.dp).background(color.copy(alpha = 0.5f)))

/** Hexagonal glowing banner: red fault, green clean recent scan, amber not checked. */
@Composable
private fun CheckEngineBanner(check: EngineCheck, onClick: () -> Unit) {
    val target = when (check.state) {
        EngineCheckState.FAULT -> DashPalette.Red
        EngineCheckState.OK -> DashPalette.Green
        EngineCheckState.UNKNOWN -> DashPalette.Amber
    }
    val color by animateColorAsState(target, tween(500), label = "banner")
    val detail = when (check.state) {
        EngineCheckState.FAULT -> check.codeCount?.takeIf { it > 0 }?.let { stringResource(R.string.dash_dtc_count, it) } ?: stringResource(R.string.dash_banner_fault)
        EngineCheckState.OK -> stringResource(R.string.dash_banner_ok)
        EngineCheckState.UNKNOWN -> stringResource(R.string.dash_banner_unknown)
    }
    val glow by rememberInfiniteTransition(label = "bannerGlow").animateFloat(0.55f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "g")
    val g = if (check.state == EngineCheckState.FAULT) glow else 0.7f
    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth().height(48.dp)
                .drawBehind {
                    val cut = size.height * 0.45f
                    val hex = Path().apply {
                        moveTo(cut, 0f); lineTo(size.width - cut, 0f); lineTo(size.width, size.height / 2)
                        lineTo(size.width - cut, size.height); lineTo(cut, size.height); lineTo(0f, size.height / 2); close()
                    }
                    drawPath(hex, color.copy(alpha = 0.22f * g), style = Stroke(10.dp.toPx(), join = StrokeJoin.Round))
                    drawPath(hex, Brush.horizontalGradient(listOf(color.copy(alpha = 0.18f), Color(0xFF14040A).copy(alpha = 0.85f), color.copy(alpha = 0.18f))))
                    drawPath(hex, color, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
                }
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(if (check.state == EngineCheckState.OK) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.dash_check_engine).uppercase(), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = color, maxLines = 1)
            Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(22.dp).background(color.copy(alpha = 0.6f)))
            Text(detail.uppercase(), style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

// ---------------------------------------------------------------- gauges + road

@Composable
private fun GaugesAndRoad(state: DashboardUiState, palette: DashPalette, now: Long) {
    val s = state.snapshot
    val speed = s.display(VehicleMetric.SPEED, state.units, now)
    val rpm = s.display(VehicleMetric.RPM, state.units, now)
    val animate = state.cockpit.animationsEnabled
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gauge = minOf(maxWidth * 0.48f, 300.dp)
            val roadH = gauge * 0.62f
            Box(Modifier.fillMaxWidth().height(gauge + roadH * 0.62f)) {
                HeroScene(
                    speedKmh = (if (s.mode != DataMode.OFFLINE && speed.freshness == Freshness.LIVE) speed.raw ?: 0.0 else 0.0).toFloat(),
                    palette = palette, animate = animate,
                    modifier = Modifier.fillMaxWidth().height(roadH).align(Alignment.BottomCenter),
                )
                val speedMax = UnitConverter.speedScaleMax(state.units.speed).toFloat()
                CircularGauge(
                    value = speed.raw?.let { UnitConverter.speed(it, state.units.speed).toFloat() }, min = 0f, max = speedMax,
                    style = palette.speedStyle, modifier = Modifier.size(gauge).align(Alignment.TopStart),
                    majorStep = if (speedMax > 200) 30f else 20f, minorDivisions = 3, animate = animate,
                    dimmed = speed.freshness == Freshness.STALE,
                ) { GaugeCenter(speed.text ?: if (speed.unsupported) "N/A" else "—", speed.unit, palette.accent, gauge.value) }
                CircularGauge(
                    value = rpm.raw?.toFloat(), min = 0f, max = 8000f,
                    style = palette.rpmStyle, modifier = Modifier.size(gauge).align(Alignment.TopEnd),
                    majorStep = 1000f, minorDivisions = 4, labelDivisor = 1000f, dangerFrom = state.cockpit.redlineRpm.toFloat(),
                    animate = animate, dimmed = rpm.freshness == Freshness.STALE,
                ) { GaugeCenter(rpm.raw?.let { Num.one(it / 1000) } ?: if (rpm.unsupported) "N/A" else "—", stringResource(R.string.dash_rpm_unit), palette.accent, gauge.value) }
            }
        }
    }
}

@Composable
private fun GaugeCenter(value: String, unit: String, unitColor: Color, gaugeDp: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Numeral.copy(fontSize = (gaugeDp * 0.20f).sp, lineHeight = (gaugeDp * 0.21f).sp, fontStyle = FontStyle.Italic), color = Color.White)
        Text(unit, style = MaterialTheme.typography.labelLarge.copy(fontSize = (gaugeDp * 0.058f).sp, fontWeight = FontWeight.Bold, textDirection = TextDirection.Ltr), color = unitColor)
    }
}

// ---------------------------------------------------------------- metric cards

private data class MetricSpec(val metric: VehicleMetric, val icon: ImageVector)

@Composable
private fun MetricGrid(state: DashboardUiState, palette: DashPalette, now: Long) {
    val specs = listOf(
        MetricSpec(VehicleMetric.COOLANT_TEMP, Icons.Rounded.DeviceThermostat),
        MetricSpec(VehicleMetric.BATTERY_VOLTAGE, Icons.Rounded.BatteryChargingFull),
        MetricSpec(VehicleMetric.FUEL_LEVEL, Icons.Rounded.LocalGasStation),
        MetricSpec(VehicleMetric.ENGINE_LOAD, EngineIcon),
        MetricSpec(VehicleMetric.THROTTLE_POSITION, Icons.Rounded.Tune),
        MetricSpec(VehicleMetric.INTAKE_AIR_TEMP, Icons.Rounded.Air),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        specs.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { MetricCard(it, state, palette, now, Modifier.weight(1f)) }
            }
        }
    }
}

/** Red = critical, orange = warning, null = normal. Uses the user's alert thresholds where they exist. */
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
    val critical = warn == DashPalette.Red
    val color by animateColorAsState(warn ?: palette.accent, tween(400), label = "metricColor")
    val fraction by animateFloatAsState(barFraction(spec.metric, d.raw) ?: 0f, tween(500), label = "metricBar")
    val stale = d.freshness == Freshness.STALE
    val value = when {
        d.unsupported -> "N/A"
        d.text == null -> "—"
        else -> d.text
    }
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.glass(shape, color, tint = if (critical) Color(0xFF8C1018) else null, glow = warn != null).padding(horizontal = 10.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(spec.icon, null, tint = color, modifier = Modifier.size(30.dp))
            Spacer(Modifier.weight(1f))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, style = Numeral.copy(fontSize = 24.sp), color = if (critical) DashPalette.Red else if (stale) Color(0xFF9FB6D6) else Color.White, maxLines = 1)
                    if (d.text != null) Text(d.unit, style = Numeral.copy(fontSize = 13.sp), color = if (critical) DashPalette.Red else Color.White, modifier = Modifier.padding(start = 1.dp, bottom = 3.dp))
                }
            }
        }
        Text(
            stringResource(spec.metric.labelRes()), style = MaterialTheme.typography.labelMedium,
            color = if (critical) Color.White else Color(0xFFCFE3FF),
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color(0x33789FD2))) {
            if (d.raw != null) {
                Box(
                    Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(5.dp)
                        .shadow(6.dp, CircleShape, ambientColor = color, spotColor = color)
                        .clip(CircleShape).background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- trip bar

@Composable
private fun TripBar(state: DashboardUiState, palette: DashPalette, now: Long) {
    val trip = state.snapshot.display(VehicleMetric.TRIP_DISTANCE, state.units, now)
    val avg = state.snapshot.display(VehicleMetric.AVG_FUEL_CONSUMPTION, state.units, now)
    fun show(d: com.jacdrive.dashboard.cockpit.MetricDisplay) = if (d.text != null) "${d.text} ${d.unit}" else if (d.unsupported) "N/A" else "—"
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.fillMaxWidth().height(58.dp).glass(shape, palette.accent, glow = true).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Route, null, tint = palette.accent, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        VDivider(palette.accent)
        TripPair(stringResource(R.string.dash_trip_short), show(trip), Modifier.weight(1f))
        VDivider(palette.accent)
        TripPair(stringResource(R.string.dash_avg_short), show(avg), Modifier.weight(1.2f))
    }
}

@Composable
private fun TripPair(label: String, value: String, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = TextCyan, modifier = Modifier.padding(bottom = 2.dp))
        Spacer(Modifier.width(6.dp))
        Text(value, style = Numeral.copy(fontSize = 20.sp), color = Color.White, maxLines = 1)
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
