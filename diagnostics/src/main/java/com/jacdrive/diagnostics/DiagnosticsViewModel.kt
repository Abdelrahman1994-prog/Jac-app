package com.jacdrive.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.obd.repository.VehicleDataRepository
import com.jacdrive.ui.components.DisplayUnits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DiagnosticsUiState(
    val snapshot: VehicleSnapshot = VehicleSnapshot.offline(),
    val units: DisplayUnits = DisplayUnits(SpeedUnit.KMH, TemperatureUnit.CELSIUS),
)

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    vehicleData: VehicleDataRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<DiagnosticsUiState> = combine(vehicleData.snapshot, settings.settings) { s, a ->
        DiagnosticsUiState(s, DisplayUnits(a.speedUnit, a.temperatureUnit))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsUiState())
}
