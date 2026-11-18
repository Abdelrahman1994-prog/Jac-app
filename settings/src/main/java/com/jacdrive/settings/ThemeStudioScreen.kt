package com.jacdrive.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.core.model.CockpitPreferences
import com.jacdrive.core.model.DashboardBackground
import com.jacdrive.core.model.DashboardWidget
import com.jacdrive.core.model.GaugeLayout
import com.jacdrive.ui.cockpit.CockpitStyles
import com.jacdrive.ui.cockpit.ThemeSwatch
import com.jacdrive.ui.cockpit.cockpitBackground
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import com.jacdrive.ui.components.labelRes
import com.jacdrive.ui.components.layoutLabelRes
import com.jacdrive.ui.gauge.CircularGauge
import com.jacdrive.ui.theme.Readout
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeStudioScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs = state.cockpit

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.studio_title), subtitle = stringResource(R.string.studio_sub), onBack = onBack) }
        item { CockpitPreview(prefs) }

        item { SectionHeader(stringResource(R.string.studio_theme)) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CockpitStyles.all.forEach { s ->
                    ThemeSwatch(s, selected = prefs.themeId == s.id) { viewModel.setCockpitTheme(s.id) }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.studio_accent)) }
        item {
            JacCard {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape)
                            .border(2.dp, if (prefs.accentOverride == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { viewModel.setAccent(null) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Restore, stringResource(R.string.studio_accent_default), modifier = Modifier.size(20.dp)) }
                    CockpitStyles.accentPresets.forEach { argb ->
                        val selected = prefs.accentOverride == argb
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color(argb))
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { viewModel.setAccent(argb) },
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.Black, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }

        item { SectionHeader(stringResource(R.string.studio_layout)) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GaugeLayout.entries.forEach { l ->
                    FilterChip(prefs.layout == l, { viewModel.setLayout(l) }, label = { Text(stringResource(l.layoutLabelRes())) })
                }
            }
        }

        item { SectionHeader(stringResource(R.string.studio_background)) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashboardBackground.entries.forEach { b ->
                    FilterChip(prefs.background == b, { viewModel.setBackground(b) }, label = { Text(stringResource(b.labelRes())) })
                }
            }
        }

        item { SectionHeader(stringResource(R.string.studio_behaviour)) }
        item {
            JacCard {
                SwitchRow(stringResource(R.string.studio_animations), prefs.animationsEnabled, viewModel::setAnimations, subtitle = stringResource(R.string.studio_animations_sub))
                RowDivider()
                SwitchRow(stringResource(R.string.studio_keep_on), prefs.keepScreenOn, viewModel::setKeepScreenOn)
                RowDivider()
                var redline by remember(prefs.redlineRpm) { mutableFloatStateOf(prefs.redlineRpm.toFloat()) }
                SettingRow(stringResource(R.string.studio_redline), subtitle = stringResource(R.string.studio_redline_value, redline.roundToInt()))
                Slider(
                    value = redline,
                    onValueChange = { redline = it },
                    onValueChangeFinished = { viewModel.setRedline(((redline / 250).roundToInt() * 250)) },
                    valueRange = 4000f..8000f,
                    steps = 15,
                )
                Text(stringResource(R.string.studio_redline_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item { SectionHeader(stringResource(R.string.studio_widgets)) }
        item {
            JacCard {
                DashboardWidget.entries.forEachIndexed { i, w ->
                    SwitchRow(stringResource(w.labelRes()), w in prefs.visibleWidgets, { viewModel.setWidgetVisible(w, it) })
                    if (i < DashboardWidget.entries.lastIndex) RowDivider()
                }
            }
        }
    }
}

/** Static preview of the chosen look. The needle positions are illustrative, not vehicle data. */
@Composable
private fun CockpitPreview(prefs: CockpitPreferences) {
    val style = remember(prefs) { CockpitStyles.resolve(prefs) }
    Box(
        Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(22.dp))
            .cockpitBackground(style, prefs.background),
    ) {
        Row(Modifier.fillMaxSize().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            CircularGauge(value = 96f, min = 0f, max = 240f, style = style, majorStep = 40f, minorDivisions = 2, animate = prefs.animationsEnabled) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("96", style = Readout.Medium, color = style.text)
                    Text("km/h", style = Readout.Unit, color = style.accent)
                }
            }
            CircularGauge(
                value = 3200f, min = 0f, max = 8000f, style = style, majorStep = 2000f, minorDivisions = 4, labelDivisor = 1000f,
                dangerFrom = prefs.redlineRpm.toFloat(), animate = prefs.animationsEnabled,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("3.2", style = Readout.Medium, color = style.text)
                    Text("x1000", style = Readout.Unit, color = style.accent)
                }
            }
        }
        Text(
            stringResource(R.string.studio_preview),
            style = Readout.Caption,
            color = style.textDim,
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        )
    }
}
