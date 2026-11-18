package com.jacdrive.obd

import com.jacdrive.obd.elm.ElmErrorKind
import com.jacdrive.obd.elm.ElmResponseParser
import com.jacdrive.obd.elm.ElmResult
import com.jacdrive.obd.elm.isLinkFailure
import com.jacdrive.obd.pid.ObdPid
import com.jacdrive.obd.pid.PidDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElmParserAndPidTest {

    private fun data(raw: String, cmd: String) = ElmResponseParser.parseData(raw, cmd) as ElmResult.Data

    @Test fun rpmWithSpacesAndPrompt() {
        val r = data("41 0C 1A F8 \r\r>", "010C")
        assertEquals(1726.0, PidDecoder.decode(ObdPid.RPM, r)!!, 0.0)
    }

    @Test fun spacesOffAndEchoOn() {
        val r = data("010D\r410D3C\r\r>", "010D")
        assertEquals(60.0, PidDecoder.decode(ObdPid.SPEED, r)!!, 0.0)
    }

    @Test fun searchingIsIgnored() {
        val r = data("SEARCHING...\r41 05 7B\r\r>", "0105")
        assertEquals(83.0, PidDecoder.decode(ObdPid.COOLANT_TEMP, r)!!, 0.0)
    }

    @Test fun standardScalings() {
        fun d(pid: ObdPid, vararg b: Int) = pid.decode(b)
        assertEquals(100.0, d(ObdPid.ENGINE_LOAD, 255), 1e-9)
        assertEquals(-40.0, d(ObdPid.INTAKE_TEMP, 0), 1e-9)
        assertEquals(50.0, d(ObdPid.THROTTLE, 0x80) .let { Math.round(it).toDouble() }, 0.0)
        assertEquals(-64.0, d(ObdPid.TIMING_ADVANCE, 0), 1e-9)
        assertEquals(14.2, d(ObdPid.CONTROL_MODULE_VOLTAGE, 0x37, 0x78), 1e-9)
        assertEquals(101.0, d(ObdPid.INTAKE_MAP, 101), 1e-9)
        assertEquals(6.55, d(ObdPid.MAF, 0x02, 0x8F), 1e-9)
    }

    @Test fun firstMatchingEcuWinsWhenSeveralAnswer() {
        val r = data("41 0D 32\r41 0D 33\r>", "010D")
        assertEquals(50.0, PidDecoder.decode(ObdPid.SPEED, r)!!, 0.0)
    }

    @Test fun truncatedReplyIsRejected() {
        val r = data("41 0C 1A\r>", "010C")
        assertNull(PidDecoder.decode(ObdPid.RPM, r))
    }

    @Test fun noDataIsNotAnError() {
        val r = ElmResponseParser.parseData("NO DATA\r\r>", "012F")
        assertEquals(ElmResult.NoData, r)
        assertFalse(r.isLinkFailure)
    }

    @Test fun errorsAreClassified() {
        val r = ElmResponseParser.parseData("SEARCHING...\rUNABLE TO CONNECT\r>", "0100")
        assertEquals(ElmErrorKind.UNABLE_TO_CONNECT, (r as ElmResult.Error).kind)
        val c = ElmResponseParser.parseData("CAN ERROR\r>", "010C")
        assertTrue(c.isLinkFailure)
        assertEquals(ElmResult.NotUnderstood, ElmResponseParser.parseData("?\r>", "01FF"))
        assertEquals(ElmResult.Timeout, ElmResponseParser.parseData("", "010C"))
    }

    @Test fun supportedPidBitmap() {
        // BE 3E B8 13 is a common real-world reply for PIDs 01-20.
        val r = data("41 00 BE 3E B8 13\r>", "0100")
        val s = PidDecoder.supportedPids(r, 0x00)
        assertTrue(0x01 in s && 0x04 in s && 0x05 in s && 0x0C in s && 0x0D in s && 0x11 in s)
        assertFalse(0x02 in s)
        assertTrue(0x20 in s) // next range available
    }

    @Test fun multiFrameIsoTpOutput() {
        val r = data("00A\r0: 43 04 01 33 02 17\r1: 03 00 04 20 00 00 00\r\r>", "03")
        val joined = ElmResponseParser.joinFrames(r)
        assertEquals(0x43, joined[0])
        assertEquals(13, joined.size)
    }

    @Test fun monitorStatusMil() {
        val r = data("41 01 83 07 E5 00\r>", "0101")
        val m = PidDecoder.monitorStatus(r)!!
        assertTrue(m.milOn)
        assertEquals(3, m.dtcCount)
    }
}
