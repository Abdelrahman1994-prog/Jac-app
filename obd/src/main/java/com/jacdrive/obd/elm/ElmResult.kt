package com.jacdrive.obd.elm

/** Outcome of one ELM327 command. Keeps "the ECU has no answer" apart from "the link is broken". */
sealed interface ElmResult {
    /** Response bytes, one entry per response line (one line per answering ECU / CAN frame). */
    data class Data(val lines: List<IntArray>) : ElmResult

    /** "OK" or other plain-text answer to an AT command. */
    data class Text(val text: String) : ElmResult

    /** ECU understood the request but has nothing to report (`NO DATA`). */
    data object NoData : ElmResult

    /** The adapter did not understand the command (`?`). */
    data object NotUnderstood : ElmResult

    /** No prompt received within the timeout. */
    data object Timeout : ElmResult

    data class Error(val kind: ElmErrorKind, val raw: String) : ElmResult
}

enum class ElmErrorKind {
    /** No vehicle protocol found — usually ignition off or wrong port. */
    UNABLE_TO_CONNECT,
    CAN_ERROR,
    BUS_ERROR,
    BUS_BUSY,
    BUFFER_FULL,
    /** Request interrupted (e.g. a character arrived while the adapter was busy). */
    STOPPED,
    FB_ERROR,
    DATA_ERROR,
    LOW_POWER,
    UNKNOWN,
}

/** True for failures of the communication link itself (as opposed to the ECU lacking data). */
val ElmResult.isLinkFailure: Boolean
    get() = this is ElmResult.Timeout || (this is ElmResult.Error && kind != ElmErrorKind.UNABLE_TO_CONNECT)
