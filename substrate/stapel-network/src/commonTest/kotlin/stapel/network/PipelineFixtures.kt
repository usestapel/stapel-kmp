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
import stapel.wire.AuthRequirement
import stapel.wire.BodyEncoding
import stapel.wire.CaptchaRequirement
import stapel.wire.ErrorDecoder
import stapel.wire.OperationKind
import stapel.wire.OperationSpec
import stapel.wire.ResponseKind
import stapel.testing.SequentialIdGenerator
import stapel.testing.TestClock
import kotlin.random.Random
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

/** The first id [SequentialIdGenerator] hands out: the `X-Request-ID` of the first logical call. */
const val FIRST_ID = "00000000-0000-4000-8000-000000000001"

fun deps(env: StapelEnvironment = testEnvironment(), connectivity: stapel.runtime.Connectivity? = null) = NetworkDependencies(
    environment = env, clock = TestClock(Instant.fromEpochSeconds(1_800_000_000)), ids = SequentialIdGenerator(), connectivity = connectivity, random = Random(7),
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
