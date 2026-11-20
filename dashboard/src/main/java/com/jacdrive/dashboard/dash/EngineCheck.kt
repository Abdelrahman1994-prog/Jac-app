package com.jacdrive.dashboard.dash

import com.jacdrive.core.database.SessionWithCodes
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.VehicleSnapshot

enum class EngineCheckState {
    /** A complete, recent fault scan found no codes and the MIL is off. */
    OK,
    /** MIL on, or fault codes present. */
    FAULT,
    /** Not enough information: no recent complete scan, demo data, or a scan with read errors. */
    UNKNOWN,
}

data class EngineCheck(val state: EngineCheckState, val codeCount: Int?, val lastScanMs: Long?, val milOn: Boolean?)

/**
 * Decides the Check Engine indicator. Never reports OK just because no code was retrieved:
 * OK requires a complete scan within [maxScanAgeMs] with zero codes and the MIL off.
 */
object EngineCheckRules {
    const val MAX_SCAN_AGE_MS = 24 * 60 * 60_000L

    fun evaluate(snapshot: VehicleSnapshot, sessions: List<SessionWithCodes>, nowMs: Long, maxScanAgeMs: Long = MAX_SCAN_AGE_MS): EngineCheck {
        if (snapshot.mode == DataMode.DEMO) return EngineCheck(EngineCheckState.UNKNOWN, null, null, null)
        val liveMil = if (snapshot.mode == DataMode.LIVE) snapshot.milOn else null
        val scan = sessions.filter { it.session.kind == "SCAN" }.maxByOrNull { it.session.timestampMs }
        val codes = scan?.codes?.map { it.code }?.distinct()
        val complete = scan != null && scan.session.failedReads.isNullOrEmpty()
        val recent = scan != null && nowMs - scan.session.timestampMs <= maxScanAgeMs

        val state = when {
            liveMil == true -> EngineCheckState.FAULT
            scan != null && recent && (codes!!.isNotEmpty() || scan.session.milOn == true) -> EngineCheckState.FAULT
            scan != null && recent && complete && codes!!.isEmpty() && scan.session.milOn == false && liveMil != true -> EngineCheckState.OK
            else -> EngineCheckState.UNKNOWN
        }
        return EngineCheck(state, codes?.size?.takeIf { recent }, scan?.session?.timestampMs, liveMil ?: scan?.session?.milOn)
    }
}
