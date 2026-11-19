package stapel.network

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One request as the server saw it. */
internal class SeenRequest(val method: String, val path: String, val headers: List<Pair<String, String>>) {
    fun header(name: String): String? = headers.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second
    fun has(name: String): Boolean = headers.any { it.first.equals(name, ignoreCase = true) }
}

/**
 * An in-process HTTP server on a real loopback socket, used to observe what each engine actually
 * puts on the wire. Runs in the test process on jvm and in the iOS simulator test binary.
 */
internal class WireServer private constructor(private val server: EmbeddedServer<*, *>, val port: Int, private val log: MutableList<SeenRequest>, private val lock: Mutex) {
    val base: String get() = "http://127.0.0.1:$port"

    suspend fun seen(): List<SeenRequest> = lock.withLock { log.toList() }

    suspend fun stop() {
        server.stopSuspend(gracePeriodMillis = 0, timeoutMillis = 500)
    }

    companion object {
        suspend fun start(): WireServer {
            val log = mutableListOf<SeenRequest>()
            val lock = Mutex()
            suspend fun record(call: ApplicationCall) {
                val headers = call.request.headers.entries().flatMap { (k, vs) -> vs.map { k to it } }
                lock.withLock { log += SeenRequest(call.request.local.method.value, call.request.local.uri, headers) }
            }
            val server = embeddedServer(CIO, port = 0, host = "127.0.0.1") {
                routing {
                    get("/set-cookie") {
                        record(call)
                        call.response.header("Set-Cookie", "stapel_jwt=stale-cookie-token; Path=/")
                        call.response.header("X-Request-ID", "srv-1")
                        call.respondText("{}", io.ktor.http.ContentType.Application.Json)
                    }
                    get("/echo") {
                        record(call)
                        call.respondText("{\"ok\":true}", io.ktor.http.ContentType.Application.Json)
                    }
                    post("/echo") {
                        record(call)
                        call.respondText("{\"ok\":true}", io.ktor.http.ContentType.Application.Json)
                    }
                    get("/redirect") {
                        record(call)
                        call.response.header("Location", "/echo")
                        call.respondText("", status = HttpStatusCode.Found)
                    }
                    get("/stall") {
                        record(call)
                        delay(5_000)
                        call.respondText("late")
                    }
                }
            }
            server.startSuspend(wait = false)
            val port = server.engine.resolvedConnectors().first().port
            return WireServer(server, port, log, lock)
        }
    }
}
