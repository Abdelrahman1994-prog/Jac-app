package com.jacdrive.obd.session

import com.jacdrive.obd.elm.ElmChannel
import com.jacdrive.obd.elm.ElmErrorKind
import com.jacdrive.obd.elm.ElmResponseParser
import com.jacdrive.obd.elm.ElmResult
import com.jacdrive.obd.elm.ObdException
import com.jacdrive.obd.elm.ObdFailure
import com.jacdrive.obd.pid.PidDecoder

enum class InitStep { RESET, CONFIGURE, SEARCH_PROTOCOL, READ_INFO, DISCOVER_PIDS }

data class AdapterInfo(
    val elmVersion: String?,
    val protocolNumber: String?,
    val protocolName: String?,
    val adapterVoltage: Double?,
) {
    /** ISO 15765-4 (protocols 6–9) and J1939 use CAN framing for multi-frame replies. */
    val isCan: Boolean get() = protocolNumber?.uppercase() in setOf("6", "7", "8", "9", "A")
}

data class InitResult(val adapter: AdapterInfo, val supportedPids: Set<Int>)

/**
 * Standard ELM327 bring-up:
 * ATZ (reset) → ATE0 (echo off) → ATL0 (linefeeds off) → ATS0 (spaces off) → ATH0 (headers off)
 * → ATSP0 (automatic protocol) → 0100 (forces the protocol search) → ATDPN / ATI / ATRV
 * → PID discovery (0100, 0120, 0140 … while the "next range" bit is set).
 *
 * Only read-only commands are ever sent.
 */
class ElmInitializer(private val channel: ElmChannel) {

    suspend fun run(onStep: (InitStep) -> Unit = {}): InitResult {
        onStep(InitStep.RESET)
        val reset = ElmResponseParser.parseText(channel.exchange("ATZ", RESET_TIMEOUT_MS), "ATZ")
        if (reset !is ElmResult.Text) throw ObdException(ObdFailure.ADAPTER_NOT_RESPONDING, "ATZ: $reset")
        val versionFromReset = reset.text.lineSequence().firstOrNull { it.contains("ELM") }?.trim()

        onStep(InitStep.CONFIGURE)
        for (cmd in listOf("ATE0", "ATL0", "ATS0", "ATH0", "ATSP0")) {
            val r = ElmResponseParser.parseText(channel.exchange(cmd, AT_TIMEOUT_MS), cmd)
            // Some clones answer "?" to settings they don't implement; that's tolerable. Silence is not.
            if (r is ElmResult.Timeout) throw ObdException(ObdFailure.ADAPTER_NOT_RESPONDING, "$cmd timed out")
        }

        onStep(InitStep.SEARCH_PROTOCOL)
        val first = ElmResponseParser.parseData(channel.exchange("0100", SEARCH_TIMEOUT_MS), "0100")
        val firstData = when (first) {
            is ElmResult.Data -> first
            is ElmResult.Error -> throw ObdException(
                if (first.kind == ElmErrorKind.UNABLE_TO_CONNECT) ObdFailure.ECU_NOT_RESPONDING else ObdFailure.LINK_LOST,
                first.raw,
            )
            is ElmResult.Timeout -> throw ObdException(ObdFailure.ADAPTER_NOT_RESPONDING, "0100 timed out")
            else -> throw ObdException(ObdFailure.ECU_NOT_RESPONDING, "0100: $first")
        }

        onStep(InitStep.READ_INFO)
        val dpn = text("ATDPN")?.removePrefix("A")?.trim()
        val version = text("ATI") ?: versionFromReset
        val voltage = text("ATRV")?.let(::parseVoltage)

        onStep(InitStep.DISCOVER_PIDS)
        val supported = discoverPids(firstData)

        return InitResult(
            AdapterInfo(version, dpn, dpn?.let(::protocolName), voltage),
            supported,
        )
    }

    private suspend fun discoverPids(first: ElmResult.Data): Set<Int> {
        val supported = PidDecoder.supportedPids(first, 0x00).toMutableSet()
        var base = 0x00
        while (base + 0x20 in supported && base + 0x20 <= 0xC0) {
            base += 0x20
            val cmd = "01%02X".format(base)
            val r = ElmResponseParser.parseData(channel.exchange(cmd, PID_TIMEOUT_MS), cmd)
            if (r !is ElmResult.Data) break
            supported += PidDecoder.supportedPids(r, base)
        }
        return supported
    }

    private suspend fun text(cmd: String): String? =
        (ElmResponseParser.parseText(channel.exchange(cmd, AT_TIMEOUT_MS), cmd) as? ElmResult.Text)?.text?.trim()

    companion object {
        const val RESET_TIMEOUT_MS = 4_000L
        const val AT_TIMEOUT_MS = 1_500L
        const val SEARCH_TIMEOUT_MS = 12_000L
        const val PID_TIMEOUT_MS = 2_000L

        fun parseVoltage(text: String): Double? = Regex("""(\d{1,2}(?:\.\d+)?)\s*V""").find(text.uppercase())?.groupValues?.get(1)?.toDoubleOrNull()

        fun protocolName(number: String): String = when (number.uppercase()) {
            "1" -> "SAE J1850 PWM"
            "2" -> "SAE J1850 VPW"
            "3" -> "ISO 9141-2"
            "4" -> "ISO 14230-4 KWP (5 baud)"
            "5" -> "ISO 14230-4 KWP (fast)"
            "6" -> "ISO 15765-4 CAN 11/500"
            "7" -> "ISO 15765-4 CAN 29/500"
            "8" -> "ISO 15765-4 CAN 11/250"
            "9" -> "ISO 15765-4 CAN 29/250"
            "A" -> "SAE J1939 CAN"
            else -> "Protocol $number"
        }
    }
}
