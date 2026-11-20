package com.jacdrive.dashboard.dash

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.dashboard.R
import com.jacdrive.dashboard.cockpit.display
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.labelRes
import com.jacdrive.ui.theme.JacFonts
import kotlinx.coroutines.delay

/** Every metric the app knows, with where it came from and how fresh it is. Nothing is filled in. */
@Composable
fun LiveDataScreen(onOpenAdapter: () -> Unit, viewModel: DashboardViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) { while (true) { value = System.currentTimeMillis(); delay(1_000) } }
    val palette = if (state.cockpit.sportMode) DashPalette.Sport else DashPalette.Normal
    val s = state.snapshot
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(stringResource(R.string.live_title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(
                stringResource(
                    when {
                        s.mode == DataMode.DEMO -> R.string.live_sub_demo
                        s.connection is ConnectionState.Connected -> R.string.live_sub_live
                        else -> R.string.live_sub_offline
                    },
                ),
                style = MaterialTheme.typography.bodyMedium, color = Color(0xFF9FB6D6),
            )
            if (s.mode == DataMode.OFFLINE) {
                OutlinedButton(onClick = onOpenAdapter, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.live_connect)) }
            }
        }
        items(VehicleMetric.entries.toList(), key = { it.name }) { m ->
            val d = s.display(m, state.units, now)
            val lv = s[m]
            val history = state.history.series[m].orEmpty().filter { now - it.tMs <= 120_000 }
            val (dot, fresh) = when {
                d.unsupported -> Color(0xFF5B6B85) to stringResource(R.string.live_unsupported)
                d.freshness == Freshness.LIVE -> DashPalette.Green to stringResource(R.string.live_fresh)
                d.freshness == Freshness.STALE -> DashPalette.Amber to stringResource(R.string.live_stale)
                else -> Color(0xFF5B6B85) to stringResource(R.string.live_missing)
            }
            JacCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(m.labelRes()), style = MaterialTheme.typography.titleSmall, color = Color.White)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                            Spacer(Modifier.width(5.dp))
                            val src = lv?.takeIf { !d.unsupported }?.source?.let { " · " + stringResource(it.labelRes()) } ?: ""
                            Text(fresh + src, style = MaterialTheme.typography.labelSmall, color = Color(0xFF9FB6D6))
                        }
                    }
                    if (history.size >= 2) {
                        Sparkline(history.map { it.tMs to it.value }, now, palette.accent, Modifier.width(84.dp).height(30.dp))
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        when { d.unsupported -> "N/A"; d.text == null -> "—"; else -> "${d.text} ${d.unit}" },
                        style = TextStyle(fontFamily = JacFonts.Display, fontWeight = FontWeight.Bold, fontSize = 20.sp, textDirection = TextDirection.Ltr),
                        color = if (d.freshness == Freshness.LIVE) Color.White else Color(0xFF9FB6D6),
                    )
                }
            }
        }
    }
}

private fun DataSource.labelRes(): Int = when (this) {
    DataSource.ECU -> R.string.live_src_ecu
    DataSource.ADAPTER -> R.string.live_src_adapter
    DataSource.CALCULATED -> R.string.live_src_calc
    DataSource.PHONE_SENSOR -> R.string.live_src_phone
    DataSource.SIMULATED -> R.string.live_src_sim
}

@Composable
private fun Sparkline(points: List<Pair<Long, Double>>, now: Long, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val lo = points.minOf { it.second }
        val hi = points.maxOf { it.second }.let { if (it - lo < 1e-6) lo + 1 else it }
        val path = Path()
        points.forEachIndexed { i, (t, v) ->
            val x = (1f - (now - t) / 120_000f) * size.width
            val y = (size.height - (v - lo) / (hi - lo) * size.height).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 2.5.dp.toPx(), Offset(size.width, (size.height - (points.last().second - lo) / (hi - lo) * size.height).toFloat()))
    }
}
