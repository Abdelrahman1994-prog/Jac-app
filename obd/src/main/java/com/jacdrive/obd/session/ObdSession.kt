package com.jacdrive.obd.session

import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.LiveValue
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.obd.calc.TripAccumulator
import com.jacdrive.obd.elm.ElmChannel
import com.jacdrive.obd.elm.ElmResponseParser
import com.jacdrive.obd.elm.ElmResult
import com.jacdrive.obd.elm.ObdException
import com.jacdrive.obd.elm.ObdFailure
import com.jacdrive.obd.elm.isLinkFailure
import com.jacdrive.obd.pid.ObdPid
import com.jacdrive.obd.pid.PidDecoder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive

/** Per-PID availability as observed during the session. */
enum class PidStatus { UNSUPPORTED, ACTIVE, NOT_RESPONDING }

data class SessionStats(
    val cycles: Long = 0,
    val requests: Long = 0,
    val successes: Long = 0,
    val noData: Long = 0,
    val linkFailures: Long = 0,
    val avgLatencyMs: Double = 0.0,
    val pidStatus: Map<ObdPid, PidStatus> = emptyMap(),
) {
    val successRate: Double get() = if (requests == 0L) 0.0 else successes.toDouble() / requests
}

data class SessionUpdate(val snapshot: VehicleSnapshot, val stats: SessionStats)

/**
 * Polls the PIDs the ECU confirmed it supports. Fast-changing values every cycle, slow ones
 * every few cycles, so the adapter is never flooded. Never requests unsupported PIDs.
 *
 * - A supported PID that answers `NO DATA` [NOT_RESPONDING_AFTER] times in a row is parked
 *   as NOT_RESPONDING (and is distinct from UNSUPPORTED).
 * - [LINK_LOST_AFTER] consecutive link failures (timeouts, bus errors) end the session with
 *   [ObdFailure.LINK_LOST] so the connection manager can reconnect.
 */
