package com.example.c001apk.util

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class MiuiConnectivityProbeTest {
    @Test fun socketWritesOnlyTheAbsoluteRequestLineAndBlankLine() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                server.soTimeout = 2_000
                val received = executor.submit<String> {
                    server.accept().use { socket ->
                        socket.soTimeout = 2_000
                        val input = socket.getInputStream()
                        // Closing the client write half lets us inspect all bytes, even extra headers.
                        val bytes = input.readBytes()
                        socket.getOutputStream().write("HTTP/1.1 204 No Content\r\n\r\n".toByteArray())
                        bytes.toString(Charsets.US_ASCII)
                    }
                }
                Socket(server.inetAddress, server.localPort).use { socket ->
                    socket.soTimeout = 2_000
                    val wrapper = object : Socket() {
                        override fun getInputStream() = socket.getInputStream()
                        override fun getOutputStream() = object : java.io.FilterOutputStream(socket.getOutputStream()) {
                            override fun flush() {
                                super.flush()
                                socket.shutdownOutput()
                            }
                        }
                    }
                    assertTrue(MiuiConnectivityProbe.exchange(wrapper))
                }
                assertEquals("GET http://connect.rom.miui.com/generate_204 HTTP/1.0\r\n\r\n",
                    received.get(2, TimeUnit.SECONDS))
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test fun onlyAnActual204StatusIsOnline() {
        fun check(response: String) = MiuiConnectivityProbe.isNoContent(response.byteInputStream())
        assertTrue(check("HTTP/1.1 204 No Content\r\n\r\n"))
        assertTrue(check("HTTP/1.0 204\r\n\r\n"))
        assertFalse(check("HTTP/1.1 200 OK\r\n\r\n"))
        assertFalse(check("HTTP/1.1 302 Found\r\nLocation: /portal\r\n\r\nHTTP/1.1 204 No Content\r\n"))
        assertFalse(check("HTTP/1.1 403 Forbidden\r\n\r\n"))
        assertFalse(check("HTTP/1.1 2040 Nope\r\n\r\n"))
        assertFalse(check("HTTP/1.1 204"))
        assertFalse(check("HTTP/1.1 204 " + "a".repeat(512)))
    }

    @Test fun reportsFinalHttpFailureButNotSuccessfulRedirectOrCancellation() {
        MockWebServer().use { server ->
            var count = 0
            val client = OkHttpClient.Builder().eventListenerFactory {
                NetworkHealth.FailureListener { count++ }
            }.build()
            val request = Request.Builder().url(server.url("/test")).build()
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/ok"))
            server.enqueue(MockResponse().setResponseCode(204))
            client.newCall(request).execute().use { assertEquals(204, it.code) }
            assertEquals(0, count)
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
            client.newCall(request).execute().use { assertEquals(503, it.code) }
            assertEquals(1, count)
            val canceled = client.newCall(request)
            canceled.cancel()
            assertTrue(runCatching { canceled.execute().close() }.exceptionOrNull() is IOException)
            assertEquals(1, count)

            val oneAttempt = client.newBuilder().retryOnConnectionFailure(false).build()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            assertTrue(runCatching { oneAttempt.newCall(request).execute().close() }.exceptionOrNull() is IOException)
            assertEquals(2, count)
        }
    }
}
