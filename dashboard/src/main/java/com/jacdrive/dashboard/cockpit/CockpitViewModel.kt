package com.jacdrive.dashboard.cockpit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacdrive.core.model.CockpitThemeId
import com.jacdrive.core.model.GaugeLayout
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.obd.repository.VehicleDataRepository
import com.jacdrive.ui.components.DisplayUnits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CockpitViewModel @Inject constructor(
    vehicleData: VehicleDataRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<CockpitUiState> = combine(
        vehicleData.snapshot,
        settings.cockpit,
        settings.settings,
    ) { snapshot, cockpit, app ->
        CockpitUiState(snapshot, cockpit, DisplayUnits(app.speedUnit, app.temperatureUnit))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CockpitUiState())

    fun setTheme(id: CockpitThemeId) = viewModelScope.launch { settings.setCockpitTheme(id) }
    fun setLayout(layout: GaugeLayout) = viewModelScope.launch { settings.setGaugeLayout(layout) }
    fun setAnimations(enabled: Boolean) = viewModelScope.launch { settings.setAnimationsEnabled(enabled) }
}
