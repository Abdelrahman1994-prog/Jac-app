package com.jacdrive.dashboard.cockpit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.core.model.CockpitThemeId
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DashboardWidget
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.GaugeLayout
import com.jacdrive.dashboard.R
import com.jacdrive.ui.cockpit.CockpitStyle
import com.jacdrive.ui.cockpit.CockpitStyles
import com.jacdrive.ui.cockpit.ThemeSwatch
import com.jacdrive.ui.cockpit.cockpitBackground
import com.jacdrive.ui.components.ImmersiveLandscapeEffect
import com.jacdrive.ui.components.layoutLabelRes
import com.jacdrive.ui.theme.JacFonts
import com.jacdrive.ui.theme.Readout
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CockpitScreen(
    onBack: () -> Unit,
    onOpenThemeStudio: () -> Unit,
    viewModel: CockpitViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ImmersiveLandscapeEffect(keepScreenOn = state.prefs.keepScreenOn)

    // Wall clock used for freshness: ticks even if the data stream stalls.
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(500)
        }
    }
    val style = remember(state.prefs) { CockpitStyles.resolve(state.prefs) }
    val warnings = CockpitWarnings.evaluate(state.snapshot, state.prefs.redlineRpm, nowMs)
    var showSheet by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().cockpitBackground(style, state.prefs.background)) {
        if (state.snapshot.mode == DataMode.DEMO) DemoWatermark(style)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
            CockpitTopBar(state, style, onBack, onCustomize = { showSheet = true })
            CockpitLayoutContent(state, style, nowMs, warnings, Modifier.weight(1f).fillMaxWidth())
        }
    }

    if (showSheet) {
        CockpitQuickSheet(
            state = state,
            onDismiss = { showSheet = false },
            onTheme = viewModel::setTheme,
            onLayout = viewModel::setLayout,
            onAnimations = viewModel::setAnimations,
            onMore = {
                showSheet = false
                onOpenThemeStudio()
            },
        )
    }
}

@Composable
private fun CockpitTopBar(state: CockpitUiState, style: CockpitStyle, onBack: () -> Unit, onCustomize: () -> Unit) {
    val time by produceState("") {
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        while (true) {
            value = fmt.format(Date())
            delay(5_000)
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(style, onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(com.jacdrive.ui.R.string.back), tint = style.text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            "JAC DRIVE",
            fontFamily = JacFonts.Display, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 0.2.em,
            color = style.text,
        )
        Spacer(Modifier.width(12.dp))
        ModePill(state, style)
        Spacer(Modifier.weight(1f))
        Text(time, style = Readout.Small, color = style.textDim)
        Spacer(Modifier.width(12.dp))
        RoundIcon(style, onCustomize) {
            Icon(Icons.Rounded.Palette, stringResource(R.string.cockpit_customize), tint = style.text, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun RoundIcon(style: CockpitStyle, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(style.panel).border(1.dp, style.panelBorder, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ModePill(state: CockpitUiState, style: CockpitStyle) {
    val demoColor = Color(0xFFB57BFF)
    val (text, color) = when (state.snapshot.mode) {
        DataMode.DEMO -> stringResource(R.string.cockpit_mode_demo) to demoColor
        DataMode.OFFLINE -> stringResource(R.string.cockpit_mode_offline) to style.textDim
        DataMode.LIVE -> {
            val c = state.snapshot.connection
            val name = if (state.visible(DashboardWidget.CONNECTION) && c is ConnectionState.Connected) {
                listOfNotNull(c.adapterName, c.protocol).joinToString(" · ")
            } else stringResource(R.string.cockpit_mode_live)
            name to style.ok
        }
    }
    Row(
        Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.5f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.snapshot.mode == DataMode.LIVE) {
            Icon(Icons.Rounded.Bluetooth, null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = Readout.Caption, color = color, maxLines = 1)
    }
}

@Composable
private fun DemoWatermark(style: CockpitStyle) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "DEMO",
            fontFamily = JacFonts.Display, fontWeight = FontWeight.Bold, fontSize = 180.sp, letterSpacing = 0.3.em,
            color = style.text.copy(alpha = 0.035f),
            modifier = Modifier.rotate(-14f),
        )
    }
}

@Composable
private fun CockpitQuickSheet(
    state: CockpitUiState,
    onDismiss: () -> Unit,
    onTheme: (CockpitThemeId) -> Unit,
    onLayout: (GaugeLayout) -> Unit,
    onAnimations: (Boolean) -> Unit,
    onMore: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.cockpit_theme), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CockpitStyles.all.forEach { s ->
                    ThemeSwatch(s, selected = state.prefs.themeId == s.id) { onTheme(s.id) }
                }
            }
            Text(stringResource(R.string.cockpit_layout), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GaugeLayout.entries.forEach { l ->
                    FilterChip(selected = state.prefs.layout == l, onClick = { onLayout(l) }, label = { Text(stringResource(l.layoutLabelRes())) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.cockpit_animations), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = state.prefs.animationsEnabled, onCheckedChange = onAnimations)
            }
            Text(
                stringResource(R.string.cockpit_more_customization),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onMore).padding(vertical = 8.dp),
            )
        }
    }
}
