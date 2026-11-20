package stapel.image.compose

import coil3.PlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import stapel.network.RefreshVerdict
import stapel.network.RequestAuthenticator
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** A 1×1 PNG. */
private val PNG = Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==")

private class Seen(val host: String, val path: String, val authorization: String?)

/**
 * One loopback server reached under two names: `localhost` plays the app's API/media host,
 * `127.0.0.1` a foreign host (a CDN, someone else's link). What the platform engine of this target
 * ([engineName]) puts on the wire is what is asserted.
 */
private class ImageServer(private val server: EmbeddedServer<*, *>, val port: Int, private val log: MutableList<Seen>, private val lock: Mutex) {
    val api: String get() = "http://localhost:$port"
    val foreign: String get() = "http://127.0.0.1:$port"

    suspend fun seen(): List<Seen> = lock.withLock { log.toList() }

    suspend fun stop() = server.stopSuspend(gracePeriodMillis = 0, timeoutMillis = 500)

    companion object {
        suspend fun start(): ImageServer {
            val log = mutableListOf<Seen>()
            val lock = Mutex()
            var port = 0
            suspend fun record(call: ApplicationCall) {
                val host = call.request.headers["Host"].orEmpty().substringBefore(':')
                lock.withLock { log += Seen(host, call.request.local.uri, call.request.headers["Authorization"]) }
            }
            val server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
                routing {
                    get("/img.png") {
                        record(call)
                        call.respondBytes(PNG, ContentType.Image.PNG)
                    }
                    get("/private.png") {
                        record(call)
                        if (call.request.headers["Authorization"] == "Bearer fresh") call.respondBytes(PNG, ContentType.Image.PNG)
                        else call.respondText("", status = HttpStatusCode.Unauthorized)
                    }
                    get("/redirect-out.png") {
                        record(call)
                        call.response.header("Location", "http://127.0.0.1:$port/img.png")
                        call.respondText("", status = HttpStatusCode.Found)
                    }
                }
            }
            server.startSuspend(wait = false)
            port = server.engine.resolvedConnectors().first().port
            return ImageServer(server, port, log, lock)
        }
    }
}

private class Session(var token: String? = "stale") : RequestAuthenticator {
    var refreshes = 0
    override suspend fun accessToken(): String? = token
    override suspend fun refresh(failedToken: String?): RefreshVerdict {
        refreshes++
        token = "fresh"
        return RefreshVerdict.Refreshed("fresh")
    }
}

class ImageWireTest {
    private suspend fun withServer(block: suspend (ImageServer) -> Unit) {
        val server = ImageServer.start()
        try {
            block(server)
        } finally {
            server.stop()
        }
    }

    private fun loader(auth: ImageAuth?, baseUrl: String? = null) = stapelImageLoader(PlatformContext.INSTANCE, testHttpClient(), auth, baseUrl) {
        memoryCachePolicy(CachePolicy.DISABLED)
        diskCachePolicy(CachePolicy.DISABLED)
    }

    private fun request(url: String, authorization: String? = null) = ImageRequest.Builder(PlatformContext.INSTANCE)
        .data(url)
        .apply { if (authorization != null) httpHeaders(NetworkHeaders.Builder().set("Authorization", authorization).build()) }
        .build()

    @Test
    fun theTokenGoesToTheApiHostAndNowhereElse() = runTest(timeout = 60.seconds) {
        withServer { server ->
            val images = loader(ImageAuth(Session("t1"), setOf("localhost")))
            assertIs<SuccessResult>(images.execute(request("${server.api}/img.png")))
            assertIs<SuccessResult>(images.execute(request("${server.foreign}/img.png")))
            val seen = server.seen()
            assertEquals(listOf("localhost", "127.0.0.1"), seen.map { it.host }, engineName)
            assertEquals("Bearer t1", seen[0].authorization, "$engineName: the API host gets the session's token")
            assertNull(seen[1].authorization, "$engineName: a foreign host got ${seen[1].authorization}")
        }
    }

    /** Inversion: the harness sees the header on a host the app does authorize. */
    @Test
    fun harnessSeesTheTokenWhenBothHostsAreAuthorized() = runTest(timeout = 60.seconds) {
        withServer { server ->
            val images = loader(ImageAuth(Session("t1"), setOf("localhost", "127.0.0.1")))
            images.execute(request("${server.foreign}/img.png"))
            assertEquals("Bearer t1", server.seen().single().authorization)
        }
    }

    @Test
    fun anAuthorizationACallerSetIsStrippedForAForeignHost() = runTest(timeout = 60.seconds) {
        withServer { server ->
            val images = loader(ImageAuth(Session("t1"), setOf("localhost")))
            assertIs<SuccessResult>(images.execute(request("${server.foreign}/img.png", authorization = "Bearer leaked")))
            assertNull(server.seen().single().authorization)
        }
    }

    @Test
    fun withoutImageAuthNoRequestCarriesAToken() = runTest(timeout = 60.seconds) {
        withServer { server ->
            val images = loader(auth = null)
            assertIs<SuccessResult>(images.execute(request("${server.api}/img.png")))
            assertNull(server.seen().single().authorization)
        }
    }

    @Test
    fun aRedirectFromTheApiHostIsNotFollowedSoTheTokenStaysHome() = runTest(timeout = 60.seconds) {
        withServer { server ->
            val images = loader(ImageAuth(Session("t1"), setOf("localhost")))
            assertIs<ErrorResult>(images.execute(request("${server.api}/redirect-out.png")))
            assertEquals(listOf("/redirect-out.png"), server.seen().map { it.path }, "$engineName followed the redirect")
        }
    }

    @Test
    fun a401RefreshesOnceAndRetriesWithTheNewToken() = runTest(timeout = 60.seconds) {
        withServer { server ->
            val session = Session("stale")
            val images = loader(ImageAuth(session, setOf("localhost")))
            assertIs<SuccessResult>(images.execute(request("${server.api}/private.png")))
            assertEquals(listOf("Bearer stale", "Bearer fresh"), server.seen().map { it.authorization })
            assertEquals(1, session.refreshes)
        }
    }

    @Test
    fun aHostRelativeUrlIsFetchedFromTheAppsHostWithTheToken() = runTest(timeout = 60.seconds) {
        withServer { server ->
            val images = loader(ImageAuth(Session("t1"), setOf("localhost")), baseUrl = "${server.api}/api/")
            assertIs<SuccessResult>(images.execute(request("/img.png")))
            val seen = server.seen().single()
            assertEquals("localhost" to "/img.png", seen.host to seen.path, engineName)
            assertEquals("Bearer t1", seen.authorization)
        }
    }

    @Test
    fun withoutABaseUrlAHostRelativeUrlFails() = runTest(timeout = 60.seconds) {
        withServer { server ->
            assertIs<ErrorResult>(loader(ImageAuth(Session("t1"), setOf("localhost"))).execute(request("/img.png")))
            assertTrue(server.seen().isEmpty())
        }
    }
}
