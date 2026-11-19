package stapel.captcha

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import stapel.config.ClientInfo
import stapel.config.ClientPlatform
import stapel.config.Posture
import stapel.config.StapelEnvironment
import stapel.network.HttpTransport
import stapel.network.NetworkDependencies
import stapel.network.StapelTransport
import stapel.network.WireBody
import stapel.network.WireOutcome
import stapel.network.WireRequest
import stapel.result.DomainError
import stapel.result.ErrorEnvelope
import stapel.result.Remediation
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.TransportCarrier
import stapel.result.TransportFailure
import stapel.runtime.IdGenerator
import stapel.wire.AuthRequirement
import stapel.wire.BodyEncoding
import stapel.wire.CaptchaRequirement
import stapel.wire.ErrorDecoder
import stapel.wire.OperationCall
import stapel.wire.OperationKind
import stapel.wire.OperationSpec
import stapel.wire.ResponseKind
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

private sealed interface ApiError : StapelError {
    data class Domain(override val envelope: ErrorEnvelope) : ApiError, DomainError {
        override val remediation: Remediation get() = Remediation.Retry
    }

    data class Transport(override val failure: TransportFailure) : ApiError, TransportCarrier

    companion object Decoder : ErrorDecoder<ApiError> {
        override fun domain(envelope: ErrorEnvelope): ApiError = Domain(envelope)

        override fun transport(failure: TransportFailure): ApiError = Transport(failure)
    }
}

@Serializable
private data class PhoneRequest(val phone: String)

private fun otp(captcha: CaptchaRequirement?) = OperationSpec<PhoneRequest, Unit, ApiError>(
    "auth_phone_request", "auth", "POST", "/auth/api/v1/phone/request/", OperationKind.Mutation, AuthRequirement.Optional,
    BodyEncoding.Json, PhoneRequest.serializer(), ResponseKind.Empty, mapOf(204 to null), ApiError, captcha = captcha,
)

private class Wire : HttpTransport {
    val requests = mutableListOf<WireRequest>()
    val answers = ArrayDeque<String?>() // envelope code, or null for 204

    override suspend fun send(request: WireRequest): WireOutcome {
        requests += request
        val code = answers.removeFirst()
        return if (code == null) {
            WireOutcome.Response(204, emptyList(), ByteArray(0))
        } else {
            WireOutcome.Response(400, emptyList(), """{"localizable_error":"$code","params":{}}""".encodeToByteArray())
        }
    }

    override fun close() {}
}

/** A headless provider double: hands out numbered tokens and records what it was asked. */
private class FakeSolver(private val abandon: Boolean = false) : CaptchaSolver {
    val asked = mutableListOf<CaptchaRequest>()

    override suspend fun solve(request: CaptchaRequest): String? {
        asked += request
        return if (abandon) null else "tok-${asked.size}"
    }
}

/** The challenge → token → retry flow, exercised through the real pipeline. */
class CaptchaPluginTest {
    private val wire = Wire()
    private val env = StapelEnvironment.single(
        "test", "https://api.example.com/", Posture.Live,
        ClientInfo("com.example.app", "1", "1", ClientPlatform.Android, "Android", "15", null, "0"),
    )
    private val deps = NetworkDependencies(
        env,
        object : Clock {
            override fun now() = Instant.fromEpochSeconds(0)
        },
        object : IdGenerator {
            override fun uuid() = "id"
        },
        random = Random(1),
    )

    private fun transport(solver: CaptchaSolver, proactive: Boolean = false) =
        StapelTransport(deps, plugins = listOf(CaptchaPlugin(solver, proactive)), transport = wire)

    private val headerAndField = CaptchaRequirement("otp", header = "X-Captcha-Token", field = "captcha_token")

    @Test
    fun requiredCaptchaIsSolvedOnceAndRetriedWithTheToken() = runTest {
        val solver = FakeSolver()
        wire.answers += listOf(CaptchaCodes.REQUIRED, null)
        val result = transport(solver).execute(otp(headerAndField), OperationCall(body = PhoneRequest("+1")))
        assertIs<StapelResult.Ok<Unit>>(result)
        assertEquals(listOf(CaptchaRequest("otp", CaptchaReason.Required, "auth_phone_request")), solver.asked)
        assertNull(wire.requests[0].header("X-Captcha-Token"))
        assertEquals("tok-1", wire.requests[1].header("X-Captcha-Token"))
        assertEquals("""{"phone":"+1","captcha_token":"tok-1"}""", (wire.requests[1].body as WireBody.Bytes).text())
    }

    @Test
    fun aRejectedTokenIsNotRetriedASecondTime() = runTest {
        val solver = FakeSolver()
        wire.answers += listOf(CaptchaCodes.REQUIRED, CaptchaCodes.INVALID)
        val error = (transport(solver).execute(otp(headerAndField), OperationCall(body = PhoneRequest("+1"))) as StapelResult.Err).error
        assertEquals(CaptchaCodes.INVALID, error.code.value)
        assertEquals(1, solver.asked.size)
        assertEquals(2, wire.requests.size)
    }

    @Test
    fun abandonedChallengeReturnsTheOriginalError() = runTest {
        wire.answers += listOf(CaptchaCodes.REQUIRED)
        val error = (transport(FakeSolver(abandon = true)).execute(otp(headerAndField), OperationCall(body = PhoneRequest("+1"))) as StapelResult.Err).error
        assertIs<ApiError.Domain>(error)
        assertEquals(CaptchaCodes.REQUIRED, error.code.value)
        assertEquals(1, wire.requests.size)
    }

    @Test
    fun bodyFieldOnlyOperationGetsNoHeader() = runTest {
        // A CaptchaMixin serializer reads only the body field (x-stapel-captcha without a header).
        wire.answers += listOf(CaptchaCodes.REQUIRED, null)
        transport(FakeSolver()).execute(otp(CaptchaRequirement("default", header = null, field = "captcha_token")), OperationCall(body = PhoneRequest("+1")))
        assertNull(wire.requests[1].header("X-Captcha-Token"))
        assertEquals("""{"phone":"+1","captcha_token":"tok-1"}""", (wire.requests[1].body as WireBody.Bytes).text())
    }

    @Test
    fun undeclaredOperationGetsBothPlaces() = runTest {
        wire.answers += listOf(CaptchaCodes.REQUIRED, null)
        transport(FakeSolver()).execute(otp(null), OperationCall(body = PhoneRequest("+1")))
        assertEquals("tok-1", wire.requests[1].header("X-Captcha-Token"))
        assertEquals("""{"phone":"+1","captcha_token":"tok-1"}""", (wire.requests[1].body as WireBody.Bytes).text())
    }

    @Test
    fun proactiveModeAttachesBeforeTheFirstAttempt() = runTest {
        val solver = FakeSolver()
        wire.answers += listOf(null)
        transport(solver, proactive = true).execute(otp(headerAndField), OperationCall(body = PhoneRequest("+1")))
        assertEquals(CaptchaReason.Proactive, solver.asked.single().reason)
        assertEquals("tok-1", wire.requests.single().header("X-Captcha-Token"))
    }

    @Test
    fun otherErrorsAreNotCaptchaBusiness() = runTest {
        val solver = FakeSolver()
        wire.answers += listOf("error.400.invalid_phone")
        transport(solver).execute(otp(headerAndField), OperationCall(body = PhoneRequest("+1")))
        assertEquals(0, solver.asked.size)
        assertEquals(1, wire.requests.size)
    }
}
