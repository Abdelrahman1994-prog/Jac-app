package com.jacdrive.obd.demo

import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.LiveValue
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.obd.calc.TripAccumulator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/** Emits simulated snapshots. Every value carries [DataSource.SIMULATED]. */
class DemoDataSource @Inject constructor() {

    fun stream(intervalMs: Long, clock: () -> Long = System::currentTimeMillis): Flow<VehicleSnapshot> = flow {
        val model = DemoDriveModel()
        val trip = TripAccumulator()
        var last = clock()
        while (true) {
            val now = clock()
            val s = model.step((now - last).coerceAtLeast(1) / 1000.0)
            last = now
            trip.add(s.speedKmh, now, s.fuelRateLph)

            fun v(x: Double) = LiveValue(x, now, DataSource.SIMULATED)
            val values = buildMap {
                put(VehicleMetric.SPEED, v(s.speedKmh))
                put(VehicleMetric.RPM, v(s.rpm))
                put(VehicleMetric.COOLANT_TEMP, v(s.coolantC))
                put(VehicleMetric.BATTERY_VOLTAGE, v(s.batteryV))
                put(VehicleMetric.ENGINE_LOAD, v(s.engineLoadPct))
                put(VehicleMetric.THROTTLE_POSITION, v(s.throttlePct))
                put(VehicleMetric.FUEL_LEVEL, v(s.fuelLevelPct))
                put(VehicleMetric.INTAKE_AIR_TEMP, v(s.intakeC))
                put(VehicleMetric.INTAKE_MANIFOLD_PRESSURE, v(s.mapKpa))
                put(VehicleMetric.TIMING_ADVANCE, v(s.timingAdvanceDeg))
                put(VehicleMetric.TRIP_DISTANCE, v(trip.distanceKm))
                trip.averageConsumption()?.let { put(VehicleMetric.AVG_FUEL_CONSUMPTION, v(it)) }
            }
            emit(
                VehicleSnapshot(
                    mode = DataMode.DEMO,
                    connection = ConnectionState.Disconnected,
                    values = values,
                    supportedMetrics = null,
                    timestampMs = now,
                ),
            )
            delay(intervalMs)
        }
    }
}
