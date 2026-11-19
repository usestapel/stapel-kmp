package stapel.network

import kotlinx.coroutines.test.runTest
import stapel.result.ErrorCode
import stapel.result.StapelResult
import stapel.result.TimeoutPhase
import stapel.result.TransportFailure
import stapel.runtime.Connectivity
import stapel.runtime.NetworkStatus
import stapel.testing.FakeTransport
import stapel.wire.AuthRequirement
import stapel.wire.OperationCall
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Gate G-N2: the pipeline's semantics over a fake wire. */
class PipelineTest {
    private val wire = FakeTransport()

    private fun transport(
        authenticator: RequestAuthenticator = FakeAuthenticator(),
        verification: VerificationHandler? = null,
        plugins: List<NetworkPlugin> = emptyList(),
        config: NetworkConfig = NetworkConfig(),
        connectivity: Connectivity? = null,
    ) = StapelTransport(deps(connectivity = connectivity), config, authenticator, verification, plugins, wire)

    private fun TestError.failure(): TransportFailure = (this as TestError.Transport).failure

    // Success, headers ----------------------------------------------------------------------

    @Test
    fun successDecodesAndSendsTheBearerAndDefaults() = runTest {
        wire.enqueue(200, """{"id":"u1","name":"Ann","extra":1}""")
        val result = transport().execute(getMe, OperationCall())
        assertEquals(StapelResult.Ok(Me("u1", "Ann")), result)
        val sent = wire.requests.single()
        assertEquals("https://api.example.com/auth/api/v1/me/", sent.url)
        assertEquals("Bearer A", sent.header("Authorization"))
        assertEquals("application/json", sent.header("Accept"))
        assertEquals(FIRST_ID, sent.header("X-Request-ID"))
        assertEquals(testClient.userAgent, sent.header("User-Agent"))
        assertEquals("\"Android\"", sent.header("Sec-CH-UA-Platform"))
        assertNull(sent.header("Cookie"))
    }

    @Test
    fun requiredOperationWithoutSessionFailsLocally() = runTest {
        val result = transport(FakeAuthenticator(token = null)).execute(getMe, OperationCall())
        assertIs<TransportFailure.Unauthenticated>((result as StapelResult.Err).error.failure())
        assertTrue(wire.requests.isEmpty(), "no request may leave without a session")
    }

    @Test
    fun noneOperationNeverCarriesAToken() = runTest {
        wire.enqueue(200, """{"id":"s"}""")
        val site = spec<Unit, Me>(id = "site", path = "/auth/api/v1/site/", auth = AuthRequirement.None, success = mapOf(200 to Me.serializer()))
        transport().execute(site, OperationCall())
        assertNull(wire.requests.single().header("Authorization"))
    }

    @Test
    fun undecodableSuccessIsAContractViolation() = runTest {
        wire.enqueue(200, """{"name":"no id"}""")
        val error = (transport().execute(getMe, OperationCall()) as StapelResult.Err).error.failure()
        assertIs<TransportFailure.ContractViolation>(error)
        assertEquals("auth_me", error.operationId)
    }

    // Refresh (recover once) -----------------------------------------------------------------

    @Test
    fun unauthorizedRefreshesOnceAndRetriesOnceWithTheNewToken() = runTest {
        val auth = FakeAuthenticator()
        wire.enqueueEnvelope(401, "error.401.unauthorized", error = "text", language = "en")
        wire.enqueue(200, """{"id":"u1"}""")
        val result = transport(auth).execute(getMe, OperationCall())
        assertIs<StapelResult.Ok<Me>>(result)
        assertEquals(1, auth.refreshes)
        assertEquals(listOf<String?>("A"), auth.failedTokens)
        assertEquals(listOf("Bearer A", "Bearer B"), wire.requests.map { it.header("Authorization") })
        assertEquals(listOf(FIRST_ID, FIRST_ID), wire.requests.map { it.header("X-Request-ID") }, "one logical call, one request id")
    }

    @Test
    fun aSecond401IsReturnedNotLooped() = runTest {
        val auth = FakeAuthenticator()
        wire.enqueueEnvelope(401, "error.401.unauthorized", error = "text", language = "en")
        wire.enqueueEnvelope(401, "error.401.unauthorized", error = "text", language = "en")
        val error = (transport(auth).execute(getMe, OperationCall()) as StapelResult.Err).error.failure()
        assertIs<TransportFailure.Unauthenticated>(error)
        assertEquals(ErrorCode("error.401.unauthorized"), error.code)
        assertEquals(1, auth.refreshes)
        assertEquals(2, wire.requests.size)
    }

