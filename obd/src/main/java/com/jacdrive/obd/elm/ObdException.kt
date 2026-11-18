package com.jacdrive.obd.elm

/** Why a connection attempt or session ended. Each maps to a user-facing explanation. */
enum class ObdFailure {
    BLUETOOTH_UNAVAILABLE,
    BLUETOOTH_OFF,
    PERMISSION_MISSING,
    SOCKET_FAILED,
    ADAPTER_NOT_RESPONDING,
    /** Adapter is fine but no ECU answered (ignition off, or unsupported vehicle protocol). */
    ECU_NOT_RESPONDING,
    LINK_LOST,
    UNSUPPORTED_TRANSPORT,
}

class ObdException(val failure: ObdFailure, message: String? = null, cause: Throwable? = null) :
    Exception(message ?: failure.name, cause)
