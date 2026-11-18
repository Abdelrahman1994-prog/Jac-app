package com.jacdrive.diagnostics

import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.LiveValue
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.diagnostics.data.DeductionReason
import com.jacdrive.diagnostics.data.DiagnosticSession
import com.jacdrive.diagnostics.data.DtcDatabase
import com.jacdrive.diagnostics.data.DtcSeverity
import com.jacdrive.diagnostics.data.HealthScore
import com.jacdrive.diagnostics.data.SessionKind
import com.jacdrive.diagnostics.data.SessionMapper
import com.jacdrive.obd.dtc.DtcType
import com.jacdrive.obd.dtc.Monitor
import com.jacdrive.obd.dtc.MonitorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DtcDatabaseTest {
    private val db = DtcDatabase(File("src/main/assets/dtc_database.json").readText())

    @Test fun loadsAllEntriesWithBothLanguages() {
        assertTrue(db.size >= 100)
        db.allCodes.forEach {
            assertTrue(it.code, it.title.en.isNotBlank() && it.title.ar.isNotBlank())
            assertTrue(it.code, it.causes.isNotEmpty() && it.steps.isNotEmpty())
            assertTrue(it.code, com.jacdrive.obd.dtc.DtcCodec.isValid(it.code))
            assertTrue(it.code, it.severity != DtcSeverity.UNKNOWN)
            it.causes.forEach { c -> assertTrue(c.ar.any { ch -> ch in '؀'..'ۿ' }) }
        }
    }

    @Test fun p0301MatchesSpecification() {
        val p = db.lookup("p0301")
        assertEquals("Cylinder 1 misfire detected", p.title.en)
        assertEquals("تم اكتشاف خلل في الاحتراق داخل الأسطوانة الأولى", p.title.ar)
        assertTrue(p.causes.any { it.en.contains("spark plug", ignoreCase = true) })
        assertTrue(p.causes.any { it.en.contains("ignition coil", ignoreCase = true) })
        assertTrue(p.causes.any { it.en.contains("injector", ignoreCase = true) })
        assertTrue(p.causes.any { it.en.contains("compression", ignoreCase = true) })
    }

    @Test fun unknownCodesAreInterpretedNotInvented() {
        val m = db.lookup("P1A23")
        assertFalse(m.known)
        assertTrue(m.manufacturerSpecific)
        assertEquals(DtcSeverity.UNKNOWN, m.severity)
        assertTrue(m.causes.isEmpty())
        val g = db.lookup("P0399")
        assertFalse(g.known)
        assertFalse(g.manufacturerSpecific)
        assertTrue(g.title.en.contains("Ignition"))
    }

    private fun session(id: Long, stored: List<String> = emptyList(), pending: List<String> = emptyList(), mil: Boolean? = false,
                        readiness: Map<Monitor, MonitorState>? = null, failed: Set<DtcType> = emptySet()) =
        DiagnosticSession(id, id * 1000, SessionKind.SCAN, mil, stored.size, stored, pending, null, readiness, failed, null, null, null)

    @Test fun comparison() {
        val c = SessionMapper.compare(session(2, listOf("P0301", "P0420")), session(1, listOf("P0420", "P0171")))
        assertEquals(listOf("P0301"), c.newCodes)
        assertEquals(listOf("P0171"), c.resolved)
        assertEquals(listOf("P0420"), c.persisting)
    }

    private val noLive = VehicleSnapshot.offline(0)

    @Test fun cleanScanScores100() {
        val r = HealthScore.compute(session(1), noLive, 0)!!
        assertEquals(100, r.score)
        assertTrue(r.deductions.isEmpty())
        assertFalse(r.liveIncluded)
    }

    @Test fun deductionsAreTransparentAndCapped() {
        val s = session(1, stored = listOf("P0301", "P0302", "P0303", "P0304", "P0300"), pending = listOf("P0171"), mil = true,
            readiness = mapOf(Monitor.CATALYST to MonitorState.INCOMPLETE, Monitor.MISFIRE to MonitorState.COMPLETE))
        val r = HealthScore.compute(s, noLive, 0)!!
        assertEquals(HealthScore.MIL, r.deductions.first { it.reason == DeductionReason.MIL_ON }.points)
        assertEquals(HealthScore.MAX_STORED, r.deductions.first { it.reason == DeductionReason.STORED_CODES }.points)
        assertEquals(100 - 30 - 40 - 5 - 3, r.score)
    }

    @Test fun noScoreWithoutCompleteScan() {
        assertNull(HealthScore.compute(null, noLive, 0))
        assertNull(HealthScore.compute(session(1, failed = setOf(DtcType.STORED)), noLive, 0))
    }

    @Test fun liveValuesCountOnlyWhenRealAndFresh() {
        fun snap(src: DataSource) = VehicleSnapshot(DataMode.LIVE, ConnectionState.Connected("a", null),
            mapOf(VehicleMetric.COOLANT_TEMP to LiveValue(112.0, 1_000, src)), null, 1_000)
        assertTrue(HealthScore.compute(session(1), snap(DataSource.ECU), 1_500)!!.deductions.any { it.reason == DeductionReason.COOLANT_HIGH })
        assertTrue(HealthScore.compute(session(1), snap(DataSource.SIMULATED), 1_500)!!.deductions.isEmpty())
        assertTrue(HealthScore.compute(session(1), snap(DataSource.ECU), 60_000)!!.deductions.isEmpty())
    }
}
