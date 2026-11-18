package com.jacdrive.obd.elm

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Sends one command to the ELM327 and returns its raw output up to the `>` prompt. */
interface ElmChannel {
    /** @return raw text, or an empty string if no prompt arrived within [timeoutMs]. */
    suspend fun exchange(command: String, timeoutMs: Long): String
}

/**
 * Stream-based channel shared by every byte transport (Bluetooth Classic now, BLE later).
 * Commands are strictly serialised: ELM327 processes one request at a time.
 */
class StreamElmChannel(
    private val input: InputStream,
    private val output: OutputStream,
    private val io: CoroutineDispatcher,
    private val clock: () -> Long = System::currentTimeMillis,
) : ElmChannel {

    private val mutex = Mutex()

    override suspend fun exchange(command: String, timeoutMs: Long): String = mutex.withLock {
        withContext(io) {
            try {
                // Discard anything left over from a previous timed-out command.
                while (input.available() > 0) input.read(ByteArray(input.available()))
                output.write("$command\r".toByteArray(Charsets.US_ASCII))
                output.flush()

                val sb = StringBuilder()
                val buffer = ByteArray(256)
                val deadline = clock() + timeoutMs
                while (clock() < deadline) {
                    val n = input.available()
                    if (n > 0) {
                        val read = input.read(buffer, 0, minOf(n, buffer.size))
                        if (read < 0) throw ObdException(ObdFailure.LINK_LOST, "stream closed")
                        sb.append(String(buffer, 0, read, Charsets.US_ASCII))
                        if (sb.contains('>')) return@withContext sb.toString()
                    } else {
                        delay(POLL_MS)
                    }
                }
                "" // timeout
            } catch (e: IOException) {
                throw ObdException(ObdFailure.LINK_LOST, e.message, e)
            }
        }
    }

    private companion object {
        const val POLL_MS = 4L
    }
}
