package com.jacdrive.dashboard.dash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacdrive.core.database.DiagnosticDao
import com.jacdrive.core.database.SessionWithCodes
import com.jacdrive.core.model.AlertPreferences
import com.jacdrive.core.model.CockpitPreferences
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.obd.history.HistoryState
import com.jacdrive.obd.history.LiveHistory
import com.jacdrive.obd.repository.VehicleDataRepository
import com.jacdrive.ui.components.DisplayUnits
import com.jacdrive.vehicle.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val snapshot: VehicleSnapshot = VehicleSnapshot.offline(),
    val units: DisplayUnits = DisplayUnits(SpeedUnit.KMH, TemperatureUnit.CELSIUS),
    val cockpit: CockpitPreferences = CockpitPreferences(),
    val alerts: AlertPreferences = AlertPreferences(),
    val make: String = "JAC",
    val model: String = "J7",
    val sessions: List<SessionWithCodes> = emptyList(),
    val history: HistoryState = HistoryState(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    vehicleData: VehicleDataRepository,
    private val settings: SettingsRepository,
    vehicle: VehicleRepository,
    diagnosticDao: DiagnosticDao,
    history: LiveHistory,
) : ViewModel() {

    private val base = combine(vehicleData.snapshot, settings.settings, settings.cockpit, settings.alerts) { s, a, c, al ->
        DashboardUiState(snapshot = s, units = DisplayUnits(a.speedUnit, a.temperatureUnit), cockpit = c, alerts = al)
    }

    val state: StateFlow<DashboardUiState> = combine(base, vehicle.profile, diagnosticDao.observeSessions(), history.state) { b, p, sessions, h ->
        b.copy(make = p.make, model = p.model, sessions = sessions, history = h)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun setSport(enabled: Boolean) = viewModelScope.launch { settings.setSportMode(enabled) }
}
