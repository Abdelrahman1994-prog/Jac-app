package com.jacdrive.dashboard.cockpit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jacdrive.core.model.DashboardWidget
import com.jacdrive.core.model.GaugeLayout
import com.jacdrive.ui.cockpit.CockpitStyle

@Composable
fun CockpitLayoutContent(
    state: CockpitUiState,
    style: CockpitStyle,
    nowMs: Long,
    warnings: Map<Telltale, Severity>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val portrait = maxHeight > maxWidth
        when {
            portrait -> PortraitLayout(state, style, nowMs, warnings)
            state.prefs.layout == GaugeLayout.DUAL_DIAL -> DualDialLayout(state, style, nowMs, warnings)
            state.prefs.layout == GaugeLayout.SPORT_CENTER -> SportCenterLayout(state, style, nowMs, warnings)
            state.prefs.layout == GaugeLayout.HORIZON -> HorizonLayout(state, style, nowMs, warnings)
            else -> DigitalLayout(state, style, nowMs, warnings)
        }
    }
}

@Composable
private fun DualDialLayout(state: CockpitUiState, style: CockpitStyle, nowMs: Long, warnings: Map<Telltale, Severity>) {
    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (state.visible(DashboardWidget.SPEED)) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                SpeedDial(state, style, nowMs, Modifier.padding(4.dp))
            }
        }
        Column(
            Modifier.weight(0.85f).fillMaxHeight().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TelltaleRow(warnings, style)
            TileGrid(state.tileWidgets, 2, state, style, nowMs, warnings, Modifier.weight(1f).fillMaxWidth())
        }
        if (state.visible(DashboardWidget.RPM)) {
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                RpmDial(state, style, nowMs, Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
private fun SportCenterLayout(state: CockpitUiState, style: CockpitStyle, nowMs: Long, warnings: Map<Telltale, Severity>) {
    val tiles = state.tileWidgets
    val half = (tiles.size + 1) / 2
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TileGrid(tiles.take(half), 1, state, style, nowMs, warnings, Modifier.weight(0.7f).fillMaxHeight())
        Box(Modifier.weight(1.3f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            when {
                state.visible(DashboardWidget.RPM) ->
                    RpmDial(state, style, nowMs, Modifier.padding(6.dp), showSpeedInCenter = state.visible(DashboardWidget.SPEED))
                state.visible(DashboardWidget.SPEED) -> SpeedDial(state, style, nowMs, Modifier.padding(6.dp))
            }
            TelltaleRow(warnings, style, Modifier.align(Alignment.BottomCenter))
        }
        TileGrid(tiles.drop(half), 1, state, style, nowMs, warnings, Modifier.weight(0.7f).fillMaxHeight())
    }
}

@Composable
private fun DigitalLayout(state: CockpitUiState, style: CockpitStyle, nowMs: Long, warnings: Map<Telltale, Severity>) {
    val tiles = state.tileWidgets
    val side = tiles.take(4)
    val bottom = tiles.drop(4)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.visible(DashboardWidget.RPM)) RpmBar(state, style, nowMs, Modifier.fillMaxWidth())
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TileGrid(side.take(2), 1, state, style, nowMs, warnings, Modifier.weight(0.6f).fillMaxHeight())
            Column(Modifier.weight(1.2f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.visible(DashboardWidget.SPEED)) HeroSpeed(state, style, nowMs, Modifier.weight(1f).fillMaxWidth())
                else Box(Modifier.weight(1f))
                TelltaleRow(warnings, style)
            }
            TileGrid(side.drop(2), 1, state, style, nowMs, warnings, Modifier.weight(0.6f).fillMaxHeight())
        }
        if (bottom.isNotEmpty()) {
            TileGrid(bottom, bottom.size, state, style, nowMs, warnings, Modifier.fillMaxWidth().weight(0.32f))
        }
    }
}

/** Fallback when the device is held upright (e.g. rotation locked). */
@Composable
private fun PortraitLayout(state: CockpitUiState, style: CockpitStyle, nowMs: Long, warnings: Map<Telltale, Severity>) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (state.visible(DashboardWidget.RPM)) {
                RpmDial(state, style, nowMs, showSpeedInCenter = state.visible(DashboardWidget.SPEED))
            } else if (state.visible(DashboardWidget.SPEED)) {
                SpeedDial(state, style, nowMs)
            }
        }
        TelltaleRow(warnings, style, Modifier.align(Alignment.CenterHorizontally))
        TileGrid(state.tileWidgets, 2, state, style, nowMs, warnings, Modifier.fillMaxWidth().weight(0.9f))
    }
}
