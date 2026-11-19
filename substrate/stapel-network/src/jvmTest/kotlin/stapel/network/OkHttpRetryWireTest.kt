package stapel.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

/**
 * OkHttp's own `retryOnConnectionFailure` is off: a POST whose connection drops after the request
 * was written is not silently re-sent by the engine (the pipeline alone decides retries).
 */
class OkHttpRetryWireTest {
    /**
     * A raw socket server. Connection 1 answers its first request with keep-alive, then reads the
     * second request and hangs up without answering (a keep-alive connection dying mid-exchange).
     * Any later connection answers 200 and closes. [requests] counts requests read off the wire.
     */
    private class DroppingServer : AutoCloseable {
        val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        val requests = AtomicInteger()
        private val connections = AtomicInteger()
        private val worker = thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatchingIo { socket.accept() } ?: break
                val first = connections.incrementAndGet() == 1
                client.use { c ->
                    val input = c.getInputStream().bufferedReader()
                    val output = c.getOutputStream()
                    fun readRequest(): Boolean {
                        var line = input.readLine() ?: return false
                        var length = 0
                        while (line.isNotEmpty()) {
                            if (line.startsWith("Content-Length:", ignoreCase = true)) length = line.substringAfter(':').trim().toInt()
                            line = input.readLine() ?: return false
                        }
                        repeat(length) { input.read() }
                        requests.incrementAndGet()
                        return true
                    }
                    if (first) {
                        if (readRequest()) {
                            output.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}".toByteArray())
                            output.flush()
                        }
                        readRequest()
                    } else if (readRequest()) {
                        output.write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\n{}".toByteArray())
                        output.flush()
                    }
                }
            }
        }

        val base get() = "http://127.0.0.1:${socket.localPort}"

        override fun close() {
            socket.close()
            worker.join(1_000)
        }
    }

    private fun post(base: String) = WireRequest(
        "POST",
        "$base/otp",
        emptyList(),
        WireBody.Bytes("{}".encodeToByteArray(), "application/json"),
        TimeoutPolicy(connect = 2.seconds, socket = 2.seconds, request = 5.seconds),
    )

    @Test
    fun droppedPostIsNotResentByTheEngine() = runTest(timeout = 30.seconds) {
        DroppingServer().use { server ->
            val outcome = withContext(Dispatchers.IO) {
                okHttpTransport(CertificatePins.None).use {
                    assertIs<WireOutcome.Response>(it.send(post(server.base)))
                    it.send(post(server.base))
                }
            }
            assertIs<WireOutcome.Failure>(outcome)
            assertEquals(true, outcome.requestSent)
            assertEquals(2, server.requests.get(), "the engine re-sent the request on its own")
        }
    }

    /** Inversion: with Ktor's engine defaults OkHttp does re-send, and this harness sees it. */
    @Test
    fun harnessDetectsEngineDefaultRetry() = runTest(timeout = 30.seconds) {
        DroppingServer().use { server ->
            val outcome = withContext(Dispatchers.IO) {
                okHttpTransport(CertificatePins.None, hardened = false).use {
                    assertIs<WireOutcome.Response>(it.send(post(server.base)))
                    it.send(post(server.base))
                }
            }
            assertIs<WireOutcome.Response>(outcome)
            assertEquals(3, server.requests.get())
        }
    }
}

private inline fun <T> runCatchingIo(block: () -> T): T? = try {
    block()
} catch (_: java.io.IOException) {
    null
}
