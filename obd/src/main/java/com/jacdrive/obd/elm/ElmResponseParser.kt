package com.jacdrive.obd.elm

/**
 * Parses raw ELM327 output (everything up to the `>` prompt).
 *
 * Handles: echo still on, `SEARCHING...`, `BUS INIT: ...OK`, spaces on/off, several ECUs
 * answering, ISO 15765 multi-frame output (`00A` length line followed by `0:`/`1:` frames)
 * and all standard error strings.
 */
object ElmResponseParser {

    private val errors = listOf(
        "UNABLE TO CONNECT" to ElmErrorKind.UNABLE_TO_CONNECT,
        "CAN ERROR" to ElmErrorKind.CAN_ERROR,
        "BUS ERROR" to ElmErrorKind.BUS_ERROR,
        "BUS BUSY" to ElmErrorKind.BUS_BUSY,
        "BUFFER FULL" to ElmErrorKind.BUFFER_FULL,
        "STOPPED" to ElmErrorKind.STOPPED,
        "FB ERROR" to ElmErrorKind.FB_ERROR,
        "DATA ERROR" to ElmErrorKind.DATA_ERROR,
        "<DATA ERROR" to ElmErrorKind.DATA_ERROR,
        "LV RESET" to ElmErrorKind.LOW_POWER,
        "LP ALERT" to ElmErrorKind.LOW_POWER,
        "ERR" to ElmErrorKind.UNKNOWN,
    )

    private val hex = Regex("^[0-9A-F]+$")
    private val frameIndex = Regex("^[0-9A-F]:")
    private val lengthLine = Regex("^[0-9A-F]{3}$")

    fun cleanLines(raw: String, command: String): List<String> {
        val cmd = command.trim().uppercase().replace(" ", "")
        return raw.replace(">", "")
            .split('\r', '\n')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }
            .filterNot { it.replace(" ", "") == cmd } // echo
            .filterNot { it.startsWith("SEARCHING") || it.startsWith("BUS INIT") }
    }

    /** Parse the answer to an OBD request (hex response expected). */
    fun parseData(raw: String, command: String): ElmResult {
        val lines = cleanLines(raw, command)
        if (lines.isEmpty()) return ElmResult.Timeout
        classifyNonData(lines)?.let { return it }

        val out = mutableListOf<IntArray>()
        for (line in lines) {
            var l = line
            if (lengthLine.matches(l.replace(" ", ""))) continue // multi-frame byte count
            if (frameIndex.containsMatchIn(l)) l = l.substringAfter(':')
            val compact = l.replace(" ", "")
            if (compact.isEmpty()) continue
            if (compact.length % 2 != 0 || !hex.matches(compact)) {
                return ElmResult.Error(ElmErrorKind.DATA_ERROR, raw)
            }
            out += IntArray(compact.length / 2) { i -> compact.substring(i * 2, i * 2 + 2).toInt(16) }
        }
        return if (out.isEmpty()) ElmResult.NoData else ElmResult.Data(out)
    }

    /** Parse the answer to an AT command (text expected). */
    fun parseText(raw: String, command: String): ElmResult {
        val lines = cleanLines(raw, command)
        if (lines.isEmpty()) return ElmResult.Timeout
        classifyNonData(lines)?.let { return it }
        return ElmResult.Text(lines.joinToString("\n"))
    }

    /**
     * Joins ISO 15765 multi-frame output into one message. ELM prints frame 0 with the first data
     * bytes and continuation frames after; with headers off they are already in order.
     */
    fun joinFrames(result: ElmResult.Data): IntArray = result.lines.fold(IntArray(0)) { acc, l -> acc + l }

    private fun classifyNonData(lines: List<String>): ElmResult? {
        if (lines.any { it == "?" }) return ElmResult.NotUnderstood
        for ((text, kind) in errors) {
            if (lines.any { it.startsWith(text) }) return ElmResult.Error(kind, lines.joinToString(" "))
        }
        if (lines.all { it.replace(" ", "") == "NODATA" }) return ElmResult.NoData
        return null
    }
}
