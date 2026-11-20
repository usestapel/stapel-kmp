package stapel.notifications.e2e

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import stapel.app.di.StapelApp
import stapel.app.di.stapelApp
import stapel.auth.StapelAuth
import stapel.auth.model.OtpChannel
import stapel.auth.presentation.OtpSignInIntent
import stapel.auth.presentation.OtpSignInPresenter
import stapel.auth.presentation.OtpSignInState
import stapel.auth.session.AuthState
import stapel.auth.ui.di.AuthBrick
import stapel.config.ClientInfo
import stapel.config.ClientPlatform
import stapel.config.Posture
import stapel.config.StapelEnvironment
import stapel.di.StapelQualifiers
import stapel.mvi.PresenterContext
import stapel.notifications.push.di.notificationsPushModule
import stapel.notifications.push.notificationsPushRouting
import stapel.push.DevicePlatform
import stapel.push.PushController
import stapel.push.PushOptions
import stapel.push.PushPermission
import stapel.push.PushProvider
import stapel.push.di.pushModule
import stapel.push.testing.FakePushProvider
import stapel.runtime.LogLevel
import stapel.runtime.PlatformContext
import stapel.runtime.StapelLogger
import stapel.session.InstallationIdentity
import stapel.session.SessionManager
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.TokenVault
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The stand under test (bricks/notifications/e2e/stand/stand.sh) and its probes. */
object Stand {
    val baseUrl: String = System.getProperty("stapel.e2e.baseUrl")
        ?: error("run with -Pstapel.e2e.baseUrl=<stand url> (bricks/notifications/e2e/stand/stand.sh up)")

    const val MOCK_CODE: String = "0000"

    /** Tokens of this run: the registry's token column is unique across runs of the stand. */
    val run: String = UUID.randomUUID().toString().take(8)

    fun email(): String = "e2e+${UUID.randomUUID()}@example.com"

    private val http: HttpClient = HttpClient.newHttpClient()

    private fun send(request: HttpRequest): String {
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "${request.method()} ${request.uri()}: ${response.statusCode()} ${response.body()}" }
        return response.body()
    }

    /** The server's device rows of [userId], read from its database (not through the client). */
    fun devices(userId: String): JsonArray =
        Json.parseToJsonElement(send(HttpRequest.newBuilder(URI("${baseUrl.trimEnd('/')}/e2e/push/devices/?user_id=$userId")).GET().build()))
            .jsonObject.getValue("devices").jsonArray

    /** A real notification for [userId] through process_notification; answers what the mock sender captured. */
    fun sendPush(userId: String, url: String?): JsonArray {
        val body = if (url == null) """{"user_id":"$userId"}""" else """{"user_id":"$userId","url":"$url"}"""
        val request = HttpRequest.newBuilder(URI("${baseUrl.trimEnd('/')}/e2e/push/send/"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build()
        return Json.parseToJsonElement(send(request)).jsonObject.getValue("outbox").jsonArray
    }

    /** GET [path] with [bearer]. */
    fun get(path: String, bearer: String): String =
        send(HttpRequest.newBuilder(URI(baseUrl.trimEnd('/') + path)).header("Authorization", "Bearer $bearer").GET().build())
}

/** A logger that keeps the `stapel.push …` markers for assertions. */
class Markers : StapelLogger {
    val lines: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    override fun isEnabled(level: LogLevel, tag: String): Boolean = level >= LogLevel.Info

    override fun log(level: LogLevel, tag: String, message: String, error: Throwable?, fields: Map<String, String>) {
        lines += message
        println("[$tag] $message")
    }

    fun has(text: String): Boolean = synchronized(lines) { lines.any { text in it } }
}

/**
 * One app installation, wired the way a generated app with `--mobile-push` is, against the stand. The
 * vendor SDK is [provider] (a fake that grants the OS prompt); one installation id for the Device's life.
 */
class Device(val name: String) : AutoCloseable {
    private val vault = InMemoryTokenVault()
    val installation: String = "e2e-$name-" + UUID.randomUUID()
    val markers = Markers()
    val provider = FakePushProvider(platform = DevicePlatform.Ios, id = "fake", tokenPrefix = "e2e-${Stand.run}-$name").apply {
        osAnswer = PushPermission.Granted
    }
    val app: StapelApp = stapelApp(PlatformContext()) {
        environment(
            StapelEnvironment.single(
                "e2e", Stand.baseUrl, Posture.Prototype,
                ClientInfo("stapel.notifications.e2e", "0.0.0", "e2e", ClientPlatform.Desktop, "JVM", "21", name),
            ),
        )
        logger(markers)
        bricks(AuthBrick())
        modules(
            pushModule(PushOptions(routing = notificationsPushRouting())),
            notificationsPushModule(),
            module { single<PushProvider> { provider } },
        )
        overrides(
            module {
                single<TokenVault> { vault }
                single<InstallationIdentity> { object : InstallationIdentity { override suspend fun deviceId() = installation } }
            },
        )
        global(false)
    }
    val auth: StapelAuth get() = app.koin.get()
    val push: PushController get() = app.koin.get()
    val scope: CoroutineScope get() = app.koin.get(StapelQualifiers.AppScope)
    var userId: String = ""
        private set

    /** Signs a new account in with the mock code through the auth presenter. */
    suspend fun signIn(email: String = Stand.email()): Device {
        app.koin.get<SessionManager>().restore()
        val presenter = app.koin.get<OtpSignInPresenter> { parametersOf(PresenterContext(scope)) }
        presenter.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, email))
        var sent = presenter.state.await { it is OtpSignInState.CodeSent || it is OtpSignInState.Locked || it is OtpSignInState.RequestFailed }
        repeat(3) {
            val limited = (sent as? OtpSignInState.Locked) ?: return@repeat
            kotlinx.coroutines.delay((limited.error.retryAfter ?: 2.seconds).coerceAtMost(5.seconds).inWholeMilliseconds + 500)
            presenter.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, email))
            sent = presenter.state.await { it is OtpSignInState.CodeSent || it is OtpSignInState.RequestFailed || (it is OtpSignInState.Locked && it !== limited) }
        }
        check(sent is OtpSignInState.CodeSent) { "code request failed: $sent" }
        presenter.dispatch(OtpSignInIntent.SubmitCode(Stand.MOCK_CODE))
        presenter.state.await { it is OtpSignInState.Done }
        val signedIn = auth.session.state.await { it is AuthState.SignedIn } as AuthState.SignedIn
        userId = signedIn.user?.id ?: error("no user id after sign-in")
        return this
    }

    override fun close() = app.close()
}

suspend fun <T> StateFlow<T>.await(timeout: Duration = 20.seconds, predicate: (T) -> Boolean): T = withTimeout(timeout) { first(predicate) }

/** The rows as a readable list for failure messages. */
fun JsonArray.describe(): String = joinToString { (it as JsonObject).toString() }
