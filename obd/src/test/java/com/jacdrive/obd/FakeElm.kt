package com.jacdrive.obd

import com.jacdrive.obd.elm.ElmChannel

/**
 * Behavioural ELM327 emulator (spaces off, headers off after init) for protocol tests.
 * [pidData] maps PID code → data bytes (hex) the ECU returns.
 */
class FakeElm(
    val pidData: MutableMap<Int, String> = mutableMapOf(
        0x04 to "80", 0x05 to "7B", 0x0C to "1AF8", 0x0D to "3C", 0x0F to "41", 0x11 to "33", 0x10 to "028F",
    ),
    /** Supported-PID bitmaps per range base. 0x00 → 04,05,0C,0D,0F,10,11 (+ 0x20 next-range bit). */
    val supportMasks: Map<Int, String> = mapOf(0x00 to "181B8001", 0x20 to "00000000"),
    var ignitionOff: Boolean = false,
    var silent: Boolean = false,
    /** PIDs advertised as supported but answering NO DATA. */
    val noDataPids: Set<Int> = emptySet(),
) : ElmChannel {
    val log = mutableListOf<String>()
    var dropAfter: Int = Int.MAX_VALUE

    override suspend fun exchange(command: String, timeoutMs: Long): String {
        log += command
        if (silent || log.size > dropAfter) return ""
        return respond(command.uppercase()) + "\r\r>"
    }

    private fun respond(cmd: String): String = when {
        cmd == "ATZ" -> "\r\rELM327 v1.5"
        cmd == "ATI" -> "ELM327 v1.5"
        cmd == "ATDPN" -> "A6"
        cmd == "ATRV" -> "12.6V"
        cmd.startsWith("AT") -> "OK"
        cmd.startsWith("01") -> {
            val pid = cmd.substring(2, 4).toInt(16)
            when {
                ignitionOff -> "SEARCHING...\rUNABLE TO CONNECT"
                pid % 0x20 == 0 -> supportMasks[pid]?.let { "41%02X$it".format(pid) } ?: "NO DATA"
                pid in noDataPids -> "NO DATA"
                else -> pidData[pid]?.let { "41%02X$it".format(pid) } ?: "NO DATA"
            }
        }
        else -> "?"
    }
}
