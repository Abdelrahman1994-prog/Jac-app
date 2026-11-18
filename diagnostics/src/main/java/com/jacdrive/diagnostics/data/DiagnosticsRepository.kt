package com.jacdrive.diagnostics.data

import com.jacdrive.core.database.DiagnosticDao
import com.jacdrive.core.database.VehicleProfileDao
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.obd.dtc.ClearResult
import com.jacdrive.obd.dtc.DiagnosticScanner
import com.jacdrive.obd.session.ObdConnectionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScanOutcome {
    data class Saved(val sessionId: Long, val session: DiagnosticSession) : ScanOutcome
    data object NotConnected : ScanOutcome
    data class Error(val message: String) : ScanOutcome
}

sealed interface ClearOutcome {
    data class Cleared(val rescanId: Long?) : ClearOutcome
    data class Rejected(val reason: String) : ClearOutcome
    data object NotConnected : ClearOutcome
    data class Error(val message: String) : ClearOutcome
}

@Singleton
class DiagnosticsRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val dao: DiagnosticDao,
    private val vehicleDao: VehicleProfileDao,
    private val obd: ObdConnectionManager,
) {
    val sessions: Flow<List<DiagnosticSession>> = dao.observeSessions().map { list -> list.map(SessionMapper::fromEntity) }

    fun session(id: Long): Flow<DiagnosticSession?> = dao.observeSession(id).map { it?.let(SessionMapper::fromEntity) }

    /** Reads MIL, readiness and stored/pending/permanent codes from the ECU and saves the session. */
    suspend fun scan(): ScanOutcome = try {
        val result = obd.withChannel { ch, info -> DiagnosticScanner(ch, info.isCan).scan() }
        if (result == null) ScanOutcome.NotConnected else {
            val (session, codes) = SessionMapper.toEntities(
                result, SessionKind.SCAN, protocolName(), adapterName(), vehicleDao.get()?.currentMileageKm,
            )
            val id = dao.insert(session, codes)
            ScanOutcome.Saved(id, SessionMapper.fromEntity(com.jacdrive.core.database.SessionWithCodes(session.copy(id = id), codes.map { it.copy(sessionId = id) })))
        }
    } catch (e: Exception) {
        ScanOutcome.Error(e.message ?: e.javaClass.simpleName)
    }

    /**
     * Sends Mode 04. MUST only be called after the user explicitly confirmed it in the UI.
     * Records a CLEAR event, then performs a read-only scan to show the result.
     */
    suspend fun clearConfirmedByUser(): ClearOutcome = try {
        val result = obd.withChannel { ch, info -> DiagnosticScanner(ch, info.isCan).clear() }
        when (result) {
            null -> ClearOutcome.NotConnected
            is ClearResult.Rejected -> ClearOutcome.Rejected(result.reason)
            is ClearResult.Failed -> ClearOutcome.Error(result.result.toString())
            ClearResult.Cleared -> {
                dao.insert(
                    com.jacdrive.core.database.DiagnosticSessionEntity(
                        timestampMs = System.currentTimeMillis(), kind = SessionKind.CLEAR.name, milOn = null,
                        reportedDtcCount = null, permanentSupported = false, readiness = null, failedReads = null,
                        protocol = protocolName(), adapterName = adapterName(), mileageKm = vehicleDao.get()?.currentMileageKm,
                    ),
                    emptyList(),
                )
                ClearOutcome.Cleared((scan() as? ScanOutcome.Saved)?.sessionId)
            }
        }
    } catch (e: Exception) {
        ClearOutcome.Error(e.message ?: e.javaClass.simpleName)
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun writeReport(session: DiagnosticSession, previous: DiagnosticSession?, arabic: Boolean, uri: android.net.Uri, lookup: (String) -> DtcInfo) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
            val html = DiagnosticReport.html(session, previous, vehicleDao.get(), lookup, arabic, version)
            context.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray(Charsets.UTF_8)) } ?: error("no stream")
        }

    private fun protocolName() = obd.state.value.adapter?.protocolName
    private fun adapterName() = (obd.state.value.connection as? ConnectionState.Connected)?.adapterName
}
