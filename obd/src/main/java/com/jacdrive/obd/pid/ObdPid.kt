package com.jacdrive.obd.pid

import com.jacdrive.core.model.VehicleMetric

/**
 * Standard Mode 01 PIDs used by the app, with SAE J1979 / ISO 15031-5 scaling.
 * [bytes] is the number of data bytes after the `41 xx` header.
 */
enum class ObdPid(
    val code: Int,
    val bytes: Int,
    val metric: VehicleMetric?,
    private val formula: (IntArray) -> Double,
) {
    ENGINE_LOAD(0x04, 1, VehicleMetric.ENGINE_LOAD, { it[0] * 100.0 / 255.0 }),
    COOLANT_TEMP(0x05, 1, VehicleMetric.COOLANT_TEMP, { it[0] - 40.0 }),
    INTAKE_MAP(0x0B, 1, VehicleMetric.INTAKE_MANIFOLD_PRESSURE, { it[0].toDouble() }),
    RPM(0x0C, 2, VehicleMetric.RPM, { (it[0] * 256 + it[1]) / 4.0 }),
    SPEED(0x0D, 1, VehicleMetric.SPEED, { it[0].toDouble() }),
    TIMING_ADVANCE(0x0E, 1, VehicleMetric.TIMING_ADVANCE, { it[0] / 2.0 - 64.0 }),
    INTAKE_TEMP(0x0F, 1, VehicleMetric.INTAKE_AIR_TEMP, { it[0] - 40.0 }),
    MAF(0x10, 2, null, { (it[0] * 256 + it[1]) / 100.0 }), // g/s, used for fuel-flow estimate
    THROTTLE(0x11, 1, VehicleMetric.THROTTLE_POSITION, { it[0] * 100.0 / 255.0 }),
    FUEL_LEVEL(0x2F, 1, VehicleMetric.FUEL_LEVEL, { it[0] * 100.0 / 255.0 }),
    CONTROL_MODULE_VOLTAGE(0x42, 2, VehicleMetric.BATTERY_VOLTAGE, { (it[0] * 256 + it[1]) / 1000.0 }),
    AMBIENT_TEMP(0x46, 1, VehicleMetric.AMBIENT_TEMP, { it[0] - 40.0 }),
    FUEL_RATE(0x5E, 2, null, { (it[0] * 256 + it[1]) / 20.0 }), // L/h
    ;

    /** Request string, e.g. "010C". */
    val request: String get() = "01%02X".format(code)

    fun decode(data: IntArray): Double = formula(data)

    companion object {
        private val byCode = entries.associateBy { it.code }
        fun of(code: Int): ObdPid? = byCode[code]
    }
}
