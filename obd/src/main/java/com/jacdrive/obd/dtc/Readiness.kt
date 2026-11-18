package com.jacdrive.obd.dtc

import com.jacdrive.obd.elm.ElmResult
import com.jacdrive.obd.pid.PidDecoder

enum class Monitor {
    MISFIRE, FUEL_SYSTEM, COMPONENTS,
    CATALYST, HEATED_CATALYST, EVAP_SYSTEM, SECONDARY_AIR, OXYGEN_SENSOR, OXYGEN_SENSOR_HEATER, EGR_VVT,
    NMHC_CATALYST, NOX_SCR, BOOST_PRESSURE, EXHAUST_GAS_SENSOR, PM_FILTER,
}

enum class MonitorState { COMPLETE, INCOMPLETE }

/** Decoded PID 0101: MIL, number of confirmed emission DTCs and readiness monitors. */
data class MonitorStatus(
    val milOn: Boolean,
    val dtcCount: Int,
    val compressionIgnition: Boolean,
    /** Only monitors the ECU reports as supported are present. */
    val monitors: Map<Monitor, MonitorState>,
) {
    val incomplete: List<Monitor> get() = monitors.filterValues { it == MonitorState.INCOMPLETE }.keys.toList()

    companion object {
        /** SAE J1979 PID 01 layout: A = MIL/count, B = common monitors, C = supported, D = incomplete. */
        fun parse(result: ElmResult.Data): MonitorStatus? {
            val p = PidDecoder.payload(result, 0x01, 4) ?: return null
            val (a, b, c, d) = p.toList()
            val diesel = b and 0x08 != 0
            val monitors = linkedMapOf<Monitor, MonitorState>()
            fun put(m: Monitor, supported: Boolean, incomplete: Boolean) {
                if (supported) monitors[m] = if (incomplete) MonitorState.INCOMPLETE else MonitorState.COMPLETE
            }
            put(Monitor.MISFIRE, b and 0x01 != 0, b and 0x10 != 0)
            put(Monitor.FUEL_SYSTEM, b and 0x02 != 0, b and 0x20 != 0)
            put(Monitor.COMPONENTS, b and 0x04 != 0, b and 0x40 != 0)
            val sparkBits = listOf(
                Monitor.CATALYST, Monitor.HEATED_CATALYST, Monitor.EVAP_SYSTEM, Monitor.SECONDARY_AIR,
                null /* bit 4 reserved (former A/C) */, Monitor.OXYGEN_SENSOR, Monitor.OXYGEN_SENSOR_HEATER, Monitor.EGR_VVT,
            )
            val dieselBits = listOf(
                Monitor.NMHC_CATALYST, Monitor.NOX_SCR, null, Monitor.BOOST_PRESSURE,
                null, Monitor.EXHAUST_GAS_SENSOR, Monitor.PM_FILTER, Monitor.EGR_VVT,
            )
            (if (diesel) dieselBits else sparkBits).forEachIndexed { bit, m ->
                if (m != null) put(m, c and (1 shl bit) != 0, d and (1 shl bit) != 0)
            }
            return MonitorStatus(a and 0x80 != 0, a and 0x7F, diesel, monitors)
        }
    }
}