class ObdSession(
    private val channel: ElmChannel,
    private val supportedPids: Set<Int>,
    private val connection: ConnectionState.Connected,
    private val intervalMs: () -> Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Slot(val pid: ObdPid, val every: Int)

    private val plan: List<Slot> = buildList {
        listOf(ObdPid.RPM, ObdPid.SPEED, ObdPid.THROTTLE, ObdPid.ENGINE_LOAD).forEach { add(Slot(it, 1)) }
        listOf(ObdPid.INTAKE_MAP, ObdPid.TIMING_ADVANCE, ObdPid.MAF, ObdPid.FUEL_RATE).forEach { add(Slot(it, 3)) }
        listOf(ObdPid.COOLANT_TEMP, ObdPid.INTAKE_TEMP, ObdPid.FUEL_LEVEL, ObdPid.AMBIENT_TEMP, ObdPid.CONTROL_MODULE_VOLTAGE)
            .forEach { add(Slot(it, 15)) }
    }.filter { it.pid.code in supportedPids }

    private val useAdapterVoltage = ObdPid.CONTROL_MODULE_VOLTAGE.code !in supportedPids
    private val hasFuelFlow = ObdPid.FUEL_RATE.code in supportedPids || ObdPid.MAF.code in supportedPids

    val supportedMetrics: Set<VehicleMetric> = buildSet {
        plan.mapNotNullTo(this) { it.pid.metric }
        add(VehicleMetric.BATTERY_VOLTAGE) // ECU PID 0x42 or the adapter's own ATRV
        if (ObdPid.SPEED.code in supportedPids) {
            add(VehicleMetric.TRIP_DISTANCE)
            if (hasFuelFlow) add(VehicleMetric.AVG_FUEL_CONSUMPTION)
        }
    }

    fun run(): Flow<SessionUpdate> = flow {
        val values = mutableMapOf<VehicleMetric, LiveValue>()
        val raw = mutableMapOf<ObdPid, Double>()
        val noDataStreak = mutableMapOf<ObdPid, Int>()
        val status = ObdPid.entries.associateWith {
            if (it.code in supportedPids) PidStatus.ACTIVE else PidStatus.UNSUPPORTED
        }.toMutableMap()
        val trip = TripAccumulator()
        var stats = SessionStats()
        var latencyTotal = 0.0
        var linkStreak = 0
        var cycle = 0L

        suspend fun request(cmd: String, timeout: Long, parse: (String) -> ElmResult): ElmResult {
            val t0 = clock()
            val r = parse(channel.exchange(cmd, timeout))
            val dt = clock() - t0
            stats = stats.copy(requests = stats.requests + 1)
            when {
                r is ElmResult.Data || r is ElmResult.Text -> {
                    linkStreak = 0
                    latencyTotal += dt
                    stats = stats.copy(successes = stats.successes + 1, avgLatencyMs = latencyTotal / (stats.successes + 1))
                }
                r is ElmResult.NoData -> {
                    linkStreak = 0
                    stats = stats.copy(noData = stats.noData + 1)
                }
                r.isLinkFailure -> {
                    linkStreak++
                    stats = stats.copy(linkFailures = stats.linkFailures + 1)
                    if (linkStreak >= LINK_LOST_AFTER) throw ObdException(ObdFailure.LINK_LOST, "$cmd: $r")
                }
            }
            return r
        }

        while (currentCoroutineContext().isActive) {
            for (slot in plan) {
                if (cycle % slot.every != 0L || status[slot.pid] != PidStatus.ACTIVE) continue
                val r = request(slot.pid.request, PID_TIMEOUT_MS) { ElmResponseParser.parseData(it, slot.pid.request) }
                val v = (r as? ElmResult.Data)?.let { PidDecoder.decode(slot.pid, it) }
                if (v != null) {
                    noDataStreak[slot.pid] = 0
                    raw[slot.pid] = v
                    slot.pid.metric?.let { values[it] = LiveValue(v, clock(), DataSource.ECU) }
                } else if (r is ElmResult.NoData || r is ElmResult.Data) {
                    val n = (noDataStreak[slot.pid] ?: 0) + 1
                    noDataStreak[slot.pid] = n
                    if (n >= NOT_RESPONDING_AFTER) status[slot.pid] = PidStatus.NOT_RESPONDING
                }
            }
            if (useAdapterVoltage && cycle % 15 == 0L) {
                val r = request("ATRV", PID_TIMEOUT_MS) { ElmResponseParser.parseText(it, "ATRV") }
                (r as? ElmResult.Text)?.text?.let(ElmInitializer::parseVoltage)?.let {
                    values[VehicleMetric.BATTERY_VOLTAGE] = LiveValue(it, clock(), DataSource.ADAPTER)
                }
            }

            // Derived values, only from fresh real readings.
            val now = clock()
            values[VehicleMetric.SPEED]?.takeIf { now - it.timestampMs < 2_000 }?.let { speed ->
                trip.add(speed.value, now, fuelRateLph(raw))
                values[VehicleMetric.TRIP_DISTANCE] = LiveValue(trip.distanceKm, now, DataSource.CALCULATED)
                trip.averageConsumption()?.let {
                    values[VehicleMetric.AVG_FUEL_CONSUMPTION] = LiveValue(it, now, DataSource.CALCULATED)
                }
            }

            cycle++
            stats = stats.copy(cycles = cycle, pidStatus = status.toMap())
            emit(
                SessionUpdate(
                    VehicleSnapshot(DataMode.LIVE, connection, values.toMap(), supportedMetrics, now),
                    stats,
                ),
            )
            delay(intervalMs())
        }
    }

    /**
     * Fuel flow in L/h: PID 5E when the ECU reports it; otherwise estimated from MAF assuming
     * petrol at stoichiometric AFR 14.7 and density 745 g/L (accurate to roughly ±10 %).
     */
    private fun fuelRateLph(raw: Map<ObdPid, Double>): Double? =
        raw[ObdPid.FUEL_RATE] ?: raw[ObdPid.MAF]?.let { it * 3600.0 / (14.7 * 745.0) }

    companion object {
        const val PID_TIMEOUT_MS = 1_000L
        const val NOT_RESPONDING_AFTER = 5
        const val LINK_LOST_AFTER = 4
    }
}
