package com.jacdrive.obd.pid

import com.jacdrive.obd.elm.ElmResult

/** Decodes Mode 01 responses. Returns null when no answering ECU sent a complete, matching reply. */
object PidDecoder {

    /** Extracts the data bytes following `41 <pid>` from the first matching response line. */
    fun payload(result: ElmResult.Data, pid: Int, bytes: Int): IntArray? = result.lines.firstNotNullOfOrNull { line ->
        val i = (0 until line.size - 1).firstOrNull { line[it] == 0x41 && line[it + 1] == pid } ?: return@firstNotNullOfOrNull null
        val start = i + 2
        if (line.size - start < bytes) null else line.copyOfRange(start, start + bytes)
    }

    fun decode(pid: ObdPid, result: ElmResult.Data): Double? = payload(result, pid.code, pid.bytes)?.let(pid::decode)

    /**
     * Parses a "PIDs supported" bitmap reply for the range starting at [base] (0x00, 0x20, 0x40…).
     * Bit 7 of the first byte = PID base+1, …, bit 0 of the last byte = PID base+0x20.
     * The union over all answering ECUs is returned.
     */
    fun supportedPids(result: ElmResult.Data, base: Int): Set<Int> {
        val out = mutableSetOf<Int>()
        for (line in result.lines) {
            val p = payload(ElmResult.Data(listOf(line)), base, 4) ?: continue
            for (byteIndex in 0 until 4) for (bit in 0 until 8) {
                if (p[byteIndex] and (0x80 ushr bit) != 0) out += base + byteIndex * 8 + bit + 1
            }
        }
        return out
    }

    /** Mode 01 PID 01: MIL state and number of stored emission DTCs. */
    data class MonitorStatus(val milOn: Boolean, val dtcCount: Int)

    fun monitorStatus(result: ElmResult.Data): MonitorStatus? =
        payload(result, 0x01, 4)?.let { MonitorStatus(milOn = it[0] and 0x80 != 0, dtcCount = it[0] and 0x7F) }
}
