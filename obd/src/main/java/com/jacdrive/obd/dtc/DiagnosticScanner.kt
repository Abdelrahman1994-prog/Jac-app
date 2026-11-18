package com.jacdrive.obd.dtc

import com.jacdrive.obd.elm.ElmChannel
import com.jacdrive.obd.elm.ElmResponseParser
import com.jacdrive.obd.elm.ElmResult

data class ScanResult(
    val timestampMs: Long,
    val monitorStatus: MonitorStatus?,
    val stored: List<String>,
    val pending: List<String>,
    /** null when the ECU does not support Mode 0A. */
    val permanent: List<String>?,
    /** Lists that could not be read because of a communication problem. */
    val failedReads: Set<DtcType>,
)

sealed interface ClearResult {
    data object Cleared : ClearResult
    /** ECU answered with a negative response or nothing usable (often: engine running). */
    data class Rejected(val reason: String) : ClearResult
    data class Failed(val result: ElmResult) : ClearResult
}

/** Read-only fault scan plus the explicit, user-confirmed Mode 04 clear. */
class DiagnosticScanner(
    private val channel: ElmChannel,
    private val isCan: Boolean,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun scan(): ScanResult {
        val status = (ElmResponseParser.parseData(channel.exchange("0101", TIMEOUT_MS), "0101") as? ElmResult.Data)
            ?.let(MonitorStatus::parse)
        val failed = mutableSetOf<DtcType>()
        suspend fun read(type: DtcType): List<String>? =
            when (val r = DtcParser.parse(channel.exchange(type.mode, TIMEOUT_MS), type, isCan)) {
                is DtcReadResult.Codes -> r.codes
                DtcReadResult.NotSupported -> null
                is DtcReadResult.Failed -> { failed += type; null }
            }
        val stored = read(DtcType.STORED).orEmpty()
        val pending = read(DtcType.PENDING).orEmpty()
        val permanent = read(DtcType.PERMANENT)
        return ScanResult(clock(), status, stored, pending, permanent, failed)
    }

    /**
     * Mode 04: clears stored/pending codes, freeze frames and readiness monitors.
     * Must only be called after explicit user confirmation. Never called automatically.
     */
    suspend fun clear(): ClearResult {
        val raw = channel.exchange("04", TIMEOUT_MS)
        val lines = ElmResponseParser.cleanLines(raw, "04")
        return when {
            lines.any { it.replace(" ", "").startsWith("44") } -> ClearResult.Cleared
            lines.any { it.replace(" ", "").startsWith("7F04") } -> ClearResult.Rejected(lines.joinToString(" "))
            else -> when (val r = ElmResponseParser.parseData(raw, "04")) {
                ElmResult.NoData -> ClearResult.Rejected("NO DATA")
                is ElmResult.Data -> ClearResult.Rejected(lines.joinToString(" "))
                else -> ClearResult.Failed(r)
            }
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
