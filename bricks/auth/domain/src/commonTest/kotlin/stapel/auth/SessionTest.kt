package stapel.auth

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import stapel.auth.api.AuthApiClient
import stapel.auth.api.model.AuthResponseStatus
import stapel.auth.api.model.LogoutRequest
import stapel.auth.api.model.LogoutResponse
import stapel.auth.api.model.TokenPairResponse
import stapel.auth.error.AuthError
import stapel.auth.error.AuthErrorReason
import stapel.auth.session.AuthState
import stapel.auth.session.AuthTokenRefresher
import stapel.auth.session.LogoutResult
import stapel.auth.session.SessionChange
import stapel.auth.session.SignOutReason
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.session.RefreshOutcome
import stapel.session.SessionEnd
import stapel.session.TokenPair
import stapel.testing.FakeAnswer
import stapel.testing.FakeApiTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SessionTest {
    private fun refresherAnswering(answer: FakeAnswer): RefreshOutcome {
        val transport = FakeApiTransport().apply { on(Ops.REFRESH) { answer } }
        var out: RefreshOutcome? = null
        kotlinx.coroutines.test.runTest { out = AuthTokenRefresher(AuthApiClient(transport)).refresh(TokenPair("a", "r")) }
        return out!!
    }

    @Test
    fun refreshCodesMapToSessionEnds() {
        val table = mapOf(
            "error.401.refresh_revoked" to SessionEnd.Revoked,
            "error.401.token_revoked" to SessionEnd.Revoked,
            "error.401.refresh_invalid" to SessionEnd.Expired,
            "error.401.refresh_not_provided" to SessionEnd.Expired,
            "error.401.account_disabled" to SessionEnd.AccountDisabled,
            "error.401.something_new" to SessionEnd.Unknown,
        )
        for ((code, end) in table) {
            // The pipeline classifies every 401 as a transport `Unauthenticated` carrying the envelope.
            val envelope = ErrorEnvelope(ErrorCode(code), 401, ErrorParams.Empty)
            val outcome = refresherAnswering(FakeAnswer.Failure(TransportFailure.Unauthenticated(envelope)))
            assertEquals(RefreshOutcome.Rejected(end, code), outcome, code)
            // Also as a domain refusal (a contract that binds the code to the operation).
            assertEquals(RefreshOutcome.Rejected(end, code), refresherAnswering(refusal(code, 401)), "$code as domain")
        }
    }

    @Test
    fun anUnmeasuredRefreshIsTransientNeverAnEnd() {
        val transient = listOf(
            FakeAnswer.Failure(TransportFailure.Offline("down")),
            FakeAnswer.Failure(TransportFailure.ServerFault(ErrorEnvelope(ErrorCode("stapel.http.500"), 500, ErrorParams.Empty))),
            FakeAnswer.Failure(TransportFailure.RateLimited(null, ErrorEnvelope(ErrorCode("error.429.rate_limit"), 429, ErrorParams.Empty))),
            refusal("error.400.bad_request", 400),
        )
        for (answer in transient) assertIs<RefreshOutcome.Transient>(refresherAnswering(answer))
    }

    @Test
    fun rotationReturnsBothNewTokens() {
        val outcome = refresherAnswering(FakeAnswer.Value(TokenPairResponse(refresh = "r2", access = "a2")))
        assertEquals(RefreshOutcome.Rotated(TokenPair("a2", "r2")), outcome)
    }

    @Test
    fun guestPromotedInPlaceKeepsTheIdAndMergeReportsBothIds() = runTest {
        val f = Fixture(this).start()
        val changes = mutableListOf<SessionChange>()
        backgroundScope.launch { f.auth.session.changes.toList(changes) }
        runCurrent()
        f.transport.on(Ops.ANONYMOUS) { FakeAnswer.Value(session("guest-1", AuthResponseStatus.Registered, guest = true)) }
        val guest = (f.auth.session.ensureGuest() as StapelResult.Ok).value
        runCurrent()
        assertTrue(guest.isGuest)
        assertIs<AuthState.Guest>(f.auth.session.state.value)

        // Promotion: same id, no longer a guest.
        f.auth.sessionImpl.adopt(session("guest-1", AuthResponseStatus.Registered, salt = "p"))
        runCurrent()
        assertEquals(SessionChange.Promoted::class, changes.last()::class)
        assertIs<AuthState.SignedIn>(f.auth.session.state.value)

        // A second guest merged into an existing account: the id changes.
        f.sessions.logout()
        runCurrent()
        f.transport.on(Ops.ANONYMOUS) { FakeAnswer.Value(session("guest-2", AuthResponseStatus.Registered, guest = true)) }
        f.auth.session.ensureGuest()
        f.auth.sessionImpl.adopt(session("acct-9", AuthResponseStatus.Merged))
        runCurrent()
        val merged = changes.last()
        assertIs<SessionChange.Merged>(merged)
        assertEquals("guest-2", merged.fromGuestId)
        assertEquals("acct-9", merged.into.id)
    }

    @Test
    fun signInWithoutAPriorGuestIsAPlainSignIn() = runTest {
        val f = Fixture(this).start()
        val changes = mutableListOf<SessionChange>()
        backgroundScope.launch { f.auth.session.changes.toList(changes) }
        runCurrent()
        f.auth.sessionImpl.adopt(session("u1", AuthResponseStatus.LoggedIn))
        runCurrent()
        assertIs<SessionChange.SignedIn>(changes.single())
    }

    @Test
    fun logoutSendsTheCurrentRefreshTokenAndAlwaysClearsLocally() = runTest {
        val f = Fixture(this).start()
        f.auth.sessionImpl.adopt(session("u1", AuthResponseStatus.LoggedIn))
        f.transport.on(Ops.LOGOUT) { FakeAnswer.Value(LogoutResponse("ok")) }
        assertEquals(LogoutResult.RevokedOnServer, f.auth.session.logout())
        assertEquals(LogoutRequest("r-u1-"), f.bodies(Ops.LOGOUT).single())
        runCurrent()
        assertEquals(AuthState.SignedOut(SignOutReason.UserRequested), f.auth.session.state.value)
        assertNull(f.sessions.currentTokens())

        // Server refuses (or is unreachable): still signed out locally, reported as LocalOnly.
        f.auth.sessionImpl.adopt(session("u2", AuthResponseStatus.LoggedIn))
        f.transport.on(Ops.LOGOUT) { FakeAnswer.Failure(TransportFailure.Offline("down")) }
        assertEquals(LogoutResult.LocalOnly, f.auth.session.logout())
        assertNull(f.sessions.currentTokens())
    }

    @Test
    fun farewellHooksRunWithTheBearerValidBeforeTheServerRevokeAndNeverBlockSignOut() = runTest {
        val f = Fixture(this).start()
        f.auth.sessionImpl.adopt(session("u1", AuthResponseStatus.LoggedIn))
        f.transport.on(Ops.LOGOUT) { FakeAnswer.Value(LogoutResponse("ok")) }
        val seen = mutableListOf<String>()
        f.sessions.registerFarewellHook {
            // What a hook's authenticated call would carry, and whether the revoke already went out.
            seen += "token=${f.sessions.currentTokens()?.accessToken != null} logoutCalls=${f.calls(Ops.LOGOUT)}"
        }
        f.sessions.registerFarewellHook { throw IllegalStateException("a failing farewell") }
        f.sessions.registerFarewellHook { kotlinx.coroutines.awaitCancellation() }

        assertEquals(LogoutResult.RevokedOnServer, f.auth.session.logout())
        assertEquals(listOf("token=true logoutCalls=0"), seen)
        assertEquals(1, f.calls(Ops.LOGOUT), "the revoke still went out after a failing and a hanging hook")
        runCurrent()
        assertEquals(AuthState.SignedOut(SignOutReason.UserRequested), f.auth.session.state.value)
        assertNull(f.sessions.currentTokens())
    }

    @Test
    fun aRejectedRefreshSignsOutWithTheMappedReason() = runTest {
        val f = Fixture(this, refresher = { RefreshOutcome.Rejected(SessionEnd.Revoked, "error.401.refresh_revoked") }).start()
        f.auth.sessionImpl.adopt(session("u1", AuthResponseStatus.LoggedIn))
        val changes = mutableListOf<SessionChange>()
        backgroundScope.launch { f.auth.session.changes.toList(changes) }
        runCurrent()
        f.sessions.refresh(null)
        runCurrent()
        assertEquals(AuthState.SignedOut(SignOutReason.Revoked), f.auth.session.state.value)
        assertEquals(SessionChange.SignedOut("u1", SignOutReason.Revoked), changes.single())
    }

    @Test
    fun errorsAreClassifiedByCode() {
        fun reason(code: String, status: Int, params: Map<String, String> = emptyMap()) =
            AuthError.of(stapel.result.ServerRefusal(ErrorEnvelope(ErrorCode(code), status, ErrorParams(params)))).reason
        assertEquals(AuthErrorReason.InvalidCode, reason("error.400.invalid_code_attempts", 400))
        assertEquals(AuthErrorReason.Blocked, reason("error.422.blocked", 422))
        assertEquals(AuthErrorReason.AccountLocked, reason("error.423.account_locked", 423))
        assertEquals(AuthErrorReason.RateLimited, reason("error.429.rate_limit", 429))
        assertEquals(AuthErrorReason.InvalidCredentials, reason("error.401.invalid_credentials", 401))
        assertEquals(AuthErrorReason.CaptchaRequired, reason("error.400.captcha_required", 400))
        assertEquals(AuthErrorReason.CaptchaInvalid, reason("error.400.captcha_invalid", 400))
        assertEquals(AuthErrorReason.AlreadyTaken, reason("error.409.email_taken", 409))
        assertEquals(AuthErrorReason.InvalidInput, reason("error.400.field.required", 400))
        val attempts = AuthError.of(stapel.result.ServerRefusal(ErrorEnvelope(ErrorCode("error.400.invalid_code_attempts"), 400, ErrorParams(mapOf("attempts_remaining" to "3")))))
        assertEquals(3, attempts.attemptsRemaining)
        assertEquals(AuthErrorReason.Network, AuthError.of(TransportFailure.Offline("x")).reason)
    }
}
