package com.jacdrive.obd.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.jacdrive.obd.elm.ObdException
import com.jacdrive.obd.elm.ObdFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** RFCOMM Serial Port Profile link, as used by ELM327 Bluetooth Classic adapters. */
class BluetoothClassicTransport(
    private val context: Context,
    private val address: String,
    private val io: CoroutineDispatcher,
) : ObdTransport {

    private var socket: BluetoothSocket? = null

    override val input: InputStream get() = socket?.inputStream ?: throw ObdException(ObdFailure.LINK_LOST, "not open")
    override val output: OutputStream get() = socket?.outputStream ?: throw ObdException(ObdFailure.LINK_LOST, "not open")

    @SuppressLint("MissingPermission") // Checked by BluetoothPermissions before any connection is started.
    override suspend fun open() {
        if (!BluetoothPermissions.hasConnect(context)) throw ObdException(ObdFailure.PERMISSION_MISSING)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw ObdException(ObdFailure.BLUETOOTH_UNAVAILABLE)
        if (!adapter.isEnabled) throw ObdException(ObdFailure.BLUETOOTH_OFF)
        runCatching { adapter.cancelDiscovery() } // discovery slows RFCOMM connects dramatically

        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            throw ObdException(ObdFailure.SOCKET_FAILED, "bad address", e)
        }
        val attempts: List<() -> BluetoothSocket> = listOf(
            { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            { device.createInsecureRfcommSocketToServiceRecord(SPP_UUID) },
        )
        var last: Exception? = null
        for (make in attempts) {
            val s = try { make() } catch (e: IOException) { last = e; continue } catch (e: SecurityException) {
                throw ObdException(ObdFailure.PERMISSION_MISSING, e.message, e)
            }
            try {
                connectCancellable(s)
                socket = s
                return
            } catch (e: CancellationException) {
                runCatching { s.close() }
                throw e
            } catch (e: IOException) {
                last = e
                runCatching { s.close() }
            }
        }
        throw ObdException(ObdFailure.SOCKET_FAILED, last?.message, last)
    }

    /** `BluetoothSocket.connect()` blocks; closing the socket is the only way to abort it. */
    @SuppressLint("MissingPermission")
    private suspend fun connectCancellable(s: BluetoothSocket) = coroutineScope {
        val job = async(io) { s.connect() }
        try {
            job.await()
        } catch (e: CancellationException) {
            runCatching { s.close() }
            throw e
        }
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    companion object {
        /** Standard Serial Port Profile UUID. */
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
