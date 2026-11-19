package stapel.session

import stapel.testing.SequentialIdGenerator
import stapel.testing.TestClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
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
import stapel.session.vault.StoredSession
import stapel.wire.ApiTransport
import stapel.wire.AuthRequirement
import stapel.wire.BodyEncoding
import stapel.wire.ErrorDecoder
import stapel.wire.OperationCall
import stapel.wire.OperationKind
import stapel.wire.OperationSpec
import stapel.wire.ResponseKind
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private sealed interface ApiError : StapelError {
    data class Domain(override val envelope: ErrorEnvelope) : ApiError, DomainError {
        override val remediation: Remediation get() = Remediation.Unknown
    }

    data class Transport(override val failure: TransportFailure) : ApiError, TransportCarrier

    companion object Decoder : ErrorDecoder<ApiError> {
        override fun domain(envelope: ErrorEnvelope): ApiError = Domain(envelope)

        override fun transport(failure: TransportFailure): ApiError = Transport(failure)
    }
}

@Serializable
private data class Me(val id: String)

@Serializable
private data class RefreshBody(val refresh: String)

@Serializable
private data class Pair2(val access: String, val refresh: String)

private val getMe = OperationSpec<Unit, Me, ApiError>(
    "auth_me", "auth", "GET", "/auth/api/v1/me/", OperationKind.Query, AuthRequirement.Required,
    BodyEncoding.None, null, ResponseKind.Json, mapOf(200 to Me.serializer()), ApiError,
)

private val refreshOp = OperationSpec<RefreshBody, Pair2, ApiError>(
    "auth_token_refresh", "auth", "POST", "/auth/api/v1/token/refresh/", OperationKind.Mutation, AuthRequirement.None,
    BodyEncoding.Json, RefreshBody.serializer(), ResponseKind.Json, mapOf(200 to Pair2.serializer()), ApiError,
)

/** A backend double: `/me/` accepts only the live access token; `/token/refresh/` rotates. */
private class Backend(var liveAccess: String = "access-B") : HttpTransport {
    val requests = mutableListOf<WireRequest>()
    var refreshAnswer: (WireRequest) -> WireOutcome = {
        WireOutcome.Response(200, emptyList(), """{"access":"access-B","refresh":"refresh-B"}""".encodeToByteArray())
    }

    val refreshCalls get() = requests.count { it.url.endsWith("/token/refresh/") }

    override suspend fun send(request: WireRequest): WireOutcome {
        requests += request
        delay(10.milliseconds)
        return when {
            request.url.endsWith("/token/refresh/") -> refreshAnswer(request)
            request.header("Authorization") == "Bearer $liveAccess" ->
                WireOutcome.Response(200, emptyList(), """{"id":"u1"}""".encodeToByteArray())
            else -> WireOutcome.Response(401, emptyList(), """{"localizable_error":"error.401.unauthorized"}""".encodeToByteArray())
        }
    }

    override fun close() {}
}

/** The shape of AUTH's refresher: the bare transport, the refresh endpoint, three outcomes. */
private class BareRefresher(private val bare: ApiTransport) : TokenRefresher {
    override suspend fun refresh(current: TokenPair): RefreshOutcome =
        when (val result = bare.execute(refreshOp, OperationCall(body = RefreshBody(current.refreshToken)))) {
            is StapelResult.Ok -> RefreshOutcome.Rotated(TokenPair(result.value.access, result.value.refresh))
            is StapelResult.Err -> when (val error = result.error) {
                is ApiError.Transport -> when (val failure = error.failure) {
                    is TransportFailure.Unauthenticated -> when (failure.code.value) {
                        "error.401.refresh_revoked", "error.401.token_revoked" -> RefreshOutcome.Rejected(SessionEnd.Revoked, failure.code.value)
                        else -> RefreshOutcome.Rejected(SessionEnd.Expired, failure.code.value)
                    }
                    else -> RefreshOutcome.Transient(failure.code.value)
                }
                is ApiError.Domain -> RefreshOutcome.Transient(error.code.value)
            }
        }
}

