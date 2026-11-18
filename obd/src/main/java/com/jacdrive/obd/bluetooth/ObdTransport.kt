package com.jacdrive.obd.bluetooth

import java.io.InputStream
import java.io.OutputStream

enum class TransportType { BLUETOOTH_CLASSIC, BLE }

/** An OBD adapter the user can connect to. */
data class AdapterDevice(
    val address: String,
    val name: String?,
    val type: TransportType = TransportType.BLUETOOTH_CLASSIC,
    val bonded: Boolean = false,
) {
    /** Heuristic used only to sort the list; any device can be tried. */
    val looksLikeObd: Boolean
        get() = name?.let { OBD_NAME.containsMatchIn(it) } == true

    private companion object {
        val OBD_NAME = Regex("OBD|ELM|V-?LINK|VEEPEAK|VGATE|ICAR|KONNWEI|CAN ?BUS|SCAN", RegexOption.IGNORE_CASE)
    }
}

/**
 * A byte-stream link to an adapter. Bluetooth Classic (SPP) is implemented; a BLE GATT
 * implementation only needs to expose the same streams.
 */
interface ObdTransport {
    suspend fun open()
    val input: InputStream
    val output: OutputStream
    fun close()
}

fun interface ObdTransportFactory {
    fun create(device: AdapterDevice): ObdTransport
}
