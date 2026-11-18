package com.jacdrive.obd

import com.jacdrive.obd.dtc.ClearResult
import com.jacdrive.obd.dtc.DiagnosticScanner
import com.jacdrive.obd.dtc.DtcCodec
import com.jacdrive.obd.dtc.DtcParser
import com.jacdrive.obd.dtc.DtcReadResult
import com.jacdrive.obd.dtc.DtcType
import com.jacdrive.obd.dtc.Monitor
import com.jacdrive.obd.dtc.MonitorState
import com.jacdrive.obd.dtc.MonitorStatus
import com.jacdrive.obd.elm.ElmChannel
import com.jacdrive.obd.elm.ElmResponseParser
import com.jacdrive.obd.elm.ElmResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DtcTest {

    @Test fun codecCoversAllSystems() {
        assertEquals("P0133", DtcCodec.decode(0x01, 0x33))
        assertEquals("P0301", DtcCodec.decode(0x03, 0x01))
        assertEquals("C0035", DtcCodec.decode(0x40, 0x35))
        assertEquals("B1A2F", DtcCodec.decode(0x9A, 0x2F))
        assertEquals("U0100", DtcCodec.decode(0xC1, 0x00))
        assertEquals("P2135", DtcCodec.decode(0x21, 0x35))
        assertTrue(DtcCodec.isValid("p0420"))
        assertFalse(DtcCodec.isValid("P04G0"))
    }

    private fun codes(raw: String, type: DtcType = DtcType.STORED, can: Boolean = true) =
        (DtcParser.parse(raw, type, can) as DtcReadResult.Codes).codes

    @Test fun canSingleFrame() = assertEquals(listOf("P0133", "P0217"), codes("43 02 01 33 02 17\r\r>"))

    @Test fun canNoCodes() = assertEquals(emptyList<String>(), codes("43 00\r>"))

    @Test fun canMultiFrame() {
        val raw = "00A\r0: 43 04 01 33 02 17\r1: 03 01 04 20 00 00 00\r\r>"
        assertEquals(listOf("P0133", "P0217", "P0301", "P0420"), codes(raw))
    }

    @Test fun canTwoEcusMerged() {
        assertEquals(listOf("P0300", "P0700"), codes("43 01 03 00\r43 01 07 00\r>"))
    }

    @Test fun legacyProtocolPadding() {
        val raw = "43 01 33 03 01 00 00\r43 04 20 00 00 00 00\r>"
        assertEquals(listOf("P0133", "P0301", "P0420"), codes(raw, can = false))
    }

    @Test fun pendingUsesItsOwnServiceByte() {
        assertEquals(listOf("P0171"), codes("47 01 01 71\r>", DtcType.PENDING))
        // A stored-code reply must not be counted as pending.
        assertEquals(emptyList<String>(), codes("43 01 01 71\r>", DtcType.PENDING))
    }

    @Test fun noDataMeansNoCodesForStoredButUnsupportedForPermanent() {
        assertEquals(DtcReadResult.Codes(emptyList()), DtcParser.parse("NO DATA\r>", DtcType.STORED, true))
        assertEquals(DtcReadResult.NotSupported, DtcParser.parse("NO DATA\r>", DtcType.PERMANENT, true))
        assertEquals(DtcReadResult.NotSupported, DtcParser.parse("7F 0A 11\r>", DtcType.PERMANENT, true))
        assertTrue(DtcParser.parse("CAN ERROR\r>", DtcType.STORED, true) is DtcReadResult.Failed)
    }

    @Test fun monitorStatusSparkIgnition() {
        // MIL on, 2 codes; misfire+fuel+components supported, fuel incomplete; catalyst, evap, O2,
        // O2 heater supported; evap incomplete.
        val r = ElmResponseParser.parseData("41 01 82 27 65 04\r>", "0101") as ElmResult.Data
        val m = MonitorStatus.parse(r)!!
        assertTrue(m.milOn)
        assertEquals(2, m.dtcCount)
        assertFalse(m.compressionIgnition)
        assertEquals(MonitorState.INCOMPLETE, m.monitors[Monitor.FUEL_SYSTEM])
        assertEquals(MonitorState.COMPLETE, m.monitors[Monitor.MISFIRE])
        assertEquals(MonitorState.COMPLETE, m.monitors[Monitor.CATALYST])
        assertEquals(MonitorState.INCOMPLETE, m.monitors[Monitor.EVAP_SYSTEM])
        assertNull(m.monitors[Monitor.SECONDARY_AIR])
        assertEquals(setOf(Monitor.FUEL_SYSTEM, Monitor.EVAP_SYSTEM), m.incomplete.toSet())
    }

    private class Scripted(val replies: Map<String, String>) : ElmChannel {
        val sent = mutableListOf<String>()
        override suspend fun exchange(command: String, timeoutMs: Long): String {
            sent += command
            return (replies[command] ?: "NO DATA") + "\r\r>"
        }
    }

    @Test fun fullScan() = runTest {
        val ch = Scripted(mapOf("0101" to "41 01 81 07 65 00", "03" to "43 01 03 01", "07" to "47 00", "0A" to "7F 0A 11"))
        val r = DiagnosticScanner(ch, isCan = true).scan()
        assertTrue(r.monitorStatus!!.milOn)
        assertEquals(listOf("P0301"), r.stored)
        assertEquals(emptyList<String>(), r.pending)
        assertNull(r.permanent)
        assertTrue(r.failedReads.isEmpty())
        assertFalse("04" in ch.sent) // scanning never clears
    }

    @Test fun clearOutcomes() = runTest {
        assertEquals(ClearResult.Cleared, DiagnosticScanner(Scripted(mapOf("04" to "44")), true).clear())
        assertTrue(DiagnosticScanner(Scripted(mapOf("04" to "7F 04 22")), true).clear() is ClearResult.Rejected)
    }
}
