package com.jacdrive.dashboard

import com.jacdrive.core.database.DiagnosticCodeEntity
import com.jacdrive.core.database.DiagnosticSessionEntity
import com.jacdrive.core.database.SessionWithCodes
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.dashboard.dash.EngineCheckRules
import com.jacdrive.dashboard.dash.EngineCheckState
import org.junit.Assert.assertEquals
import org.junit.Test

class EngineCheckTest {
    private val now = 10 * 86_400_000L
    private fun live(mil: Boolean?) = VehicleSnapshot(DataMode.LIVE, ConnectionState.Connected("a", null), emptyMap(), null, now, mil)
    private fun scan(t: Long, codes: List<String>, mil: Boolean?, failed: String? = null) = SessionWithCodes(
        DiagnosticSessionEntity(id = 1, timestampMs = t, kind = "SCAN", milOn = mil, reportedDtcCount = codes.size, permanentSupported = false,
            readiness = null, failedReads = failed, protocol = null, adapterName = null, mileageKm = null),
        codes.map { DiagnosticCodeEntity(sessionId = 1, code = it, type = "STORED") },
    )

    @Test fun noScanIsUnknownNotGreen() =
        assertEquals(EngineCheckState.UNKNOWN, EngineCheckRules.evaluate(live(false), emptyList(), now).state)

    @Test fun cleanRecentCompleteScanIsOk() =
        assertEquals(EngineCheckState.OK, EngineCheckRules.evaluate(live(false), listOf(scan(now - 3_600_000, emptyList(), false)), now).state)

    @Test fun incompleteScanIsUnknown() =
        assertEquals(EngineCheckState.UNKNOWN, EngineCheckRules.evaluate(live(false), listOf(scan(now - 1000, emptyList(), false, failed = "STORED")), now).state)

    @Test fun oldScanIsUnknown() =
        assertEquals(EngineCheckState.UNKNOWN, EngineCheckRules.evaluate(live(false), listOf(scan(now - 3 * 86_400_000L, emptyList(), false)), now).state)

    @Test fun codesOrLiveMilAreFault() {
        val r = EngineCheckRules.evaluate(live(false), listOf(scan(now - 1000, listOf("P0301", "P0420"), true)), now)
        assertEquals(EngineCheckState.FAULT, r.state)
        assertEquals(2, r.codeCount)
        assertEquals(EngineCheckState.FAULT, EngineCheckRules.evaluate(live(true), listOf(scan(now - 1000, emptyList(), false)), now).state)
    }

    @Test fun demoIsAlwaysUnknown() {
        val demo = VehicleSnapshot(DataMode.DEMO, ConnectionState.Disconnected, emptyMap(), null, now, false)
        assertEquals(EngineCheckState.UNKNOWN, EngineCheckRules.evaluate(demo, listOf(scan(now - 1000, emptyList(), false)), now).state)
    }
}
