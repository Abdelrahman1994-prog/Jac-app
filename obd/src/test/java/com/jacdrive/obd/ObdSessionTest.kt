package com.jacdrive.obd

import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.obd.elm.ObdException
import com.jacdrive.obd.elm.ObdFailure
import com.jacdrive.obd.elm.StreamElmChannel
import com.jacdrive.obd.pid.ObdPid
import com.jacdrive.obd.session.ElmInitializer
import com.jacdrive.obd.session.ObdSession
import com.jacdrive.obd.session.PidStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

class ObdSessionTest {

    private val connected = ConnectionState.Connected("OBDII", "ISO 15765-4 CAN 11/500")

    @Test fun initializerRunsStandardSequenceAndDiscoversPids() = runTest {
        val elm = FakeElm()
        val result = ElmInitializer(elm).run()
        assertEquals(listOf("ATZ", "ATE0", "ATL0", "ATS0", "ATH0", "ATSP0", "0100"), elm.log.take(7))
        assertEquals("6", result.adapter.protocolNumber)
        assertEquals("ISO 15765-4 CAN 11/500", result.adapter.protocolName)
        assertEquals(12.6, result.adapter.adapterVoltage!!, 0.0)
        assertTrue(listOf(0x04, 0x05, 0x0C, 0x0D, 0x0F, 0x10, 0x11).all { it in result.supportedPids })
        assertFalse(0x2F in result.supportedPids)
        assertTrue("0120" in elm.log) // followed the next-range bit
        assertFalse("0140" in elm.log)
    }

    @Test fun ignitionOffIsReportedAsEcuNotResponding() = runTest {
        try {
            ElmInitializer(FakeElm(ignitionOff = true)).run()
            fail()
        } catch (e: ObdException) {
            assertEquals(ObdFailure.ECU_NOT_RESPONDING, e.failure)
        }
    }

    @Test fun silentAdapterIsReported() = runTest {
        try {
            ElmInitializer(FakeElm(silent = true)).run()
            fail()
        } catch (e: ObdException) {
            assertEquals(ObdFailure.ADAPTER_NOT_RESPONDING, e.failure)
        }
    }

    @Test fun sessionDecodesOnlySupportedPids() = runTest {
        val elm = FakeElm()
        val init = ElmInitializer(elm).run()
        elm.log.clear()
        val update = ObdSession(elm, init.supportedPids, connected, { 0L }).run().first()
        val s = update.snapshot
        assertEquals(DataMode.LIVE, s.mode)
        assertEquals(1726.0, s[VehicleMetric.RPM]!!.value, 0.0)
        assertEquals(60.0, s[VehicleMetric.SPEED]!!.value, 0.0)
        assertEquals(83.0, s[VehicleMetric.COOLANT_TEMP]!!.value, 0.0)
        assertEquals(DataSource.ECU, s[VehicleMetric.RPM]!!.source)
        // Fuel level (0x2F) is not supported: never requested, reported as unsupported.
        assertFalse("012F" in elm.log)
        assertTrue(s.isUnsupported(VehicleMetric.FUEL_LEVEL))
        // Voltage via adapter (ATRV) because PID 0x42 is unsupported.
        assertEquals(DataSource.ADAPTER, s[VehicleMetric.BATTERY_VOLTAGE]!!.source)
        assertEquals(PidStatus.UNSUPPORTED, update.stats.pidStatus[ObdPid.FUEL_LEVEL])
    }

    @Test fun slowPidsArePolledLessOften() = runTest {
        val elm = FakeElm()
        val init = ElmInitializer(elm).run()
        elm.log.clear()
        ObdSession(elm, init.supportedPids, connected, { 0L }).run().take(15).toList()
        assertEquals(15, elm.log.count { it == "010C" })
        assertEquals(1, elm.log.count { it == "0105" })
    }

    @Test fun supportedButSilentPidBecomesNotResponding() = runTest {
        val elm = FakeElm(noDataPids = setOf(0x11))
        val init = ElmInitializer(elm).run()
        val last = ObdSession(elm, init.supportedPids, connected, { 0L }).run().take(8).toList().last()
        assertEquals(PidStatus.NOT_RESPONDING, last.stats.pidStatus[ObdPid.THROTTLE])
        assertEquals(PidStatus.ACTIVE, last.stats.pidStatus[ObdPid.RPM])
    }

    @Test fun repeatedTimeoutsEndSessionAsLinkLost() = runTest {
        val elm = FakeElm()
        val init = ElmInitializer(elm).run()
        elm.dropAfter = elm.log.size + 3
        try {
            ObdSession(elm, init.supportedPids, connected, { 0L }).run().take(20).toList()
            fail()
        } catch (e: ObdException) {
            assertEquals(ObdFailure.LINK_LOST, e.failure)
        }
    }

    @Test fun tripAndConsumptionAreCalculatedFromRealSpeedAndMaf() = runTest {
        var t = 0L
        val elm = FakeElm()
        val init = ElmInitializer(elm).run()
        val updates = ObdSession(elm, init.supportedPids, connected, { 0L }, clock = { t += 100; t }).run().take(400).toList()
        val trip = updates.last().snapshot[VehicleMetric.TRIP_DISTANCE]!!
        assertEquals(DataSource.CALCULATED, trip.source)
        assertTrue(trip.value > 0.0)
    }

    /** The real stream channel against an in-memory ELM that answers on '\r'. */
    @Test fun streamChannelReadsUntilPrompt() = runBlocking {
        val elm = FakeElm()
        val pipe = LoopbackElm(elm)
        val ch = StreamElmChannel(pipe.input, pipe.output, Dispatchers.IO)
        val raw = ch.exchange("010D", 1_000)
        assertTrue(raw.endsWith(">"))
        assertTrue(raw.contains("410D3C"))
        assertEquals("", StreamElmChannel(EmptyInput, ByteArrayOutputStream(), Dispatchers.IO).exchange("010D", 50))
    }

    private object EmptyInput : InputStream() {
        override fun read(): Int = -1
        override fun available(): Int = 0
    }

    private class LoopbackElm(private val elm: FakeElm) {
        private val pending = StringBuilder()
        private var reply = ByteArray(0)
        private var pos = 0
        val output = object : OutputStream() {
            override fun write(b: Int) {
                if (b == '\r'.code) {
                    reply = runBlocking { elm.exchange(pending.toString(), 0) }.toByteArray()
                    pos = 0
                    pending.clear()
                } else pending.append(b.toChar())
            }
        }
        val input = object : InputStream() {
            override fun available(): Int = reply.size - pos
            override fun read(): Int = if (pos < reply.size) reply[pos++].toInt() else -1
        }
    }
}
