package stapel.result

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private fun env(status: Int, code: String? = null, params: Map<String, String> = emptyMap()): ErrorEnvelope =
    if (code == null) ErrorEnvelope.synthesized(status) else ErrorEnvelope(ErrorCode(code), status, ErrorParams(params))

/** A generated-style operation error: domain classes plus the transport carrier. */
private sealed interface OpError : StapelError {
    data class Invalid(override val envelope: ErrorEnvelope) : OpError, DomainError {
        override val remediation: Remediation get() = Remediation.FixInput
    }
    data class Transport(override val failure: TransportFailure) : OpError, TransportCarrier
}

class ErrorModelTest {
    @Test
    fun resultCombinators() {
        val ok: StapelResult<Int, StapelError> = StapelResult.Ok(2)
        val err: StapelResult<Int, StapelError> = StapelResult.Err(TransportFailure.Offline())
        assertEquals(StapelResult.Ok(4), ok.map { it * 2 })
        assertSame(err, err.map { it * 2 })
        assertEquals(StapelResult.Ok("2"), ok.flatMap { StapelResult.Ok(it.toString()) })
        assertEquals(-1, err.fold({ it }, { -1 }))
        assertEquals(2, ok.getOrNull())
        assertNull(err.getOrNull())
        assertEquals(TransportFailure.Offline(), err.errorOrNull())
        assertEquals(7, err.getOrElse { 7 })
        val thrown = assertFailsWith<StapelException> { err.getOrThrow() }
        assertEquals(CoreErrorCodes.TransportFailed, thrown.error.code)
        val mapped = err.mapError { OpError.Transport(it.transportFailureOrNull()!!) }
        assertIs<OpError.Transport>(mapped.errorOrNull())
        var seen = 0
        ok.onOk { seen += it }.onErr { seen += 100 }
        assertEquals(2, seen)
        assertTrue(ok.isOk)
        assertTrue(err.isErr)
    }

    @Test
    fun embeddedStatus() {
        assertEquals(404, ErrorCode("error.404.not_found").embeddedStatus)
        assertEquals(503, ErrorCode("stapel.http.503").embeddedStatus)
        assertEquals(400, ErrorCode("error.400").embeddedStatus)
        assertNull(ErrorCode("auth.otp.invalid").embeddedStatus)
        assertNull(ErrorCode("error.4x0.bad").embeddedStatus)
        assertNull(ErrorCode("stapel.http.5xx").embeddedStatus)
    }

    @Test
    fun paramsParseNumbersLeniently() {
        val p = ErrorParams(mapOf("a" to "3", "b" to "4.0", "c" to "x", "d" to "4.5"))
        assertEquals(3L, p.long("a"))
        assertEquals(4L, p.long("b"))
        assertNull(p.long("c"))
        assertNull(p.long("d"))
        assertNull(p.int("missing"))
        assertEquals(ErrorParams(mapOf("a" to "3", "b" to "4.0", "c" to "x", "d" to "4.5")), p)
    }

    @Test
    fun classificationRules() {
        assertIs<TransportFailure.Unauthenticated>(classifyFailure(env(401, "error.401.not_authenticated")) { true })
        val challenge = VerificationChallenge("ch-1", "email_change", listOf("totp"))
        val v = classifyFailure(env(403, "error.403.verification_required"), challenge) { true }
        assertIs<TransportFailure.VerificationRequired>(v)
        assertEquals("ch-1", v.challenge.challengeId)
        // Known domain codes stay domain, even for 403 without challenge, 429 and 5xx.
        assertNull(classifyFailure(env(403, "error.403.change_requires_current")) { true })
        assertNull(classifyFailure(env(503, "error.503.verification_unavailable")) { true })
        assertNull(classifyFailure(env(429, "error.429.otp_cooldown")) { true })
        // Unknown codes: status-classified for 429/5xx, domain for other answered statuses.
        assertIs<TransportFailure.RateLimited>(classifyFailure(env(429, "error.429.throttled")))
        assertIs<TransportFailure.Unavailable>(classifyFailure(env(502, "error.502.x")))
        assertIs<TransportFailure.Unavailable>(classifyFailure(env(504)))
        assertIs<TransportFailure.ServerFault>(classifyFailure(env(500)))
        assertIs<TransportFailure.ServerFault>(classifyFailure(env(507, "error.507.full")))
        assertNull(classifyFailure(env(400, "error.400.brand_new_code")))
        assertNull(classifyFailure(env(403, "error.403.forbidden")))
        // No envelope: synthesized code, never domain even if a table claimed it.
        val bodiless = classifyFailure(env(404)) { true }
        assertIs<TransportFailure.HttpStatus>(bodiless)
        assertEquals("stapel.http.404", bodiless.code.value)
        assertIs<TransportFailure.HttpStatus>(classifyFailure(env(302)))
        // 401 without envelope keeps its synthesized code.
        assertEquals("stapel.http.401", classifyFailure(env(401))!!.code.value)
    }

    @Test
    fun retryAfterPrecedence() {
        fun wait(params: Map<String, String>, header: kotlin.time.Duration? = null) =
            (classifyFailure(env(429, "error.429.throttled", params), retryAfterHeader = header) as TransportFailure.RateLimited).retryAfter
        assertEquals(30.seconds, wait(mapOf("retry_after" to "30", "retry_after_minutes" to "5"), 9.seconds))
        assertEquals(5.minutes, wait(mapOf("retry_after_minutes" to "5"), 9.seconds))
        assertEquals(9.seconds, wait(emptyMap(), 9.seconds))
        assertNull(wait(emptyMap()))
        assertEquals(5.minutes, ServerRefusal(env(422, "error.422.blocked", mapOf("retry_after_minutes" to "5"))).retryAfter)
    }

