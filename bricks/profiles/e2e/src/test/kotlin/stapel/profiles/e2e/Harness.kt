package stapel.profiles.e2e

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
import stapel.cdn.di.cdnModule
import stapel.config.ClientInfo
import stapel.config.ClientPlatform
import stapel.config.Posture
import stapel.config.StapelEnvironment
import stapel.di.StapelQualifiers
import stapel.mvi.PresenterContext
import stapel.profiles.StapelProfiles
import stapel.profiles.ui.di.ProfilesBrick
import stapel.result.state.Resource
import stapel.runtime.PlatformContext
import stapel.session.SessionManager
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.TokenVault
import stapel.session.vault.VaultRead
import stapel.wire.FilePart
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The stand under test (bricks/profiles/e2e/stand/stand.sh). */
object Stand {
    val baseUrl: String = System.getProperty("stapel.e2e.baseUrl")
        ?: error("run with -Pstapel.e2e.baseUrl=<stand url> (bricks/profiles/e2e/stand/stand.sh up)")

    /** stapel-auth's default mock OTP code; prototype posture also accepts it for contact SMS codes. */
    const val MOCK_CODE: String = "0000"

    fun email(): String = "e2e+${UUID.randomUUID()}@example.com"

    private val http: HttpClient = HttpClient.newHttpClient()

    /** A GET as the server answers it, with [bearer] when given: the server's state, not the client's. */
    fun get(path: String, bearer: String? = null): JsonObject {
        val request = HttpRequest.newBuilder(URI(baseUrl.trimEnd('/') + path)).GET()
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }.build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "GET $path: ${response.statusCode()} ${response.body()}" }
        return Json.parseToJsonElement(response.body()) as JsonObject
    }
}

/**
 * One app installation wired the way a generated app is: `stapelApp { bricks(AuthBrick(), ProfilesBrick()); modules(cdnModule()) }`
 * against the stand, with an in-memory vault the test can read.
 */
class Device(
    val name: String,
    val vault: InMemoryTokenVault = InMemoryTokenVault(),
    /** The device's signed-out language choice; share one between Devices to model a relaunch. */
    val localeStorage: stapel.runtime.LocalePreferenceStorage = stapel.runtime.InMemoryLocalePreferenceStorage(),
) : AutoCloseable {
    val app: StapelApp = stapelApp(PlatformContext()) {
        environment(
            StapelEnvironment.single(
                "e2e", Stand.baseUrl, Posture.Prototype,
                ClientInfo("stapel.profiles.e2e", "0.0.0", "e2e", ClientPlatform.Desktop, "JVM", "21", name),
            ),
        )
        bricks(AuthBrick(), ProfilesBrick())
        modules(cdnModule())
        overrides(
            module {
                single<TokenVault> { vault }
                single<stapel.runtime.LocalePreferenceStorage> { localeStorage }
                // One installation per Device (the JVM platform id is one file shared by every test).
                val id = "e2e-$name-" + UUID.randomUUID()
                single<stapel.session.InstallationIdentity> { object : stapel.session.InstallationIdentity { override suspend fun deviceId() = id } }
            },
        )
        global(false)
    }
    val auth: StapelAuth get() = app.koin.get()
    val profiles: StapelProfiles get() = app.koin.get()
    val scope: CoroutineScope get() = app.koin.get(StapelQualifiers.AppScope)
    var userId: String = ""
        private set

    fun context(): PresenterContext = PresenterContext(scope)

    /** Signs a new account in with the mock code through the auth presenter. */
    suspend fun signIn(email: String = Stand.email()): Device {
        app.koin.get<SessionManager>().restore()
        val presenter = app.koin.get<OtpSignInPresenter> { parametersOf(context()) }
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

    /** The access token the vault holds now. */
    suspend fun bearer(): String = ((vault.read() as VaultRead.Present).session.tokens.accessToken)

    override fun close() = app.close()
}

suspend fun <T> StateFlow<T>.await(timeout: Duration = 20.seconds, predicate: (T) -> Boolean): T = withTimeout(timeout) { first(predicate) }

/** The first settled value of a load-state flow. */
suspend fun <T> Flow<Resource<T>>.settled(timeout: Duration = 20.seconds): Resource<T> =
    withTimeout(timeout) { first { it !is Resource.Loading && !(it is Resource.Ready && it.freshness.refreshing) } }

/** A small real PNG (the CDN decodes it): 8×8 opaque pixels. */
fun png(): FilePart {
    val bytes = PNG_8X8
    return FilePart("avatar.png", "image/png", bytes.size.toLong()) { Buffer().apply { write(bytes) } }
}

private val PNG_8X8: ByteArray by lazy {
    val image = java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB)
    for (x in 0 until 8) for (y in 0 until 8) image.setRGB(x, y, (x * 32 shl 16) or (y * 32 shl 8) or 0x80)
    val out = java.io.ByteArrayOutputStream()
    javax.imageio.ImageIO.write(image, "png", out)
    out.toByteArray()
}
