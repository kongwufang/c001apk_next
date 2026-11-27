package com.example.c001apk.util

import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.TimeUnit

/** The entire request is one request line and a blank line; no HTTP client defaults. */
internal object MiuiConnectivityProbe {
    const val HOST = "connect.rom.miui.com"
    const val PATH = "/generate_204"
    const val TIMEOUT_MS = 5_000
    internal const val REQUEST = "GET http://$HOST$PATH HTTP/1.0\r\n\r\n"

    fun check(timeoutMs: Int = TIMEOUT_MS): Boolean {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs.toLong())
        fun remaining(): Int = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
            .coerceIn(0L, timeoutMs.toLong()).toInt()
        val addresses = try {
            InetAddress.getAllByName(HOST)
        } catch (_: IOException) {
            return false
        }
        for (address in addresses) {
            val time = remaining()
            if (time == 0) return false
            try {
                Socket(Proxy.NO_PROXY).use { socket ->
                    socket.connect(InetSocketAddress(address, 80), minOf(time, 2_000))
                    val readBudget = remaining()
                    if (readBudget == 0) return false
                    socket.soTimeout = readBudget
                    return exchange(socket) {
                        socket.soTimeout = remaining().takeIf { it > 0 } ?: throw IOException("Probe deadline")
                    }
                }
            } catch (_: IOException) {
                // A failed route can fall back to another resolved address within the same deadline.
            }
        }
        return false
    }

    internal fun exchange(socket: Socket, beforeRead: () -> Unit = {}): Boolean {
        socket.getOutputStream().apply {
            write(REQUEST.toByteArray(Charsets.US_ASCII))
            flush()
        }
        return isNoContent(socket.getInputStream().buffered(), beforeRead)
    }

    private val status204 = Regex("HTTP/1\\.[01] 204(?: [ -~]*)?")

    internal fun isNoContent(input: InputStream, beforeRead: () -> Unit = {}): Boolean {
        val line = StringBuilder()
        repeat(512) {
            beforeRead()
            val c = input.read()
            if (c == '\r'.code) {
                beforeRead()
                return input.read() == '\n'.code && status204.matches(line)
            }
            if (c !in 32..126) return false
            line.append(c.toChar())
        }
        return false
    }
}
