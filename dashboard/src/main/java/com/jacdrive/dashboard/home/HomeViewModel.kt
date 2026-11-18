package com.jacdrive.dashboard.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacdrive.core.database.VehicleProfileEntity
import com.jacdrive.core.model.CockpitPreferences
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.obd.repository.VehicleDataRepository
import com.jacdrive.ui.components.DisplayUnits
import com.jacdrive.vehicle.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val vehicle: VehicleProfileEntity? = null,
    val snapshot: VehicleSnapshot = VehicleSnapshot.offline(),
    val units: DisplayUnits = DisplayUnits(SpeedUnit.KMH, TemperatureUnit.CELSIUS),
    val cockpit: CockpitPreferences = CockpitPreferences(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    vehicleData: VehicleDataRepository,
    vehicleRepository: VehicleRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val state: StateFlow<HomeUiState> = combine(
        vehicleRepository.profile,
        vehicleData.snapshot,
        settings.settings,
        settings.cockpit,
    ) { vehicle, snapshot, app, cockpit ->
        HomeUiState(vehicle, snapshot, DisplayUnits(app.speedUnit, app.temperatureUnit), cockpit)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
