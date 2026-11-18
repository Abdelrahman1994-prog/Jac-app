package com.jacdrive.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacdrive.core.model.AppSettings
import com.jacdrive.core.model.CockpitPreferences
import com.jacdrive.core.model.CockpitThemeId
import com.jacdrive.core.model.DashboardBackground
import com.jacdrive.core.model.DashboardWidget
import com.jacdrive.core.model.GaugeLayout
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.model.ThemeMode
import com.jacdrive.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val app: AppSettings = AppSettings(),
    val cockpit: CockpitPreferences = CockpitPreferences(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val backup: com.jacdrive.core.backup.BackupManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    sealed interface DataMessage {
        data object BackupSaved : DataMessage
        data class RestoreReady(val info: com.jacdrive.core.backup.BackupInfo) : DataMessage
        data class Failed(val reason: String) : DataMessage
    }

    val dataMessage = kotlinx.coroutines.flow.MutableStateFlow<DataMessage?>(null)

    fun exportBackup(uri: android.net.Uri) = viewModelScope.launch {
        dataMessage.value = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { backup.export(it) } ?: error("no stream")
            DataMessage.BackupSaved
        }.getOrElse { DataMessage.Failed(it.message ?: "error") }
    }

    fun checkRestore(uri: android.net.Uri) = viewModelScope.launch {
        dataMessage.value = runCatching {
            val info = context.contentResolver.openInputStream(uri)?.use { backup.validate(it) } ?: error("no stream")
            DataMessage.RestoreReady(info)
        }.getOrElse { DataMessage.Failed(it.message ?: "error") }
    }

    fun restore(info: com.jacdrive.core.backup.BackupInfo) = viewModelScope.launch { backup.restoreAndRestart(info) }

    fun dismissData() { dataMessage.value = null }

    val state: StateFlow<SettingsUiState> = combine(repo.settings, repo.cockpit, ::SettingsUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private fun io(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { repo.block() }
    }

    fun setThemeMode(m: ThemeMode) = io { setThemeMode(m) }
    fun setSpeedUnit(u: SpeedUnit) = io { setSpeedUnit(u) }
    fun setTemperatureUnit(u: TemperatureUnit) = io { setTemperatureUnit(u) }
    fun setPollingInterval(ms: Long) = io { setPollingInterval(ms) }
    fun setDemoMode(enabled: Boolean) = io { setDemoMode(enabled) }

    fun setCockpitTheme(id: CockpitThemeId) = io { setCockpitTheme(id) }
    fun setAccent(argb: Long?) = io { setAccentOverride(argb) }
    fun setLayout(l: GaugeLayout) = io { setGaugeLayout(l) }
    fun setBackground(b: DashboardBackground) = io { setDashboardBackground(b) }
    fun setAnimations(enabled: Boolean) = io { setAnimationsEnabled(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = io { setKeepScreenOn(enabled) }
    fun setRedline(rpm: Int) = io { setRedlineRpm(rpm) }
    fun setWidgetVisible(w: DashboardWidget, visible: Boolean) = io { setWidgetVisible(w, visible) }
}
