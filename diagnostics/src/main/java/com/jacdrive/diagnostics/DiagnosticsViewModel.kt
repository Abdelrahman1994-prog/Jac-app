package com.jacdrive.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.model.freshness
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.diagnostics.data.ClearOutcome
import com.jacdrive.diagnostics.data.DiagnosticSession
import com.jacdrive.diagnostics.data.DiagnosticsRepository
import com.jacdrive.diagnostics.data.DtcDatabaseProvider
import com.jacdrive.diagnostics.data.DtcInfo
import com.jacdrive.diagnostics.data.DtcSeverity
import com.jacdrive.diagnostics.data.HealthScore
import com.jacdrive.diagnostics.data.HealthScoreResult
import com.jacdrive.diagnostics.data.ScanOutcome
import com.jacdrive.diagnostics.data.SessionKind
import com.jacdrive.obd.repository.VehicleDataRepository
import com.jacdrive.ui.components.DisplayUnits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class Busy { SCANNING, CLEARING }

sealed interface DiagMessage {
    data object ScanSaved : DiagMessage
    data object ReportSaved : DiagMessage
    data object Cleared : DiagMessage
    data object NotConnected : DiagMessage
    data object EngineRunning : DiagMessage
    data class Rejected(val reason: String) : DiagMessage
    data class Error(val detail: String) : DiagMessage
}

data class DiagnosticsUiState(
    val snapshot: VehicleSnapshot = VehicleSnapshot.offline(),
    val units: DisplayUnits = DisplayUnits(SpeedUnit.KMH, TemperatureUnit.CELSIUS),
    val sessions: List<DiagnosticSession> = emptyList(),
    val busy: Busy? = null,
    val message: DiagMessage? = null,
) {
    val live: Boolean get() = snapshot.mode == DataMode.LIVE
    val latestScan: DiagnosticSession? get() = sessions.firstOrNull { it.kind == SessionKind.SCAN }
}

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    vehicleData: VehicleDataRepository,
    settings: SettingsRepository,
    private val repository: DiagnosticsRepository,
    private val dtcProvider: DtcDatabaseProvider,
) : ViewModel() {

    private val local = MutableStateFlow(Pair<Busy?, DiagMessage?>(null, null))

    val state: StateFlow<DiagnosticsUiState> = combine(vehicleData.snapshot, settings.settings, repository.sessions, local) { s, a, sessions, l ->
        DiagnosticsUiState(s, DisplayUnits(a.speedUnit, a.temperatureUnit), sessions, l.first, l.second)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiagnosticsUiState())

    private val criticalCodes: Set<String> by lazy {
        dtcProvider.database.allCodes.filter { it.severity == DtcSeverity.CRITICAL }.map { it.code }.toSet()
    }

    fun info(code: String): DtcInfo = dtcProvider.database.lookup(code)

    fun healthScore(state: DiagnosticsUiState, nowMs: Long): HealthScoreResult? =
        HealthScore.compute(state.latestScan, state.snapshot, nowMs, criticalCodes)

    fun session(id: Long) = repository.session(id)

    fun scan() {
        if (local.value.first != null) return
        local.update { Busy.SCANNING to null }
        viewModelScope.launch {
            val msg = when (val r = repository.scan()) {
                is ScanOutcome.Saved -> DiagMessage.ScanSaved
                ScanOutcome.NotConnected -> DiagMessage.NotConnected
                is ScanOutcome.Error -> DiagMessage.Error(r.message)
            }
            local.update { null to msg }
        }
    }

    /** True if the engine is confirmed running from fresh ECU data; clearing is refused then. */
    fun engineRunning(nowMs: Long = System.currentTimeMillis()): Boolean {
        val rpm = state.value.snapshot[VehicleMetric.RPM] ?: return false
        return rpm.freshness(VehicleMetric.RPM, nowMs) == Freshness.LIVE && rpm.value > 300
    }

    /** Called only from the confirmation dialog after the user ticked "I understand". */
    fun clearConfirmed() {
        if (local.value.first != null) return
        if (engineRunning()) {
            local.update { null to DiagMessage.EngineRunning }
            return
        }
        local.update { Busy.CLEARING to null }
        viewModelScope.launch {
            val msg = when (val r = repository.clearConfirmedByUser()) {
                is ClearOutcome.Cleared -> DiagMessage.Cleared
                is ClearOutcome.Rejected -> DiagMessage.Rejected(r.reason)
                ClearOutcome.NotConnected -> DiagMessage.NotConnected
                is ClearOutcome.Error -> DiagMessage.Error(r.message)
            }
            local.update { null to msg }
        }
    }

    fun dismissMessage() = local.update { it.first to null }

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }

    /** Writes an HTML report for [session] to [uri] (picked with the system file dialog). */
    fun exportReport(session: com.jacdrive.diagnostics.data.DiagnosticSession, previous: com.jacdrive.diagnostics.data.DiagnosticSession?, arabic: Boolean, uri: android.net.Uri) =
        viewModelScope.launch {
            val ok = runCatching { repository.writeReport(session, previous, arabic, uri) { dtcProvider.database.lookup(it) } }.isSuccess
            local.update { it.first to if (ok) DiagMessage.ReportSaved else DiagMessage.Error("report") }
        }
}
