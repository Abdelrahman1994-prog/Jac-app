package com.jacdrive.analytics.performance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacdrive.core.database.PerformanceDao
import com.jacdrive.core.database.PerformanceRunEntity
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.model.freshness
import com.jacdrive.core.settings.SettingsRepository
import com.jacdrive.obd.repository.VehicleDataRepository
import com.jacdrive.ui.chart.TimedValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimerUi(val type: RunType, val state: TimerState, val resultS: Double?, val runningSinceMs: Double?, val saved: Boolean)

data class PerformanceUiState(
    val snapshot: VehicleSnapshot = VehicleSnapshot.offline(),
    val acknowledged: Boolean = false,
    val armed: Boolean = false,
    val timers: List<TimerUi> = RunType.entries.map { TimerUi(it, TimerState.WAITING_FOR_STOP, null, null, false) },
    val rpm: List<TimedValue> = emptyList(),
    val throttle: List<TimedValue> = emptyList(),
    val load: List<TimedValue> = emptyList(),
    val maxSpeedKmh: Double? = null,
    val avgSpeedKmh: Double? = null,
    val g: GSample? = null,
    val gUnavailable: Boolean = false,
    val peakG: Double = 0.0,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
)

@HiltViewModel
class PerformanceViewModel @Inject constructor(
    private val vehicleData: VehicleDataRepository,
    private val dao: PerformanceDao,
    private val gSensor: GForceSensor,
    settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PerformanceUiState(gUnavailable = !gSensor.available))
    val state: StateFlow<PerformanceUiState> = _state.asStateFlow()

    val runs: StateFlow<List<PerformanceRunEntity>> = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val timers = RunType.entries.map(::AccelerationTimer)
    private val saved = mutableSetOf<RunType>()
    private var lastSpeedTs = 0L
    private var speedTimeSum = 0.0
    private var speedTime = 0L
    private var runPeakG = 0.0

    init {
        viewModelScope.launch { vehicleData.snapshot.collect(::onSnapshot) }
        viewModelScope.launch { settings.settings.map { it.speedUnit }.collect { u -> _state.update { it.copy(speedUnit = u) } } }
        if (gSensor.available) viewModelScope.launch {
            gSensor.samples().collect { g ->
                if (g != null && timers.any { it.state == TimerState.RUNNING }) runPeakG = maxOf(runPeakG, g.magnitude)
                _state.update { it.copy(g = g, peakG = if (g != null) maxOf(it.peakG, g.magnitude) else it.peakG) }
            }
        }
    }

    private fun onSnapshot(s: VehicleSnapshot) {
        val now = System.currentTimeMillis()
        fun add(list: List<TimedValue>, m: VehicleMetric) =
            s[m]?.takeIf { it.freshness(m, now) == Freshness.LIVE }?.let { v ->
                if (list.lastOrNull()?.tMs == v.timestampMs) list else (list + TimedValue(v.timestampMs, v.value)).filter { now - it.tMs <= 60_000 }
            } ?: list.filter { now - it.tMs <= 60_000 }

        // Timers and session stats use real ECU speed only — never demo data.
        val speed = s[VehicleMetric.SPEED]?.takeIf { s.mode == DataMode.LIVE && it.source == DataSource.ECU && it.freshness(VehicleMetric.SPEED, now) == Freshness.LIVE }
        var maxSpeed = _state.value.maxSpeedKmh
        if (speed != null && speed.timestampMs > lastSpeedTs) {
            if (lastSpeedTs > 0 && speed.timestampMs - lastSpeedTs < 5_000) {
                val dt = speed.timestampMs - lastSpeedTs
                speedTimeSum += speed.value * dt
                speedTime += dt
            }
            lastSpeedTs = speed.timestampMs
            maxSpeed = maxOf(maxSpeed ?: 0.0, speed.value)
            if (_state.value.armed) timers.forEach { it.onSpeed(speed.value, speed.timestampMs) }
        }

        _state.update {
            it.copy(
                snapshot = s,
                rpm = add(it.rpm, VehicleMetric.RPM),
                throttle = add(it.throttle, VehicleMetric.THROTTLE_POSITION),
                load = add(it.load, VehicleMetric.ENGINE_LOAD),
                maxSpeedKmh = maxSpeed,
                avgSpeedKmh = if (speedTime > 0) speedTimeSum / speedTime else null,
                timers = timerUi(),
            )
        }
    }

    private fun timerUi() = timers.map {
        TimerUi(it.type, it.state, it.resultS, it.startMs.takeIf { _ -> it.state == TimerState.RUNNING }, it.type in saved)
    }

    fun acknowledge(v: Boolean) = _state.update { it.copy(acknowledged = v, armed = it.armed && v) }

    fun arm() {
        if (!_state.value.acknowledged) return
        timers.forEach { it.reset() }
        saved.clear()
        runPeakG = 0.0
        _state.update { it.copy(armed = true, timers = timerUi()) }
    }

    fun disarm() {
        timers.forEach { it.reset() }
        _state.update { it.copy(armed = false, timers = timerUi()) }
    }

    fun save(type: RunType) {
        val t = timers.first { it.type == type }
        val result = t.resultS ?: return
        if (type in saved) return
        saved += type
        viewModelScope.launch {
            dao.insert(
                PerformanceRunEntity(
                    timestampMs = System.currentTimeMillis(), type = type.name, timeS = result,
                    peakG = runPeakG.takeIf { it > 0 }, maxSpeedKmh = _state.value.maxSpeedKmh ?: type.toKmh,
                ),
            )
        }
        _state.update { it.copy(timers = timerUi()) }
    }

    fun resetSession() {
        speedTimeSum = 0.0; speedTime = 0; lastSpeedTs = 0
        _state.update { it.copy(maxSpeedKmh = null, avgSpeedKmh = null, peakG = 0.0) }
    }

    fun deleteRun(id: Long) = viewModelScope.launch { dao.delete(id) }
}
