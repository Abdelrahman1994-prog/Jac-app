package com.jacdrive.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jacdrive.core.model.AppSettings
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.model.ThemeMode
import com.jacdrive.ui.components.JacCard
import com.jacdrive.ui.components.ScreenHeader
import com.jacdrive.ui.components.SectionHeader
import kotlin.math.roundToLong

private enum class Language(val tag: String) { SYSTEM(""), ENGLISH("en"), ARABIC("ar") }

@Composable
fun SettingsScreen(
    onBack: (() -> Unit)?,
    onOpenThemeStudio: () -> Unit,
    onOpenAdapter: () -> Unit,
    onOpenAlerts: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenTrips: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val app = state.app
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val dataMessage by viewModel.dataMessage.collectAsStateWithLifecycle()
    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(viewModel::exportBackup) }
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::checkRestore) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.settings_title), onBack = onBack) }

        item { SectionHeader(stringResource(R.string.settings_language)) }
        item {
            JacCard {
                var language by remember {
                    val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
                    mutableStateOf(Language.entries.firstOrNull { it.tag.isNotEmpty() && tags.startsWith(it.tag) } ?: Language.SYSTEM)
                }
                SegmentedChoice(
                    options = Language.entries,
                    selected = language,
                    label = {
                        when (it) {
                            Language.SYSTEM -> stringResource(R.string.settings_language_system)
                            Language.ENGLISH -> "English"
                            Language.ARABIC -> "العربية"
                        }
                    },
                ) {
                    language = it
                    AppCompatDelegate.setApplicationLocales(
                        if (it == Language.SYSTEM) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(it.tag),
                    )
                }
            }
        }

        item { SectionHeader(stringResource(R.string.settings_appearance)) }
        item {
            JacCard {
                Text(stringResource(R.string.settings_theme_mode), style = MaterialTheme.typography.labelLarge)
                SegmentedChoice(ThemeMode.entries, app.themeMode, {
                    stringResource(
                        when (it) {
                            ThemeMode.DARK -> R.string.settings_dark
                            ThemeMode.LIGHT -> R.string.settings_light
                            ThemeMode.SYSTEM -> R.string.settings_system
                        },
                    )
                }, viewModel::setThemeMode)
                RowDivider()
                SettingRow(
                    stringResource(R.string.settings_cockpit_themes),
                    subtitle = stringResource(R.string.settings_cockpit_themes_sub),
                    icon = Icons.Rounded.Palette,
                    onClick = onOpenThemeStudio,
                )
            }
        }

        item { SectionHeader(stringResource(R.string.settings_units)) }
        item {
            JacCard {
                Text(stringResource(R.string.settings_speed), style = MaterialTheme.typography.labelLarge)
                SegmentedChoice(SpeedUnit.entries, app.speedUnit, { if (it == SpeedUnit.KMH) "km/h" else "mph" }, viewModel::setSpeedUnit)
                Text(stringResource(R.string.settings_temperature), style = MaterialTheme.typography.labelLarge)
                SegmentedChoice(TemperatureUnit.entries, app.temperatureUnit, { if (it == TemperatureUnit.CELSIUS) "°C" else "°F" }, viewModel::setTemperatureUnit)
            }
        }

        item { SectionHeader(stringResource(R.string.settings_obd)) }
        item {
            JacCard {
                SettingRow(
                    stringResource(R.string.settings_adapter),
                    subtitle = app.adapterName?.let { stringResource(R.string.adapter_saved, it) } ?: stringResource(R.string.settings_adapter_sub),
                    icon = Icons.Rounded.Bluetooth,
                    onClick = onOpenAdapter,
                )
                RowDivider()
                PollingIntervalSetting(app.pollingIntervalMs, viewModel::setPollingInterval)
                RowDivider()
                SwitchRow(
                    stringResource(R.string.settings_demo),
                    app.demoModeEnabled,
                    viewModel::setDemoMode,
                    subtitle = stringResource(R.string.settings_demo_sub),
                    icon = Icons.Rounded.Science,
                )
            }
        }

        item { SectionHeader(stringResource(R.string.settings_alerts)) }
        item {
            JacCard {
                SettingRow(
                    stringResource(R.string.settings_alerts_voice),
                    subtitle = stringResource(R.string.settings_alerts_voice_sub),
                    icon = Icons.Rounded.NotificationsActive,
                    onClick = onOpenAlerts,
                )
            }
        }

        item { SectionHeader(stringResource(R.string.settings_data)) }
        item {
            JacCard {
                SettingRow(
                    stringResource(R.string.settings_backup), subtitle = stringResource(R.string.settings_backup_sub), icon = Icons.Rounded.Backup,
                    onClick = { backupLauncher.launch("jacdrive-backup-${java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())}.zip") },
                )
                RowDivider()
                SettingRow(
                    stringResource(R.string.settings_restore), subtitle = stringResource(R.string.settings_restore_sub), icon = Icons.Rounded.SettingsBackupRestore,
                    onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                )
                RowDivider()
                SettingRow(stringResource(R.string.settings_export), subtitle = stringResource(R.string.settings_export_sub), icon = Icons.Rounded.Description, onClick = onOpenDiagnostics)
                RowDivider()
                SettingRow(stringResource(R.string.settings_export_trips), subtitle = stringResource(R.string.settings_export_trips_sub), icon = Icons.Rounded.Route, onClick = onOpenTrips)
            }
        }

        item { SectionHeader(stringResource(R.string.settings_about)) }
        item {
            val context = LocalContext.current
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "-"
            }
            JacCard {
                SettingRow(stringResource(R.string.settings_version), subtitle = version, icon = Icons.Rounded.Info)
                RowDivider()
                SettingRow(stringResource(R.string.settings_safety), icon = Icons.Rounded.Shield, onClick = { dialog = "safety" })
                RowDivider()
                SettingRow(stringResource(R.string.settings_licenses), icon = Icons.Rounded.Gavel, onClick = { dialog = "licenses" })
            }
        }
    }

    when (val m = dataMessage) {
        SettingsViewModel.DataMessage.BackupSaved -> InfoDialog(stringResource(R.string.settings_backup), stringResource(R.string.data_backup_saved)) { viewModel.dismissData() }
        is SettingsViewModel.DataMessage.Failed -> InfoDialog(stringResource(R.string.settings_restore), stringResource(R.string.data_failed, m.reason)) { viewModel.dismissData() }
        is SettingsViewModel.DataMessage.RestoreReady -> AlertDialog(
            onDismissRequest = { viewModel.dismissData() },
            title = { Text(stringResource(R.string.settings_restore)) },
            text = {
                Text(stringResource(R.string.data_restore_confirm, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(m.info.createdMs))))
            },
            confirmButton = { TextButton(onClick = { viewModel.restore(m.info) }) { Text(stringResource(R.string.data_restore_now)) } },
            dismissButton = { TextButton(onClick = { viewModel.dismissData() }) { Text(stringResource(R.string.data_cancel)) } },
        )
        null -> Unit
    }

    when (dialog) {
        "safety" -> InfoDialog(stringResource(R.string.settings_safety), stringResource(R.string.settings_safety_body)) { dialog = null }
        "licenses" -> InfoDialog(stringResource(R.string.settings_licenses), stringResource(R.string.settings_licenses_body)) { dialog = null }
    }
}

@Composable
private fun PollingIntervalSetting(current: Long, onChange: (Long) -> Unit) {
    var value by remember(current) { mutableFloatStateOf(current.toFloat()) }
    SettingRow(
        stringResource(R.string.settings_polling),
        subtitle = stringResource(R.string.settings_polling_value, value.roundToLong()),
    )
    Slider(
        value = value,
        onValueChange = { value = it },
        onValueChangeFinished = { onChange((value / 50).roundToLong() * 50) },
        valueRange = AppSettings.MIN_POLLING_MS.toFloat()..AppSettings.MAX_POLLING_MS.toFloat(),
        steps = ((AppSettings.MAX_POLLING_MS - AppSettings.MIN_POLLING_MS) / 50 - 1).toInt(),
    )
    Text(
        stringResource(R.string.settings_polling_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_ok)) } },
    )
}

