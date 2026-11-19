package stapel.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import stapel.config.ClientInfo
import stapel.config.ClientPlatform
import stapel.config.Posture
import stapel.config.StapelEnvironment
import stapel.result.DomainError
import stapel.result.ErrorEnvelope
import stapel.result.Remediation
import stapel.result.StapelError
import stapel.result.TransportCarrier
import stapel.result.TransportFailure
import stapel.runtime.IdGenerator
import stapel.wire.AuthRequirement
import stapel.wire.BodyEncoding
import stapel.wire.CaptchaRequirement
import stapel.wire.ErrorDecoder
import stapel.wire.OperationKind
import stapel.wire.OperationSpec
import stapel.wire.ResponseKind
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

/** A brick-shaped error type, as CODEGEN generates one. */
sealed interface TestError : StapelError {
    data class Domain(override val envelope: ErrorEnvelope) : TestError, DomainError {
        override val remediation: Remediation get() = Remediation.FixInput
    }

    data class Transport(override val failure: TransportFailure) : TestError, TransportCarrier

    companion object Decoder : ErrorDecoder<TestError> {
        override fun domain(envelope: ErrorEnvelope): TestError = Domain(envelope)

        override fun transport(failure: TransportFailure): TestError = Transport(failure)
    }
}

@Serializable
data class Me(val id: String, val name: String? = null)

@Serializable
data class OtpRequest(val phone: String)

fun <B, T> spec(
    id: String = "auth_me",
    method: String = "GET",
    path: String = "/auth/api/v1/me/",
    kind: OperationKind = if (method == "GET") OperationKind.Query else OperationKind.Mutation,
    auth: AuthRequirement = AuthRequirement.Required,
    bodyEncoding: BodyEncoding = BodyEncoding.None,
    body: KSerializer<B>? = null,
    responseKind: ResponseKind = ResponseKind.Json,
    success: Map<Int, KSerializer<out T>?>,
    captcha: CaptchaRequirement? = null,
): OperationSpec<B, T, TestError> = OperationSpec(
    id = id, module = "auth", method = method, path = path, kind = kind, auth = auth,
    bodyEncoding = bodyEncoding, body = body, responseKind = responseKind, success = success,
    errors = TestError, captcha = captcha,
)

val getMe: OperationSpec<Unit, Me, TestError> = spec(success = mapOf(200 to Me.serializer()))

val requestOtp: OperationSpec<OtpRequest, Unit, TestError> = spec(
    id = "auth_phone_request", method = "POST", path = "/auth/api/v1/phone/request/", auth = AuthRequirement.Optional,
    bodyEncoding = BodyEncoding.Json, body = OtpRequest.serializer(), responseKind = ResponseKind.Empty,
    success = mapOf(204 to null),
    captcha = CaptchaRequirement("otp", header = "X-Captcha-Token", field = "captcha_token"),
)

val testClient = ClientInfo("com.example.app", "1.2.3", "45", ClientPlatform.Android, "Android", "15", "Pixel", "0.0.0")

fun testEnvironment(base: String = "https://api.example.com/") =
    StapelEnvironment.single("test", base, Posture.Prototype, testClient)

class SequentialIds : IdGenerator {
    private var n = 0

    override fun uuid(): String = "call-${++n}"
}

class FixedClock : Clock {
    override fun now(): Instant = Instant.fromEpochSeconds(1_800_000_000)
}

/** Answers from a script; records every request. */
class FakeTransport : HttpTransport {
    val requests = mutableListOf<WireRequest>()
    private val script = ArrayDeque<(WireRequest) -> WireOutcome>()
    var fallback: ((WireRequest) -> WireOutcome)? = null

    fun enqueue(answer: (WireRequest) -> WireOutcome) {
        script.addLast(answer)
    }

    fun enqueue(status: Int, body: String = "", headers: List<Pair<String, String>> = emptyList()) =
        enqueue { WireOutcome.Response(status, headers, body.encodeToByteArray()) }

    fun enqueueEnvelope(status: Int, code: String, params: String = "{}", extra: String = "") =
        enqueue(status, "{\"localizable_error\":\"$code\",\"error\":\"text\",\"error_language\":\"en\",\"params\":$params$extra}")

    fun enqueueFailure(error: TransportFailure.NoResponse, requestSent: Boolean) = enqueue { WireOutcome.Failure(error, requestSent) }

    override suspend fun send(request: WireRequest): WireOutcome {
        requests += request
        val answer = script.removeFirstOrNull() ?: fallback ?: error("no scripted answer for request #${requests.size}: $request")
        return answer(request)
    }

    override fun close() {}
}

fun deps(env: StapelEnvironment = testEnvironment(), connectivity: stapel.runtime.Connectivity? = null) = NetworkDependencies(
    environment = env, clock = FixedClock(), ids = SequentialIds(), connectivity = connectivity, random = Random(7),
)

/** An authenticator over a mutable token with a scripted refresh. */
class FakeAuthenticator(var token: String? = "A") : RequestAuthenticator {
    var refreshes = 0
    val failedTokens = mutableListOf<String?>()
    var verdict: () -> RefreshVerdict = { RefreshVerdict.Refreshed("B").also { token = "B" } }

    override suspend fun accessToken(): String? = token

    override suspend fun refresh(failedToken: String?): RefreshVerdict {
        refreshes++
        failedTokens += failedToken
        return verdict()
    }
}
