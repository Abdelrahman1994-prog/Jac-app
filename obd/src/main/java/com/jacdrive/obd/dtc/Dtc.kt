package com.jacdrive.obd.dtc

/** Which list a trouble code was read from. */
enum class DtcType(val mode: String, val responseService: Int) {
    /** Confirmed codes that turned on (or would turn on) the MIL — Mode 03. */
    STORED("03", 0x43),

    /** Detected in the current or last drive cycle, not yet confirmed — Mode 07. */
    PENDING("07", 0x47),

    /** Cannot be erased by a scan tool; clear themselves after the fault is repaired — Mode 0A. */
    PERMANENT("0A", 0x4A),
}

object DtcCodec {
    private val systems = charArrayOf('P', 'C', 'B', 'U')

    /** Decodes the two-byte SAE J2012 representation, e.g. 0x01 0x33 → "P0133". */
    fun decode(a: Int, b: Int): String {
        val letter = systems[(a shr 6) and 0x03]
        val d1 = (a shr 4) and 0x03
        val d2 = a and 0x0F
        val d3 = (b shr 4) and 0x0F
        val d4 = b and 0x0F
        return "%c%d%X%X%X".format(letter, d1, d2, d3, d4)
    }

    private val pattern = Regex("^[PCBU][0-3][0-9A-F]{3}$")

    fun isValid(code: String): Boolean = pattern.matches(code.uppercase())
}
