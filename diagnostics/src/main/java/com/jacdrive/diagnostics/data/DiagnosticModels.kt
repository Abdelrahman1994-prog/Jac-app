package com.jacdrive.diagnostics.data

import com.jacdrive.core.database.DiagnosticCodeEntity
import com.jacdrive.core.database.DiagnosticSessionEntity
import com.jacdrive.core.database.SessionWithCodes
import com.jacdrive.obd.dtc.DtcType
import com.jacdrive.obd.dtc.Monitor
import com.jacdrive.obd.dtc.MonitorState
import com.jacdrive.obd.dtc.ScanResult

enum class SessionKind { SCAN, CLEAR }

data class DiagnosticSession(
    val id: Long,
    val timestampMs: Long,
    val kind: SessionKind,
    val milOn: Boolean?,
    val reportedDtcCount: Int?,
    val stored: List<String>,
    val pending: List<String>,
    /** null = the ECU does not support permanent codes. */
    val permanent: List<String>?,
    val readiness: Map<Monitor, MonitorState>?,
    val failedReads: Set<DtcType>,
    val protocol: String?,
    val adapterName: String?,
    val mileageKm: Int?,
) {
    val allCodes: Set<String> get() = (stored + pending + permanent.orEmpty()).toSet()
    val complete: Boolean get() = failedReads.isEmpty()
}

data class SessionComparison(
    val newCodes: List<String>,
    val resolved: List<String>,
    val persisting: List<String>,
)

object SessionMapper {
    fun toEntities(
        scan: ScanResult, kind: SessionKind, protocol: String?, adapterName: String?, mileageKm: Int?,
    ): Pair<DiagnosticSessionEntity, List<DiagnosticCodeEntity>> {
        val session = DiagnosticSessionEntity(
            timestampMs = scan.timestampMs,
            kind = kind.name,
            milOn = scan.monitorStatus?.milOn,
            reportedDtcCount = scan.monitorStatus?.dtcCount,
            permanentSupported = scan.permanent != null,
            readiness = scan.monitorStatus?.monitors?.entries?.joinToString(",") { "${it.key.name}=${if (it.value == MonitorState.COMPLETE) "C" else "I"}" },
            failedReads = scan.failedReads.joinToString(",") { it.name }.ifEmpty { null },
            protocol = protocol,
            adapterName = adapterName,
            mileageKm = mileageKm,
        )
        val codes = scan.stored.map { DiagnosticCodeEntity(sessionId = 0, code = it, type = DtcType.STORED.name) } +
            scan.pending.map { DiagnosticCodeEntity(sessionId = 0, code = it, type = DtcType.PENDING.name) } +
            scan.permanent.orEmpty().map { DiagnosticCodeEntity(sessionId = 0, code = it, type = DtcType.PERMANENT.name) }
        return session to codes
    }

    fun fromEntity(e: SessionWithCodes): DiagnosticSession {
        val s = e.session
        fun codes(t: DtcType) = e.codes.filter { it.type == t.name }.map { it.code }
        return DiagnosticSession(
            id = s.id,
            timestampMs = s.timestampMs,
            kind = runCatching { SessionKind.valueOf(s.kind) }.getOrDefault(SessionKind.SCAN),
            milOn = s.milOn,
            reportedDtcCount = s.reportedDtcCount,
            stored = codes(DtcType.STORED),
            pending = codes(DtcType.PENDING),
            permanent = if (s.permanentSupported) codes(DtcType.PERMANENT) else null,
            readiness = s.readiness?.split(',')?.mapNotNull { pair ->
                val (k, v) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val m = runCatching { Monitor.valueOf(k) }.getOrNull() ?: return@mapNotNull null
                m to if (v == "C") MonitorState.COMPLETE else MonitorState.INCOMPLETE
            }?.toMap(),
            failedReads = s.failedReads?.split(',')?.mapNotNull { runCatching { DtcType.valueOf(it) }.getOrNull() }?.toSet().orEmpty(),
            protocol = s.protocol,
            adapterName = s.adapterName,
            mileageKm = s.mileageKm,
        )
    }

    /** Compares a session with an earlier one. */
    fun compare(current: DiagnosticSession, previous: DiagnosticSession): SessionComparison {
        val now = current.allCodes
        val before = previous.allCodes
        return SessionComparison(
            newCodes = (now - before).sorted(),
            resolved = (before - now).sorted(),
            persisting = (now intersect before).sorted(),
        )
    }
}