/**
 * G-D2 + the session rows of G-N2: runtime → network → session wired by constructors only, a real
 * [SessionManager] behind the real pipeline, a fake backend on the wire.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PipelineSessionTest {
    private val client = ClientInfo("com.example.app", "1", "1", ClientPlatform.Ios, "iOS", "18", null, "0")
    private val environment = StapelEnvironment.single("test", "https://api.example.com/", Posture.Prototype, client)

    private inner class Stack(scope: TestScope, initial: TokenPair = pair("A"), clock: TestClock = FakeClock()) {
        val backend = Backend()
        val deps = NetworkDependencies(environment, clock, SequentialIdGenerator(), random = Random(1))
        val bare = StapelTransport(deps, transport = backend)
        val vault = RecordingVault(StoredSession(initial, SessionKind.Authenticated))
        val session = SessionManager(SessionDependencies(vault, scope.backgroundScope, clock), BareRefresher(bare))
        val api = StapelTransport(deps, authenticator = session.authenticator, transport = backend)
    }

    @Test
    fun parallel401sCauseExactlyOneRefreshCall() = runTest {
        val stack = Stack(this)
        stack.session.restore()
        val results = List(25) { async { stack.api.execute(getMe, OperationCall()) } }.awaitAll()
        assertTrue(results.all { it is StapelResult.Ok }, "$results")
        assertEquals(1, stack.backend.refreshCalls, "N parallel 401s must make exactly one refresh call")
        val refresh = stack.backend.requests.single { it.url.endsWith("/token/refresh/") }
        assertEquals(null, refresh.header("Authorization"), "the refresh goes through the bare transport")
        assertEquals("""{"refresh":"refresh-A"}""", (refresh.body as WireBody.Bytes).text())
        assertEquals("refresh-B", stack.vault.record!!.tokens.refreshToken)
    }

    @Test
    fun rejectedRefreshEndsTheSessionAndTheCallIsUnauthenticated() = runTest {
        val stack = Stack(this)
        stack.backend.refreshAnswer = {
            WireOutcome.Response(401, emptyList(), """{"localizable_error":"error.401.refresh_revoked"}""".encodeToByteArray())
        }
        stack.session.restore()
        val results = List(5) { async { stack.api.execute(getMe, OperationCall()) } }.awaitAll()
        for (result in results) {
            val failure = ((result as StapelResult.Err).error as ApiError.Transport).failure
            assertIs<TransportFailure.Unauthenticated>(failure)
        }
        assertEquals(1, stack.backend.refreshCalls)
        assertEquals(SessionState.Unauthenticated(SessionEnd.Revoked), stack.session.state.value)
        // After the end, a protected call fails locally without touching the wire.
        val before = stack.backend.requests.size
        stack.api.execute(getMe, OperationCall())
        assertEquals(before, stack.backend.requests.size)
    }

    @Test
    fun transientRefreshKeepsTheSessionAndFailsTheCallAsTransient() = runTest {
        val stack = Stack(this)
        stack.backend.refreshAnswer = { WireOutcome.Response(502, emptyList(), "<html>bad gateway</html>".encodeToByteArray()) }
        stack.session.restore()
        val result = stack.api.execute(getMe, OperationCall())
        val failure = ((result as StapelResult.Err).error as ApiError.Transport).failure
        assertIs<TransportFailure.ConnectionFailed>(failure)
        assertEquals(SessionState.Authenticated(1), stack.session.state.value)
        assertEquals("refresh-A", stack.vault.record!!.tokens.refreshToken)
        assertEquals(1, stack.backend.refreshCalls, "the refresh call itself is a mutation: not retried on 502")
    }

    @Test
    fun accessTokenAboutToExpireIsRefreshedBeforeTheRequest() = runTest {
        val clock = FakeClock()
        val stack = Stack(this, initial = pair("A", exp = clock.now() + 5.seconds), clock = clock)
        stack.session.restore()
        assertIs<StapelResult.Ok<Me>>(stack.api.execute(getMe, OperationCall()))
        assertEquals(
            listOf("/token/refresh/", "/me/"),
            stack.backend.requests.map { it.url.substringAfter("/v1") },
            "no request may go out with a token about to expire",
        )
        assertEquals("Bearer access-B", stack.backend.requests.last().header("Authorization"))
    }

}