    @Test
    fun refreshWithoutVerdictIsTransientNotSignedOut() = runTest {
        val auth = FakeAuthenticator().apply { verdict = { RefreshVerdict.Unavailable } }
        wire.enqueueEnvelope(401, "error.401.unauthorized", error = "text", language = "en")
        val error = (transport(auth).execute(getMe, OperationCall()) as StapelResult.Err).error.failure()
        assertIs<TransportFailure.ConnectionFailed>(error)
        assertEquals(1, wire.requests.size)
    }

    @Test
    fun endedSessionIsUnauthenticated() = runTest {
        val auth = FakeAuthenticator().apply { verdict = { RefreshVerdict.SessionEnded } }
        wire.enqueueEnvelope(401, "error.401.unauthorized", error = "text", language = "en")
        val error = (transport(auth).execute(getMe, OperationCall()) as StapelResult.Err).error.failure()
        assertIs<TransportFailure.Unauthenticated>(error)
    }

    @Test
    fun a401WithoutATokenDoesNotRefresh() = runTest {
        val auth = FakeAuthenticator(token = null)
        wire.enqueueEnvelope(401, "error.401.unauthorized", error = "text", language = "en")
        transport(auth).execute(requestOtp, OperationCall(body = OtpRequest("+1")))
        assertEquals(0, auth.refreshes)
    }

    // Step-up --------------------------------------------------------------------------------

    private val challengeBody =
        """{"localizable_error":"error.403.verification_required","error":"Additional verification required","verification":{"challenge_id":"chg_1","scope":"password_change","factors":["totp","email_otp"],"expires_at":1}}"""

    @Test
    fun stepUpChallengeIsResolvedOnceAndRetriedWithTheGrant() = runTest {
        val seen = mutableListOf<String>()
        wire.enqueue(403, challengeBody)
        wire.enqueue(200, """{"id":"u1"}""")
        val result = transport(verification = { challenge, op ->
            seen += "${challenge.challengeId} ${challenge.scope} ${challenge.factors} $op"
            VerificationOutcome.Verified("grant-1")
        }).execute(getMe, OperationCall())
        assertIs<StapelResult.Ok<Me>>(result)
        assertEquals(listOf("chg_1 password_change [totp, email_otp] auth_me"), seen)
        assertNull(wire.requests[0].header("X-Verification-Token"))
        assertEquals("grant-1", wire.requests[1].header("X-Verification-Token"))
    }

    @Test
    fun declinedOrRepeatedChallengeIsReturned() = runTest {
        var calls = 0
        wire.enqueue(403, challengeBody)
        wire.enqueue(403, challengeBody)
        val error = (transport(verification = { _, _ -> calls++; VerificationOutcome.Verified(null) }).execute(getMe, OperationCall()) as StapelResult.Err)
            .error.failure()
        assertIs<TransportFailure.VerificationRequired>(error)
        assertEquals(1, calls, "a recovery fires at most once per logical call")
        assertEquals(2, wire.requests.size)
    }

    @Test
    fun enrollmentRequiredIsADomainRefusalNotAChallenge() = runTest {
        wire.enqueue(403, """{"localizable_error":"error.403.verification_enrollment_required","verification":{"scope":"x","factors":["totp"],"enroll":true}}""")
        var calls = 0
        val error = (transport(verification = { _, _ -> calls++; VerificationOutcome.Declined }).execute(getMe, OperationCall()) as StapelResult.Err).error
        assertIs<TestError.Domain>(error)
        assertEquals(0, calls)
    }

    // Plug-ins -------------------------------------------------------------------------------

    @Test
    fun aPluginRecoversAtMostOncePerCall() = runTest {
        var asked = 0
        val plugin = object : NetworkPlugin {
            override val id = "test.once"

            override suspend fun recover(context: RequestContext, error: stapel.result.StapelError): Recovery {
                asked++
                return Recovery.Retry { it.setHeader("X-Test", "retry") }
            }
        }
        wire.enqueueEnvelope(400, "error.400.something", error = "text", language = "en")
        wire.enqueueEnvelope(400, "error.400.something", error = "text", language = "en")
        val error = (transport(plugins = listOf(plugin)).execute(getMe, OperationCall()) as StapelResult.Err).error
        assertIs<TestError.Domain>(error)
        assertEquals(1, asked)
        assertEquals(listOf(null, "retry"), wire.requests.map { it.header("X-Test") })
    }

