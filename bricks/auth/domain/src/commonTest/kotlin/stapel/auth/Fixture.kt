package stapel.auth

import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import stapel.auth.api.AuthApiClient
import stapel.auth.api.model.AuthCapabilities
import stapel.auth.api.model.AuthResponse
import stapel.auth.api.model.AuthResponseStatus
import stapel.auth.api.model.AuthType
import stapel.auth.api.model.OtpSentResponse
import stapel.auth.api.model.TokenPairResponse
import stapel.auth.api.model.User
import stapel.auth.platform.AuthPlatform
import stapel.cache.StapelCache
import stapel.cache.StapelCacheConfig
import stapel.mvi.PresenterContext
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.session.RefreshOutcome
import stapel.session.SessionDependencies
import stapel.session.SessionManager
import stapel.session.TokenRefresher
import stapel.session.vault.InMemoryTokenVault
import stapel.testing.FakeAnswer
import stapel.testing.FakeApiTransport
import stapel.testing.virtualTimeClock
import stapel.wire.WireJson
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Instant

/** Operation ids used by the tests. */
internal object Ops {
    const val CAPABILITIES = "auth_api_v1_capabilities_retrieve"
    const val EMAIL_REQUEST = "auth_api_v1_email_request_create"
    const val EMAIL_VERIFY = "auth_api_v1_email_verify_create"
    const val PASSWORD_LOGIN = "auth_api_v1_password_login_create"
    const val PASSWORD_REGISTER = "auth_api_v1_password_register_create"
    const val TOTP_VERIFY = "auth_api_v1_totp_challenge_verify_create"
    const val FORCED_CHANGE = "auth_api_v1_password_forced_change_create"
    const val ANONYMOUS = "auth_api_v1_anonymous_create"
    const val LOGOUT = "auth_api_v1_logout_create"
    const val ME = "auth_api_v1_me_retrieve"
    const val REFRESH = "auth_api_v1_token_refresh_create"
}

/** A three-part token whose payload carries [userId] and [exp]; the signature is not checked client-side. */
internal fun jwt(userId: String, exp: Long = 4_000_000_000L, salt: String = ""): String {
    val enc = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    val payload = """{"user_id":"$userId","exp":$exp,"n":"$salt"}"""
    return enc.encode("""{"alg":"none"}""".encodeToByteArray()) + "." + enc.encode(payload.encodeToByteArray()) + ".sig"
}

internal fun user(id: String, guest: Boolean = false, email: String? = "a@example.com"): User = User(
    id = id,
    username = "u-$id",
    email = email,
    authType = if (guest) AuthType.Anonymous else AuthType.Email,
    isEmailVerified = !guest,
    isPhoneVerified = false,
    isAnonymous = guest,
    isStaff = false,
    isSuperuser = false,
    oauthProvider = null,
    createdAt = Instant.fromEpochSeconds(1_700_000_000),
    lastLogin = null,
)

internal fun session(id: String, status: AuthResponseStatus, guest: Boolean = false, salt: String = ""): AuthResponse =
    AuthResponse(user(id, guest), status, TokenPairResponse(refresh = "r-$id-$salt", access = jwt(id, salt = salt)))

internal fun refusal(code: String, status: Int, params: Map<String, String> = emptyMap()): FakeAnswer =
    FakeAnswer.Refusal(ErrorEnvelope(ErrorCode(code), status, ErrorParams(params), serverText = code))

internal val sent = OtpSentResponse(message = "sent", target = "a***@example.com")

/** Capabilities as the stand answers them, with [extraMethods] appended (raw `methods[]` entries). */
internal fun capabilities(
    extraMethods: String = "",
    passwordRegistration: Boolean = false,
    guest: Boolean = true,
    cooldown: Int = 30,
): AuthCapabilities = WireJson.decodeFromString(
    AuthCapabilities.serializer(),
    """
    {"registration":{"oauth":[],"phone":true,"email":true,"password":$passwordRegistration,"sso":false,"anonymous":$guest,"email_mock":true,"phone_mock":true},
     "login":{"oauth":[],"phone":true,"email":true,"password":true,"sso":false,"qr":false,"passkey":false,"magic_link":false,"email_mock":true,"phone_mock":true},
     "mfa":{"totp":true,"passkey":false},
     "methods":[
       {"id":"email","enabled":true,"placement":"main","order":0,"interaction":"inline","icon_svg":"","mock":true,"can_login":true,"can_register":true},
       {"id":"phone","enabled":true,"placement":"main","order":1,"interaction":"inline","icon_svg":"","mock":true,"can_login":true,"can_register":true},
       {"id":"password","enabled":true,"placement":"overflow","order":7,"interaction":"modal","icon_svg":"","can_login":true,"can_register":false}
       $extraMethods
     ],
     "otp":{"email_code_length":4,"phone_code_length":4,"totp_code_length":6,"ttl_seconds":600,"resend_cooldown_seconds":$cooldown},
     "posture":{"preset":"public_space","stage":"prototype"}}
    """.trimIndent(),
)

/** The library wired over a scripted transport, a real session manager and a memory cache. */
internal class Fixture(
    scope: TestScope,
    config: (Clock) -> AuthConfig = { AuthConfig(clock = it) },
    platform: AuthPlatform = AuthPlatform(),
    refresher: TokenRefresher = TokenRefresher { RefreshOutcome.Transient("not scripted") },
) {
    val clock: Clock = scope.virtualTimeClock()
    val transport = FakeApiTransport()
    val api = AuthApiClient(transport)
    val vault = InMemoryTokenVault()
    val sessions: SessionManager = SessionManager(SessionDependencies(vault, scope.backgroundScope, clock), refresher)
    val cache: StapelCache = StapelCache(StapelCacheConfig(json = WireJson, scope = scope.backgroundScope, clock = clock))
    val auth = StapelAuth(api, sessions, cache, scope.backgroundScope, config(clock), platform, deviceId = { "device-0123456789abcdef" })
    val context = PresenterContext(scope.backgroundScope)

    init {
        transport.on(Ops.CAPABILITIES) { FakeAnswer.Value(capabilities()) }
    }

    /** Settles the session (reads the empty vault). */
    suspend fun start(): Fixture {
        auth.session.restore()
        return this
    }

    fun calls(operationId: String): Int = transport.calls.count { it.first == operationId }

    @Suppress("UNCHECKED_CAST")
    fun bodies(operationId: String): List<Any?> = transport.calls.filter { it.first == operationId }.map { it.second.body }

    fun raw(json: String): JsonObject = Json.parseToJsonElement(json) as JsonObject
}
