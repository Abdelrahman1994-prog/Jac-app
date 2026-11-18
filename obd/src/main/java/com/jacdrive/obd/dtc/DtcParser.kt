package com.jacdrive.obd.dtc

import com.jacdrive.obd.elm.ElmResponseParser
import com.jacdrive.obd.elm.ElmResult

sealed interface DtcReadResult {
    data class Codes(val codes: List<String>) : DtcReadResult

    /** The ECU does not implement this mode (e.g. Mode 0A on older vehicles). */
    data object NotSupported : DtcReadResult
    data class Failed(val result: ElmResult) : DtcReadResult
}

/**
 * Parses Mode 03 / 07 / 0A replies.
 *
 * CAN (ISO 15765-4): every ECU message is `4x NN [A B]×NN`, either on one line or as an ISO-TP
 * multi-frame block (`00A` length line, then `0:` `1:` … frames).
 * Legacy protocols (J1850, ISO 9141, KWP): `4x` followed by exactly three code pairs per line,
 * padded with `00 00`.
 * Codes from several ECUs are merged and de-duplicated, preserving order.
 */
object DtcParser {

    private val frameIndex = Regex("^([0-9A-F]):\\s*(.*)$")
    private val lengthLine = Regex("^[0-9A-F]{3}$")

    fun parse(raw: String, type: DtcType, isCan: Boolean): DtcReadResult {
        val command = type.mode
        val lines = ElmResponseParser.cleanLines(raw, command)
        // Negative response "7F <mode> <NRC>" from every ECU: the mode is not implemented.
        val compact = lines.map { it.replace(" ", "") }
        if (compact.isNotEmpty() && compact.all { it.startsWith("7F") }) return DtcReadResult.NotSupported
        when (val check = ElmResponseParser.parseData(raw, command)) {
            is ElmResult.Data -> Unit
            ElmResult.NoData -> return if (type == DtcType.PERMANENT) DtcReadResult.NotSupported else DtcReadResult.Codes(emptyList())
            ElmResult.NotUnderstood -> return DtcReadResult.NotSupported
            else -> return DtcReadResult.Failed(check)
        }

        val messages = if (isCan) canMessages(lines) else lines.map(::bytes)
        val codes = LinkedHashSet<String>()
        for (msg in messages) {
            if (msg.isEmpty() || msg[0] != type.responseService) continue
            if (isCan) {
                val count = msg.getOrNull(1) ?: continue
                for (i in 0 until count) {
                    val a = msg.getOrNull(2 + i * 2) ?: break
                    val b = msg.getOrNull(3 + i * 2) ?: break
                    if (a != 0 || b != 0) codes += DtcCodec.decode(a, b)
                }
            } else {
                var i = 1
                while (i + 1 < msg.size) {
                    val a = msg[i]
                    val b = msg[i + 1]
                    if (a != 0 || b != 0) codes += DtcCodec.decode(a, b)
                    i += 2
                }
            }
        }
        return DtcReadResult.Codes(codes.toList())
    }

    private fun canMessages(lines: List<String>): List<IntArray> {
        val out = mutableListOf<IntArray>()
        var current: MutableList<Int>? = null
        var expected = -1
        fun flush() {
            current?.let { out += (if (expected > 0) it.take(expected) else it).toIntArray() }
            current = null
            expected = -1
        }
        for (line in lines) {
            val compact = line.replace(" ", "")
            val frame = frameIndex.find(line.trim())
            when {
                lengthLine.matches(compact) -> { flush(); expected = compact.toInt(16); current = mutableListOf() }
                frame != null -> {
                    if (frame.groupValues[1] == "0" && current == null) current = mutableListOf()
                    current?.addAll(bytes(frame.groupValues[2]).toList())
                }
                else -> { flush(); out += bytes(line) }
            }
        }
        flush()
        return out
    }

    private fun bytes(line: String): IntArray {
        val c = line.replace(" ", "")
        if (c.length % 2 != 0 || c.any { it !in "0123456789ABCDEF" }) return IntArray(0)
        return IntArray(c.length / 2) { c.substring(it * 2, it * 2 + 2).toInt(16) }
    }
}