    @Test
    fun setBodyFieldAddsToAJsonObjectBody() = runTest {
        val plugin = object : NetworkPlugin {
            override val id = "test.field"

            override suspend fun recover(context: RequestContext, error: stapel.result.StapelError) =
                Recovery.Retry { assertTrue(it.setBodyField("captcha_token", "tok")) }
        }
        wire.enqueueEnvelope(400, "error.400.captcha_required", error = "text", language = "en")
        wire.enqueue(204)
        transport(plugins = listOf(plugin)).execute(requestOtp, OperationCall(body = OtpRequest("+1")))
        assertEquals("""{"phone":"+1"}""", (wire.requests[0].body as WireBody.Bytes).text())
        assertEquals("""{"phone":"+1","captcha_token":"tok"}""", (wire.requests[1].body as WireBody.Bytes).text())
    }

    // Envelope decoding ----------------------------------------------------------------------

    @Test
    fun envelopeIsDecodedIncludingLegacyLanguageAndRequestId() = runTest {
        wire.enqueue(400, """{"localizable_error":"error.400.invalid_code","error":"Неверный код","language":"ru","params":{"attempts_remaining":2,"nested":{"a":1}}}""", listOf("X-Request-ID" to "srv-9"))
        val error = (transport().execute(getMe, OperationCall()) as StapelResult.Err).error as TestError.Domain
        val envelope = error.envelope
        assertEquals("error.400.invalid_code", envelope.code.value)
        assertEquals("Неверный код", envelope.serverText)
        assertEquals("ru", envelope.serverLanguage)
        assertEquals("2", envelope.params["attempts_remaining"])
        assertEquals("""{"a":1}""", envelope.params["nested"])
        assertEquals("srv-9", envelope.requestId)
    }

    @Test
    fun htmlOrEmptyBodyIsTheSynthesizedStatusCode() = runTest {
        wire.enqueue(404, "<html>nginx</html>")
        val error = (transport().execute(getMe, OperationCall()) as StapelResult.Err).error.failure()
        assertIs<TransportFailure.HttpStatus>(error)
        assertEquals("stapel.http.404", error.code.value)
        assertEquals(FIRST_ID, error.envelope.requestId, "the sent request id stands in when the server echoes none")
    }

    @Test
    fun rateLimitTakesParamsBeforeTheHeader() = runTest {
        wire.enqueueJson(429, """{"localizable_error":"error.429.rate_limit","error":"text","error_language":"en","params":{"retry_after":120,"retry_after_minutes":2}}""")
        val error = (transport(config = NetworkConfig(retry = RetryPolicy.None)).execute(getMe, OperationCall()) as StapelResult.Err)
            .error.failure()
        assertIs<TransportFailure.RateLimited>(error)
        assertEquals(120.seconds, error.retryAfter)
    }

    @Test
    fun redirectIsNotFollowedAndIsAFailure() = runTest {
        wire.enqueue(302, "", listOf("Location" to "https://login.example.com/"))
        val result = transport().execute(getMe, OperationCall())
        assertIs<StapelResult.Err<TestError>>(result)
        assertEquals(1, wire.requests.size)
    }

    // Retry matrix ---------------------------------------------------------------------------

    @Test
    fun queryRetriesTransportFaultsAndUnavailable() = runTest {
        wire.enqueueFailure(TransportFailure.Timeout(TimeoutPhase.Socket), requestSent = true)
        wire.enqueue(503, "")
        wire.enqueue(200, """{"id":"u1"}""")
        assertIs<StapelResult.Ok<Me>>(transport().execute(getMe, OperationCall()))
        assertEquals(3, wire.requests.size)
        assertEquals(setOf(FIRST_ID), wire.requests.map { it.header("X-Request-ID") }.toSet())
    }

    @Test
    fun retriesStopAtMaxAttempts() = runTest {
        wire.fallback = { WireOutcome.Response(503, emptyList(), ByteArray(0)) }
        val error = (transport().execute(getMe, OperationCall()) as StapelResult.Err).error.failure()
        assertIs<TransportFailure.Unavailable>(error)
        assertEquals(3, wire.requests.size)
    }

