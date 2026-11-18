package com.jacdrive.core.model

/** Overall source of the live data stream currently feeding the UI. */
enum class DataMode {
    /** No adapter connected and demo mode is off: nothing is shown as a value. */
    OFFLINE,

    /** Simulated drive cycle. Every value is tagged [DataSource.SIMULATED]. */
    DEMO,

    /** Real data from a connected OBD-II adapter. */
    LIVE,
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val step: String) : ConnectionState
    data class Connected(val adapterName: String, val protocol: String?) : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}

/**
 * Immutable picture of the vehicle at a point in time.
 *
 * @param supportedMetrics metrics the ECU confirmed it supports (PID discovery). `null` means
 * discovery has not run (offline / demo), which is different from "not supported".
 */
data class VehicleSnapshot(
    val mode: DataMode,
    val connection: ConnectionState,
    val values: Map<VehicleMetric, LiveValue>,
    val supportedMetrics: Set<VehicleMetric>?,
    val timestampMs: Long,
) {
    operator fun get(metric: VehicleMetric): LiveValue? = values[metric]

    fun isUnsupported(metric: VehicleMetric): Boolean =
        supportedMetrics != null && metric !in supportedMetrics

    companion object {
        fun offline(nowMs: Long = System.currentTimeMillis()) = VehicleSnapshot(
            mode = DataMode.OFFLINE,
            connection = ConnectionState.Disconnected,
            values = emptyMap(),
            supportedMetrics = null,
            timestampMs = nowMs,
        )
    }
}
