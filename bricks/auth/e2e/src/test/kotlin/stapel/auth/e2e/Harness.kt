package stapel.auth.e2e

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import stapel.auth.AuthConfig
import stapel.auth.GuestPolicy
import stapel.auth.StapelAuth
import stapel.auth.api.AuthApi
import stapel.auth.api.AuthApiClient
import stapel.auth.session.AuthTokenRefresher
import stapel.auth.session.StepUpController
import stapel.cache.ManagedStapelCache
import stapel.cache.StapelCache
import stapel.cache.StapelCacheConfig
import stapel.cache.session.CacheSessionBinding
import stapel.captcha.CaptchaPlugin
import stapel.captcha.CaptchaSolver
import stapel.config.ClientInfo
import stapel.config.ClientPlatform
import stapel.config.Posture
import stapel.config.StapelEnvironment
import stapel.mvi.PresenterContext
import stapel.network.CallEvent
import stapel.network.NetworkDependencies
import stapel.network.NetworkPlugin
import stapel.network.StapelTransport
import stapel.runtime.randomIdGenerator
import stapel.session.SessionConfig
import stapel.session.SessionDependencies
import stapel.session.SessionManager
import stapel.session.TokenPair
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.VaultRead
import stapel.wire.WireJson
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The stand under test. */
object Stand {
    val baseUrl: String = System.getProperty("stapel.e2e.baseUrl")
        ?: error("run with -Pstapel.e2e.baseUrl=<stand url> (bricks/auth/e2e/stand/stand.sh up)")
    val captchaBaseUrl: String? = System.getProperty("stapel.e2e.captchaBaseUrl")

    /** The stand's mock OTP code (stapel-auth `MOCK_OTP_CODE` default). */
    const val MOCK_CODE: String = "0000"

    /** Access-token lifetime configured on the stand. */
    val ACCESS_LIFETIME: Duration = 5.seconds

    private val http: HttpClient = HttpClient.newHttpClient()

    /** Server-side counters of the cookie probe (`/e2e/probe/`). */
    fun probe(base: String = baseUrl): JsonObject {
        val response = http.send(HttpRequest.newBuilder(URI(base.trimEnd('/') + "/e2e/probe/")).GET().build(), HttpResponse.BodyHandlers.ofString())
        return Json.parseToJsonElement(response.body()) as JsonObject
    }

    fun cookieRequests(base: String = baseUrl): Int = probe(base)["with_cookie"]!!.jsonPrimitive.int

    /** The `stapel-auth` version installed on the stand (written by stand.sh). */
    fun installedAuthVersion(): String? {
        val file = File(System.getProperty("user.dir"), "build/stand/versions.txt")
        if (!file.isFile) return null
        val lines = file.readLines()
        val idx = lines.indexOf("Name: stapel-auth")
        return lines.getOrNull(idx + 1)?.removePrefix("Version: ")?.trim()
    }

    fun email(): String = "e2e+${UUID.randomUUID()}@example.com"

    /** A fictional NANP number (555-0100…0199, reserved for fiction) the backend's validator accepts. */
    fun phone(): String = "+1202555" + (100..199).random().toString().padStart(4, '0')
}

/** One request as the client's pipeline saw it. */
data class Attempt(val operationId: String, val attempt: Int, val status: Int?, val errorCode: String?)

/** A network plug-in that only observes: the client-side request log. */
class RequestLog : NetworkPlugin {
    override val id: String = "e2e.request-log"
    val attempts: MutableList<Attempt> = CopyOnWriteArrayList()

    override fun observe(event: CallEvent) {
        if (event is CallEvent.Attempt) attempts += Attempt(event.operationId, event.attempt, event.status, event.errorCode)
    }

    fun count(operationId: String): Int = attempts.count { it.operationId == operationId }

    fun clear() = attempts.clear()
}

/**
 * One app installation: the real stack (pipeline over OkHttp, session manager, cache bound to the
 * session, the auth façade) against the stand, with an in-memory vault the test can inspect.
 */
class Device(
    val name: String,
    baseUrl: String = Stand.baseUrl,
    proactiveWindow: Duration = 1.seconds,
    captcha: CaptchaSolver? = null,
) : AutoCloseable {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val log = RequestLog()
    val vault = InMemoryTokenVault()
    private val environment = StapelEnvironment.single(
        name = "e2e",
        baseUrl = baseUrl,
        posture = Posture.Prototype,
        client = ClientInfo(
            appId = "stapel.auth.e2e",
            appVersion = "0.0.0",
            build = "e2e",
            platform = ClientPlatform.Desktop,
            osName = "JVM",
            osVersion = System.getProperty("java.version"),
            deviceModel = name,
            sdkVersion = "e2e",
        ),
    )
    private val deps = NetworkDependencies(environment, Clock.System, randomIdGenerator())
    val bareApi: AuthApi = AuthApiClient(StapelTransport(deps, plugins = listOf(log)))
    val sessions: SessionManager = SessionManager(
        SessionDependencies(vault, scope, Clock.System),
        AuthTokenRefresher(bareApi),
        SessionConfig(proactiveRefreshWindow = proactiveWindow),
    )
    private var authedApi: AuthApi? = null
    val stepUp = StepUpController { checkNotNull(authedApi) }
    val api: AuthApi = AuthApiClient(
        StapelTransport(
            deps,
            authenticator = sessions.authenticator,
            verification = stepUp,
            plugins = listOfNotNull(log, captcha?.let { CaptchaPlugin(it) }),
        ),
    ).also { authedApi = it }
    val cache: ManagedStapelCache = StapelCache(StapelCacheConfig(json = WireJson, scope = scope, clock = Clock.System))
    private val binding = CacheSessionBinding(cache, sessions, scope)
    val deviceId: String = "e2e-device-" + UUID.randomUUID()
    val auth = StapelAuth(api, sessions, cache, scope, AuthConfig(guest = GuestPolicy.OnDemand), deviceId = { deviceId })

    init {
        runBlocking { auth.session.restore() }
    }

    /** A presenter context living as long as the device. */
    fun context(): PresenterContext = PresenterContext(scope)

    suspend fun storedTokens(): TokenPair? = (vault.read() as? VaultRead.Present)?.session?.tokens

    override fun close() {
        binding.close()
        scope.cancel()
    }
}

/** Waits (real time) until [predicate] holds for the flow's value. */
suspend fun <T> StateFlow<T>.await(timeout: Duration = 20.seconds, predicate: (T) -> Boolean): T =
    withTimeout(timeout) { first(predicate) }

/** `exp - iat` of an access token, read without verification. */
fun accessLifetimeSeconds(token: String): Long {
    val payload = String(Base64.getUrlDecoder().decode(token.split('.')[1]))
    val json = Json.parseToJsonElement(payload) as JsonObject
    return json["exp"]!!.jsonPrimitive.long - json["iat"]!!.jsonPrimitive.long
}

fun waitPastExpiry() = Thread.sleep((Stand.ACCESS_LIFETIME + 1.5.seconds).inWholeMilliseconds)