    @Test
    fun mutationIsNotResentOnceItMayHaveArrived() = runTest {
        wire.enqueueFailure(TransportFailure.ConnectionFailed("reset"), requestSent = true)
        val error = (transport().execute(requestOtp, OperationCall(body = OtpRequest("+1"))) as StapelResult.Err).error.failure()
        assertIs<TransportFailure.ConnectionFailed>(error)
        assertEquals(1, wire.requests.size)
    }

    @Test
    fun mutationIsResentWhenItProvablyNeverLeft() = runTest {
        wire.enqueueFailure(TransportFailure.ConnectionFailed("refused"), requestSent = false)
        wire.enqueue(204)
        assertIs<StapelResult.Ok<Unit>>(transport().execute(requestOtp, OperationCall(body = OtpRequest("+1"))))
        assertEquals(2, wire.requests.size)
    }

    @Test
    fun mutationIsNotResentOn503() = runTest {
        wire.enqueue(503, "")
        transport().execute(requestOtp, OperationCall(body = OtpRequest("+1")))
        assertEquals(1, wire.requests.size)
    }

    @Test
    fun pinMismatchIsNeverRetried() = runTest {
        wire.enqueueFailure(TransportFailure.TlsRejected(pinMismatch = true), requestSent = false)
        val error = (transport().execute(getMe, OperationCall()) as StapelResult.Err).error.failure()
        assertIs<TransportFailure.TlsRejected>(error)
        assertEquals(1, wire.requests.size)
    }

    @Test
    fun shortRateLimitIsWaitedOutForQueriesOnly() = runTest {
        wire.enqueueJson(429, """{"localizable_error":"error.429.rate_limit","error":"text","error_language":"en","params":{"retry_after":2}}""")
        wire.enqueue(200, """{"id":"u1"}""")
        assertIs<StapelResult.Ok<Me>>(transport().execute(getMe, OperationCall()))
        wire.enqueueJson(429, """{"localizable_error":"error.429.rate_limit","error":"text","error_language":"en","params":{"retry_after":60}}""")
        assertIs<StapelResult.Err<TestError>>(transport().execute(getMe, OperationCall()))
        assertEquals(3, wire.requests.size, "a long wait is not honoured automatically")
    }

    @Test
    fun recoveriesDoNotConsumeTransportAttempts() = runTest {
        wire.enqueueEnvelope(401, "error.401.unauthorized", error = "text", language = "en")
        wire.enqueue(503, "")
        wire.enqueue(503, "")
        wire.enqueue(200, """{"id":"u1"}""")
        assertIs<StapelResult.Ok<Me>>(transport().execute(getMe, OperationCall()))
        assertEquals(4, wire.requests.size)
    }

    @Test
    fun offlineShortCircuitsWithoutSending() = runTest {
        val connectivity = object : Connectivity {
            val reports = mutableListOf<String>()
            override val status: StateFlow<NetworkStatus> = MutableStateFlow(NetworkStatus.Offline)

            override fun reportReachable() {
                reports += "up"
            }

            override fun reportUnreachable() {
                reports += "down"
            }
        }
        val error = (transport(connectivity = connectivity).execute(getMe, OperationCall()) as StapelResult.Err).error.failure()
        assertIs<TransportFailure.Offline>(error)
        assertTrue(wire.requests.isEmpty())
    }

    // URL ------------------------------------------------------------------------------------

    @Test
    fun pathParamsAreEncodedAndNullQueryValuesDropped() = runTest {
        val session = spec<Unit, Unit>(id = "s", method = "DELETE", path = "/auth/api/v1/sessions/{id}/", responseKind = stapel.wire.ResponseKind.Empty, success = mapOf(204 to null))
        val t = StapelTransport(deps(testEnvironment("https://h.example.com/identity/v2")), transport = wire)
        assertEquals(
            "https://h.example.com/identity/v2/sessions/a%2Fb%20c/?f.brand=x&f.brand=y",
            t.url(session, OperationCall<Unit>(pathParams = listOf("a/b c"), query = listOf("f.brand" to "x", "skip" to null, "f.brand" to "y"))),
        )
    }

    @Test
    fun acceptLanguageCarriesTheBaseLanguage() {
        assertEquals("ru-RU,ru;q=0.9", acceptLanguage("ru-RU"))
        assertEquals("en", acceptLanguage("en"))
    }
}
