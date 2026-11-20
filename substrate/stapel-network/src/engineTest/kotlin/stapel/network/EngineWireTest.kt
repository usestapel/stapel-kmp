package stapel.network

import kotlinx.coroutines.test.runTest
import stapel.result.TimeoutPhase
import stapel.result.TransportFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Gate G-N3/G-N4 against the real engine of this target ([engineName]): what leaves the client on a
 * real socket. The subject is the engine configuration, so no fake transport is involved.
 */
class EngineWireTest {
    private suspend fun withServer(block: suspend (WireServer) -> Unit) {
        val server = WireServer.start()
        try {
            block(server)
        } finally {
            server.stop()
        }
    }

    private fun get(url: String, vararg headers: Pair<String, String>) = WireRequest("GET", url, headers.toList())

    @Test
    fun noCookieEverLeavesTheClient() = runTest(timeout = 60.seconds) {
        withServer { server ->
            // Prime every store a careless client could read from (on iOS: the shared cookie storage).
            cookieKeepingTestTransport().use { it.send(get("${server.base}/set-cookie")) }
            platformTestTransport().use { transport ->
                val first = transport.send(get("${server.base}/set-cookie"))
                assertIs<WireOutcome.Response>(first)
                assertEquals(200, first.status)
                assertNull(first.header("Set-Cookie"), "Set-Cookie must not reach the pipeline")
                assertEquals("srv-1", first.header("X-Request-ID"))
                val second = transport.send(get("${server.base}/echo", "Authorization" to "Bearer fresh"))
                assertIs<WireOutcome.Response>(second)
            }
            val seen = server.seen().drop(1)
            assertEquals(2, seen.size)
            for (request in seen) {
                assertFalse(request.has("Cookie"), "$engineName sent a Cookie header: ${request.header("Cookie")}")
            }
            assertEquals("Bearer fresh", seen[1].header("Authorization"))
        }
    }

    /** Inversion: the harness does see a cookie when the client keeps one. */
    @Test
    fun harnessDetectsACookieKeepingClient() = runTest(timeout = 60.seconds) {
        withServer { server ->
            cookieKeepingTestTransport().use { transport ->
                transport.send(get("${server.base}/set-cookie"))
                transport.send(get("${server.base}/echo"))
            }
            val seen = server.seen()
            assertEquals("stapel_jwt=stale-cookie-token", seen[1].header("Cookie"))
        }
    }

    /** Inversion with Ktor's own cookie plug-in on top of the hardened engine: also visible. */
    @Test
    fun harnessDetectsKtorCookiePlugin() = runTest(timeout = 60.seconds) {
        withServer { server ->
            platformTestTransport(ktorCookies = true).use { transport ->
                transport.send(get("${server.base}/set-cookie"))
                transport.send(get("${server.base}/echo"))
            }
            assertEquals("stapel_jwt=stale-cookie-token", server.seen()[1].header("Cookie"))
        }
    }

    @Test
    fun aMultipartUploadReportsItsProgressUpToTheWholeBody() = runTest(timeout = 60.seconds) {
        withServer { server ->
            val file = ByteArray(256 * 1024) { it.toByte() }
            val progress = mutableListOf<Pair<Long, Long?>>()
            platformTestTransport().use { transport ->
                val outcome = transport.send(
                    WireRequest(
                        "POST", "${server.base}/echo", emptyList(),
                        WireBody.Multipart(listOf(WirePart.Field("kind", "avatar"), WirePart.File("file", "a.bin", null, file))),
                        onUpload = { sent, total -> progress += sent to total },
                    ),
                )
                assertIs<WireOutcome.Response>(outcome)
                assertEquals(200, outcome.status)
            }
            assertTrue(progress.isNotEmpty(), "$engineName reported no upload progress")
            val (sent, total) = progress.last()
            assertTrue(sent > file.size, "the whole multipart body was reported sent: $sent")
            assertTrue(total == null || total == sent, "the last report covers the whole body: $sent of $total")
            assertEquals(progress.map { it.first }.sorted(), progress.map { it.first }, "progress only grows")
        }
    }

    @Test
    fun redirectsAreNotFollowed() = runTest(timeout = 60.seconds) {
        withServer { server ->
            val outcome = platformTestTransport().use { it.send(get("${server.base}/redirect", "Authorization" to "Bearer t")) }
            assertIs<WireOutcome.Response>(outcome)
            assertEquals(302, outcome.status)
            assertEquals(listOf("/redirect"), server.seen().map { it.path })
        }
    }

    @Test
    fun headersAndBodyGoOutAsGiven() = runTest(timeout = 60.seconds) {
        withServer { server ->
            val outcome = platformTestTransport().use {
                it.send(
                    WireRequest(
                        "POST",
                        "${server.base}/echo",
                        listOf("X-Request-ID" to "call-1", "Accept-Language" to "ru", "User-Agent" to "app/1 stapel-kmp/0"),
                        WireBody.Bytes("{\"a\":1}".encodeToByteArray(), "application/json"),
                    ),
                )
            }
            assertIs<WireOutcome.Response>(outcome)
            val seen = server.seen().single()
            assertEquals("call-1", seen.header("X-Request-ID"))
            assertEquals("ru", seen.header("Accept-Language"))
            assertEquals("app/1 stapel-kmp/0", seen.header("User-Agent"))
            assertTrue(seen.header("Content-Type").orEmpty().startsWith("application/json"))
            assertFalse(seen.has("Cookie"))
        }
    }

    @Test
    fun stalledResponseTimesOut() = runTest(timeout = 60.seconds) {
        withServer { server ->
            val outcome = platformTestTransport().use {
                it.send(
                    WireRequest(
                        "GET",
                        "${server.base}/stall",
                        emptyList(),
                        timeouts = TimeoutPolicy(connect = 2.seconds, socket = 1.seconds, request = 1.seconds),
                    ),
                )
            }
            assertIs<WireOutcome.Failure>(outcome)
            val error = outcome.error
            assertIs<TransportFailure.Timeout>(error)
            assertTrue(error.phase == TimeoutPhase.Socket || error.phase == TimeoutPhase.Request, "got $error")
            assertTrue(outcome.requestSent)
        }
    }

    @Test
    fun refusedConnectionIsNotSent() = runTest(timeout = 60.seconds) {
        val server = WireServer.start()
        val base = server.base
        server.stop()
        val outcome = platformTestTransport().use {
            it.send(WireRequest("POST", "$base/echo", emptyList(), timeouts = TimeoutPolicy(connect = 500.milliseconds)))
        }
        assertIs<WireOutcome.Failure>(outcome)
        assertIs<TransportFailure.ConnectionFailed>(outcome.error, outcome.toString())
        assertFalse(outcome.requestSent)
    }
}
