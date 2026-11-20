package com.jacdrive.obd.history

import com.jacdrive.core.di.ApplicationScope
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.freshness
import com.jacdrive.obd.repository.VehicleDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** One sampled value. */
data class HistoryPoint(val tMs: Long, val value: Double)

/** Recent samples per metric plus the data mode they came from (so demo history is labelled). */
data class HistoryState(val series: Map<VehicleMetric, List<HistoryPoint>> = emptyMap(), val mode: DataMode = DataMode.OFFLINE)

/**
 * Keeps the last [WINDOW_MS] (10 min) of fresh values, sampled once per second, for the
 * dashboard trend chart and the Live Data screen. Only values that are actually present and fresh
 * are recorded — gaps stay gaps. History is cleared when switching between live and demo data.
 */
@Singleton
class LiveHistory @Inject constructor(
    private val vehicleData: VehicleDataRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state.asStateFlow()
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            launch { vehicleData.snapshot.collect { } } // keep the shared snapshot stream active
            while (true) {
                sample(System.currentTimeMillis())
                delay(SAMPLE_MS)
            }
        }
    }

    internal fun sample(now: Long) {
        val s = vehicleData.snapshot.value
        val prev = _state.value
        val base = if (s.mode != prev.mode) emptyMap() else prev.series
        val next = HashMap<VehicleMetric, List<HistoryPoint>>()
        for (m in VehicleMetric.entries) {
            val old = base[m].orEmpty().filter { now - it.tMs <= WINDOW_MS }
            val v = s[m]?.takeIf { it.freshness(m, now) == Freshness.LIVE }
            next[m] = if (v != null && s.mode != DataMode.OFFLINE) old + HistoryPoint(now, v.value) else old
        }
        _state.value = HistoryState(next, s.mode)
    }

    companion object {
        const val WINDOW_MS = 10 * 60_000L
        const val SAMPLE_MS = 1_000L
    }
}
