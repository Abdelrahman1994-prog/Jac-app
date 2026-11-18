package com.jacdrive.settings

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.core.model.AlertSeverity
import com.jacdrive.core.model.AlertType
import com.jacdrive.core.model.VoiceLanguage
import com.jacdrive.obd.alerts.VoiceStatus
import com.jacdrive.ui.components.InfoNote
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import com.jacdrive.ui.theme.JacTheme
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun AlertsScreen(onBack: () -> Unit, viewModel: AlertsViewModel = hiltViewModel()) {
    val p by viewModel.prefs.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val voice by viewModel.voiceStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.alerts_title), subtitle = stringResource(R.string.alerts_sub), onBack = onBack) }

        item { SectionHeader(stringResource(R.string.alerts_voice)) }
        item {
            JacCard {
                SwitchRow(stringResource(R.string.alerts_voice_on), p.voiceEnabled, { v -> viewModel.update { it.copy(voiceEnabled = v) } }, subtitle = stringResource(R.string.alerts_voice_on_sub), icon = Icons.Rounded.RecordVoiceOver)
                RowDivider()
                Text(stringResource(R.string.alerts_voice_lang), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                SegmentedChoice(VoiceLanguage.entries, p.voiceLanguage, {
                    when (it) {
                        VoiceLanguage.APP -> stringResource(R.string.alerts_lang_app)
                        VoiceLanguage.ARABIC -> "العربية"
                        VoiceLanguage.ENGLISH -> "English"
                    }
                }) { l -> viewModel.update { it.copy(voiceLanguage = l) } }
                when (voice) {
                    VoiceStatus.LANGUAGE_MISSING -> {
                        Text(stringResource(R.string.alerts_voice_missing), style = MaterialTheme.typography.bodySmall, color = JacTheme.status.warning)
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }) { Text(stringResource(R.string.alerts_voice_settings)) }
                    }
                    VoiceStatus.ENGINE_UNAVAILABLE -> Text(stringResource(R.string.alerts_voice_unavailable), style = MaterialTheme.typography.bodySmall, color = JacTheme.status.warning)
                    else -> Unit
                }
                OutlinedButton(onClick = { viewModel.testVoice(); viewModel.refreshVoice() }, modifier = Modifier.padding(top = 6.dp)) { Text(stringResource(R.string.alerts_test)) }
                RowDivider()
                SwitchRow(stringResource(R.string.alerts_notifications), p.notificationsEnabled, { v -> viewModel.update { it.copy(notificationsEnabled = v) } })
            }
        }
        item { InfoNote(stringResource(R.string.alerts_android_auto_note)) }

        item { SectionHeader(stringResource(R.string.alerts_types)) }
        item {
            JacCard {
                AlertToggle(viewModel, p.isEnabled(AlertType.COOLANT_HIGH), AlertType.COOLANT_HIGH, R.string.alerts_coolant)
                if (p.isEnabled(AlertType.COOLANT_HIGH)) {
                    ThresholdSlider(stringResource(R.string.alerts_threshold_c, p.coolantThresholdC), p.coolantThresholdC.toFloat(), 95f..120f, 24) { v -> viewModel.update { it.copy(coolantThresholdC = v.roundToInt()) } }
                }
                RowDivider()
                AlertToggle(viewModel, p.isEnabled(AlertType.BATTERY_LOW), AlertType.BATTERY_LOW, R.string.alerts_battery_low)
                if (p.isEnabled(AlertType.BATTERY_LOW)) {
                    ThresholdSlider(stringResource(R.string.alerts_threshold_v, String.format(Locale.US, "%.1f", p.batteryLowRunningV)), p.batteryLowRunningV.toFloat(), 12f..13.8f, 17) { v ->
                        viewModel.update { it.copy(batteryLowRunningV = (v * 10).roundToInt() / 10.0) }
                    }
                }
                RowDivider()
                AlertToggle(viewModel, p.isEnabled(AlertType.BATTERY_HIGH), AlertType.BATTERY_HIGH, R.string.alerts_battery_high)
                if (p.isEnabled(AlertType.BATTERY_HIGH)) {
                    ThresholdSlider(stringResource(R.string.alerts_threshold_v, String.format(Locale.US, "%.1f", p.batteryHighV)), p.batteryHighV.toFloat(), 14.6f..16f, 13) { v ->
                        viewModel.update { it.copy(batteryHighV = (v * 10).roundToInt() / 10.0) }
                    }
                }
                RowDivider()
                AlertToggle(viewModel, p.isEnabled(AlertType.HIGH_RPM), AlertType.HIGH_RPM, R.string.alerts_rpm)
                if (p.isEnabled(AlertType.HIGH_RPM)) {
                    ThresholdSlider(stringResource(R.string.alerts_threshold_rpm, p.rpmThreshold), p.rpmThreshold.toFloat(), 3000f..7000f, 15) { v -> viewModel.update { it.copy(rpmThreshold = (v / 250).roundToInt() * 250) } }
                }
                RowDivider()
                AlertToggle(viewModel, p.isEnabled(AlertType.CHECK_ENGINE), AlertType.CHECK_ENGINE, R.string.alerts_mil)
                RowDivider()
                AlertToggle(viewModel, p.isEnabled(AlertType.OBD_DISCONNECTED), AlertType.OBD_DISCONNECTED, R.string.alerts_disconnect)
                RowDivider()
                AlertToggle(viewModel, p.isEnabled(AlertType.MAINTENANCE_DUE), AlertType.MAINTENANCE_DUE, R.string.alerts_maintenance)
                RowDivider()
                ThresholdSlider(stringResource(R.string.alerts_cooldown, p.cooldownMinutes), p.cooldownMinutes.toFloat(), 1f..30f, 28) { v -> viewModel.update { it.copy(cooldownMinutes = v.roundToInt()) } }
            }
        }
        item { InfoNote(stringResource(R.string.alerts_behaviour_note)) }

        item {
            SectionHeader(stringResource(R.string.alerts_history)) {
                if (history.isNotEmpty()) TextButton(onClick = viewModel::clearHistory) { Text(stringResource(R.string.alerts_clear)) }
            }
        }
        if (history.isEmpty()) {
            item { Text(stringResource(R.string.alerts_history_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(history.take(200), key = { it.id }) { e ->
                val color = when (e.severity) {
                    AlertSeverity.CRITICAL.name -> JacTheme.status.critical
                    AlertSeverity.WARNING.name -> JacTheme.status.warning
                    else -> JacTheme.status.info
                }
                JacCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(viewModel.message(e), style = MaterialTheme.typography.bodyMedium, color = color)
                            Text(
                                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(e.timestampMs)) +
                                    if (e.spoken) " · 🔊" else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertToggle(vm: AlertsViewModel, enabled: Boolean, type: AlertType, label: Int) {
    SwitchRow(stringResource(label), enabled, { vm.toggle(type, it) })
}

@Composable
private fun ThresholdSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onDone: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { onDone(v) }, valueRange = range, steps = steps)
    }
    Spacer(Modifier.width(0.dp))
}