    @Test
    fun remediationHeuristicMatchesTheBackend() {
        fun d(code: String, status: Int, vararg params: String) = Remediation.default(code, status, params.toList())
        assertEquals(Remediation.Verify, d("error.403.step_up_required", 403))
        assertEquals(Remediation.WaitAndRetry, d("error.400.cooldown", 400, "retry_after_minutes"))
        assertEquals(Remediation.WaitAndRetry, d("error.422.blocked", 422))
        assertEquals(Remediation.Reauthenticate, d("error.403.sso_required", 403))
        assertEquals(Remediation.Reauthenticate, d("error.401.x", 401))
        assertEquals(Remediation.ContactSupport, d("error.500.x", 500))
        assertEquals(Remediation.FixInput, d("error.409.x", 409))
        assertEquals(Remediation.Retry, d("error.404.thing_not_found", 404))
        assertEquals(Remediation.FixInput, d("error.404.gone", 404))
        assertEquals(Remediation.Retry, d("error.403.x", 403))
        assertEquals(Remediation.Retry, d("error.400.code_expired", 400))
        assertEquals(Remediation.FixInput, d("error.400.bad_email", 400))
        assertEquals(Remediation.Retry, d("error.418.x", 418))
        assertEquals(Remediation.WaitAndRetry, Remediation.fromWire("wait_and_retry"))
        assertEquals(Remediation.Unknown, Remediation.fromWire("pray"))
        assertEquals(Remediation.Unknown, Remediation.fromWire(""))
    }

    @Test
    fun carrierDelegatesEverything() {
        val inner = TransportFailure.RateLimited(3.seconds, env(429))
        val e: OpError = OpError.Transport(inner)
        assertEquals(inner.code, e.code)
        assertEquals(429, e.status)
        assertEquals(Remediation.WaitAndRetry, e.remediation)
        assertSame(inner, e.transportFailureOrNull())
        assertEquals(3.seconds, e.retryAfter)
        assertTrue(e.isTransient)
        assertNull(OpError.Invalid(env(400, "error.400.x")).transportFailureOrNull())
        // Exhaustive over the generated shape: compile-time proof that one `when` covers both families.
        val label = when (e) {
            is OpError.Invalid -> "domain"
            is OpError.Transport -> "transport"
        }
        assertEquals("transport", label)
    }

    @Test
    fun transientClassification() {
        assertTrue(TransportFailure.Offline().isTransient)
        assertTrue(TransportFailure.Timeout(TimeoutPhase.Request).isTransient)
        assertTrue(TransportFailure.TlsRejected(pinMismatch = false).isTransient)
        assertFalse(TransportFailure.TlsRejected(pinMismatch = true).isTransient)
        assertTrue(TransportFailure.Unavailable(env(503)).isTransient)
        assertTrue(TransportFailure.HttpStatus(env(408)).isTransient)
        assertFalse(TransportFailure.HttpStatus(env(404)).isTransient)
        assertFalse(TransportFailure.Unauthenticated().isTransient)
        assertFalse(TransportFailure.ContractViolation("op", "x").isTransient)
        assertFalse(ServerRefusal(env(400, "error.400.x")).isTransient)
    }

    @Test
    fun noResponseFailuresHaveNoStatusAndTheTransportCode() {
        for (e in listOf(TransportFailure.Offline(), TransportFailure.ConnectionFailed("dns"), TransportFailure.Timeout(TimeoutPhase.Connect))) {
            assertNull(e.status)
            assertNull(e.envelope)
            assertEquals(CoreErrorCodes.TransportFailed, e.code)
            assertEquals(Remediation.Retry, e.remediation)
        }
        assertEquals(Remediation.ContactSupport, TransportFailure.TlsRejected(pinMismatch = true).remediation)
        assertNull(TransportFailure.Unauthenticated().status)
        assertEquals(Remediation.Bug, TransportFailure.ContractViolation("op", "x", 200).remediation)
    }

    @Test
    fun snapshotRoundTripKeepsWhatTextAndBranchingNeed() {
        val original = ServerRefusal(
            ErrorEnvelope(ErrorCode("error.422.blocked"), 422, ErrorParams(mapOf("retry_after_minutes" to "5")), "Blocked", "en", "r1"),
        )
        val snap = original.snapshot()
        assertEquals("error.422.blocked", snap.code)
        assertEquals(Remediation.WaitAndRetry, snap.remediation)
        val back = snap.toError()
        assertEquals(original.code, back.code)
        assertEquals(original.status, back.status)
        assertEquals(original.params, back.params)
        assertEquals("r1", back.envelope?.requestId)
        assertEquals(snap, back.snapshot())
        assertNull(TransportFailure.Offline().snapshot().toError().envelope)
        assertTrue(TransportFailure.Offline().snapshot().transient)
    }

    @Test
    fun hasCode() {
        val e = ServerRefusal(env(400, "error.400.a"))
        assertTrue(e.hasCode("error.400.b", "error.400.a"))
        assertTrue(e.hasCode(ErrorCode("error.400.a")))
        assertFalse(e.hasCode("error.400.b"))
    }
}
