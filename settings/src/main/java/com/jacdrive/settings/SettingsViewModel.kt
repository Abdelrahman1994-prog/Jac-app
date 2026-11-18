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
) : ViewModel() {

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
