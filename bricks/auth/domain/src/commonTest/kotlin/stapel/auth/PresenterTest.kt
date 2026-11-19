package stapel.auth

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import stapel.auth.api.error.AuthApiError
import stapel.auth.api.model.AuthResponseStatus
import stapel.auth.api.model.FirstLoginChallengeResponse
import stapel.auth.api.model.FirstLoginChallengeResponseStatus
import stapel.auth.api.model.Requires
import stapel.auth.api.model.TOTPChallengeResponse
import stapel.auth.api.model.TOTPChallengeResponseStatus
import stapel.auth.api.model.TOTPChallengeVerify
import stapel.auth.error.AuthErrorReason
import stapel.auth.model.AuthMethodId
import stapel.auth.model.OtpChannel
import stapel.auth.model.SignInKind
import stapel.auth.model.TotpProof
import stapel.auth.platform.AuthPlatform
import stapel.auth.presentation.AuthEffect
import stapel.auth.presentation.ChallengeIntent
import stapel.auth.presentation.ChallengeStep
import stapel.auth.presentation.GuestIntent
import stapel.auth.presentation.GuestPresenter
import stapel.auth.presentation.GuestState
import stapel.auth.presentation.MethodChooserPresenter
import stapel.auth.presentation.MethodChooserState
import stapel.auth.presentation.OtpSignInIntent
import stapel.auth.presentation.OtpSignInPresenter
import stapel.auth.presentation.OtpSignInState
import stapel.auth.presentation.PasswordRegisterPresenter
import stapel.auth.presentation.PasswordRegisterState
import stapel.auth.presentation.PasswordSignInIntent
import stapel.auth.presentation.PasswordSignInPresenter
import stapel.auth.presentation.PasswordSignInState
import stapel.auth.session.AuthState
import stapel.mvi.record
import stapel.testing.FakeAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PresenterTest {
    private val unfinishable = """,
        {"id":"oauth","enabled":true,"placement":"bottom","order":2,"interaction":"redirect","icon_svg":"","can_login":true},
        {"id":"passkey","enabled":true,"placement":"main","order":3,"interaction":"inline","icon_svg":"","can_login":true},
        {"id":"magic_link","enabled":true,"placement":"overflow","order":4,"interaction":"modal","icon_svg":"","can_login":true},
        {"id":"sso","enabled":true,"placement":"main","order":5,"interaction":"redirect","icon_svg":"","can_login":true},
        {"id":"qr","enabled":true,"placement":"overflow","order":6,"interaction":"modal","icon_svg":"","can_login":true}"""

    private object AnyBrowser : stapel.auth.platform.ExternalUserAgent {
        override suspend fun authenticate(url: String, redirectUri: String): String? = null
    }

    private object AnyPasskeys : stapel.auth.platform.PasskeyAuthenticator {
        override val isAvailable: Boolean = true

        override suspend fun create(creationOptionsJson: String): String? = null

        override suspend fun get(requestOptionsJson: String): String? = null
    }

    @Test
    fun methodsTheClientCannotCompleteAreNeverOfferedEvenWithAdaptersPresent() = runTest {
        val f = Fixture(this, platform = AuthPlatform(browser = AnyBrowser, passkeys = AnyPasskeys)).start()
        f.transport.on(Ops.CAPABILITIES) { FakeAnswer.Value(capabilities(extraMethods = unfinishable)) }
        val p = MethodChooserPresenter(f.auth, f.context)
        runCurrent()
        val ready = assertIs<MethodChooserState.Ready>(p.state.value)
        assertEquals(listOf(AuthMethodId.Email, AuthMethodId.Phone, AuthMethodId.Password), ready.zones.all.map { it.id })
        assertEquals(listOf(AuthMethodId.Email, AuthMethodId.Phone), ready.zones.main.map { it.id })
    }

    @Test
    fun theProjectFenceNarrowsTheServerList() = runTest {
        val f = Fixture(this, config = { AuthConfig(methods = setOf(AuthMethodId.Phone), clock = it) }).start()
        val p = MethodChooserPresenter(f.auth, f.context)
        runCurrent()
        assertEquals(listOf(AuthMethodId.Phone), assertIs<MethodChooserState.Ready>(p.state.value).zones.all.map { it.id })
    }

    @Test
    fun otpRequestThenCodeSignsInAndEmitsTheEffectOnce() = runTest {
        val f = Fixture(this).start()
        f.transport.on(Ops.EMAIL_REQUEST) { FakeAnswer.Value(sent) }
        f.transport.on(Ops.EMAIL_VERIFY) { FakeAnswer.Value(session("u1", AuthResponseStatus.Registered)) }
        val p = OtpSignInPresenter(f.auth, f.context)
        val rec = p.record(backgroundScope)
        p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, " a@example.com "))
        runCurrent()
        val codeSent = assertIs<OtpSignInState.CodeSent>(p.state.value)
        assertEquals(4, codeSent.codeLength)
        assertTrue(codeSent.isMock)
        assertEquals("a***@example.com", codeSent.maskedTarget)
        p.dispatch(OtpSignInIntent.SubmitCode("0000"))
        runCurrent()
        val done = assertIs<OtpSignInState.Done>(p.state.value)
        assertEquals(SignInKind.Registered, done.outcome.kind)
        assertEquals(1, rec.effects.filterIsInstance<AuthEffect.SignedIn>().size)
        assertIs<AuthState.SignedIn>(f.auth.session.state.value)
        rec.stop()
    }

    @Test
    fun resendIsIgnoredDuringTheCooldownAndSentOnceAfterIt() = runTest {
        val f = Fixture(this).start()
        f.transport.on(Ops.EMAIL_REQUEST) { FakeAnswer.Value(sent) }
        val p = OtpSignInPresenter(f.auth, f.context)
        p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, "a@example.com"))
        runCurrent()
        assertEquals(30.seconds, assertIs<OtpSignInState.CodeSent>(p.state.value).resendIn)
        p.dispatch(OtpSignInIntent.Resend)
        runCurrent()
        assertEquals(1, f.calls(Ops.EMAIL_REQUEST), "ignored while the timer runs")
        advanceTimeBy(29.seconds)
        runCurrent()
        p.dispatch(OtpSignInIntent.Resend)
        runCurrent()
        assertEquals(1, f.calls(Ops.EMAIL_REQUEST), "still one second to go")
        advanceTimeBy(2.seconds)
        runCurrent()
        assertTrue(assertIs<OtpSignInState.CodeSent>(p.state.value).canResend)
        p.dispatch(OtpSignInIntent.Resend)
        runCurrent()
        assertEquals(2, f.calls(Ops.EMAIL_REQUEST))
    }

    @Test
    fun wrongCodeKeepsTheStepWithAttemptsAndBlockedLocks() = runTest {
        val f = Fixture(this).start()
        f.transport.on(Ops.EMAIL_REQUEST) { FakeAnswer.Value(sent) }
        f.transport.on(Ops.EMAIL_VERIFY) { refusal("error.400.invalid_code_attempts", 400, mapOf("attempts_remaining" to "2")) }
        val p = OtpSignInPresenter(f.auth, f.context)
        p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, "a@example.com"))
        runCurrent()
        p.dispatch(OtpSignInIntent.SubmitCode("1111"))
        runCurrent()
        val wrong = assertIs<OtpSignInState.CodeSent>(p.state.value)
        assertEquals(AuthErrorReason.InvalidCode, wrong.error?.reason)
        assertEquals(2, wrong.error?.attemptsRemaining)
        assertNull(f.sessions.currentTokens())

        f.transport.on(Ops.EMAIL_VERIFY) { refusal("error.422.blocked", 422, mapOf("retry_after_minutes" to "15")) }
        p.dispatch(OtpSignInIntent.SubmitCode("2222"))
        runCurrent()
        val locked = assertIs<OtpSignInState.Locked>(p.state.value)
        assertEquals(f.clock.now() + (15 * 60).seconds, locked.until)
    }

    @Test
    fun aRateLimitOnTheCodeRequestIsATypedLockWithTheServersWaitAndAWayBack() = runTest {
        val f = Fixture(this).start()
        f.transport.on(Ops.EMAIL_REQUEST) {
            refusal("error.429.rate_limit", 429, mapOf("retry_after" to "30", "retry_after_display" to "0:30", "retry_after_minutes" to "1"))
        }
        val p = OtpSignInPresenter(f.auth, f.context)
        p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, "a@example.com"))
        runCurrent()
        val locked = assertIs<OtpSignInState.Locked>(p.state.value)
        assertEquals(AuthErrorReason.RateLimited, locked.error.reason)
        assertIs<AuthApiError.RateLimit>(locked.error.cause, "the operation's typed domain error, not a transport failure")
        assertEquals(30.seconds, locked.error.retryAfter, "seconds win over the rounded-up minutes")
        assertEquals("0:30", locked.error.retryAfterDisplay)
        assertEquals(f.clock.now() + 30.seconds, locked.until)
        assertEquals(1, f.calls(Ops.EMAIL_REQUEST), "no blind retry")

        p.dispatch(OtpSignInIntent.EditIdentifier)
        runCurrent()
        assertEquals(OtpSignInState.Idle(OtpChannel.Email, "a@example.com"), p.state.value)
    }

    @Test
    fun captchaRequiredWithoutASolverIsATypedRequestFailure() = runTest {
        val f = Fixture(this).start()
        f.transport.on(Ops.EMAIL_REQUEST) { refusal("error.400.captcha_required", 400) }
        val p = OtpSignInPresenter(f.auth, f.context)
        p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, "a@example.com"))
        runCurrent()
        assertEquals(AuthErrorReason.CaptchaRequired, assertIs<OtpSignInState.RequestFailed>(p.state.value).error.reason)
        assertEquals(1, f.calls(Ops.EMAIL_REQUEST), "no blind retry")
    }

    @Test
    fun totpChallengeIsTheSharedStepAndCompletesTheSignIn() = runTest {
        val f = Fixture(this).start()
        f.transport.on(Ops.PASSWORD_LOGIN) {
            FakeAnswer.Value(TOTPChallengeResponse(TOTPChallengeResponseStatus.TotpRequired, challengeToken = "ch-1", expiresIn = 300))
        }
        f.transport.on(Ops.TOTP_VERIFY) { FakeAnswer.Value(session("u1", AuthResponseStatus.LoggedIn)) }
        val p = PasswordSignInPresenter(f.auth, f.context)
        p.dispatch(PasswordSignInIntent.SignIn("alice", "pw"))
        runCurrent()
        val step = assertIs<ChallengeStep.Totp>(assertIs<PasswordSignInState.Challenge>(p.state.value).step)
        assertNull(f.sessions.currentTokens(), "no session before the second factor")
        p.dispatch(ChallengeIntent.SubmitTotp(TotpProof.BackupCode("bk-1")))
        runCurrent()
        assertIs<PasswordSignInState.Done>(p.state.value)
        assertEquals(TOTPChallengeVerify(challengeToken = step.challenge.token, backupCode = "bk-1"), f.bodies(Ops.TOTP_VERIFY).single())
    }

    @Test
    fun anExpiredChallengeIsRefusedLocallyWithoutACall() = runTest {
        val f = Fixture(this).start()
        f.transport.on(Ops.PASSWORD_LOGIN) {
            FakeAnswer.Value(TOTPChallengeResponse(TOTPChallengeResponseStatus.TotpRequired, challengeToken = "ch-1", expiresIn = 60))
        }
        val p = PasswordSignInPresenter(f.auth, f.context)
        p.dispatch(PasswordSignInIntent.SignIn("alice", "pw"))
        runCurrent()
        advanceTimeBy(61.seconds)
        p.dispatch(ChallengeIntent.SubmitTotp(TotpProof.Code("123456")))
        runCurrent()
        val step = assertIs<ChallengeStep.Totp>(assertIs<PasswordSignInState.Challenge>(p.state.value).step)
        assertEquals(AuthErrorReason.ChallengeExpired, step.error!!.reason)
        assertEquals(0, f.calls(Ops.TOTP_VERIFY))
    }

    @Test
    fun forcedPasswordChangeLeadsToTheSessionAndMfaEnrolIsADeadEndStateNotACrash() = runTest {
        val f = Fixture(this).start()
        f.transport.on(Ops.PASSWORD_LOGIN) {
            FakeAnswer.Value(FirstLoginChallengeResponse(FirstLoginChallengeResponseStatus.FirstLoginRequired, Requires.PasswordChange, "fl-1", 300))
        }
        f.transport.on(Ops.FORCED_CHANGE) { FakeAnswer.Value(session("u1", AuthResponseStatus.LoggedIn)) }
        val p = PasswordSignInPresenter(f.auth, f.context)
        p.dispatch(PasswordSignInIntent.SignIn("alice", "pw"))
        runCurrent()
        assertIs<ChallengeStep.ForcedPasswordChange>(assertIs<PasswordSignInState.Challenge>(p.state.value).step)
        p.dispatch(ChallengeIntent.SubmitNewPassword("new-password-1"))
        runCurrent()
        assertIs<PasswordSignInState.Done>(p.state.value)

        f.transport.on(Ops.PASSWORD_LOGIN) {
            FakeAnswer.Value(FirstLoginChallengeResponse(FirstLoginChallengeResponseStatus.FirstLoginRequired, Requires.MfaEnroll, "fl-2", 300))
        }
        val q = PasswordSignInPresenter(f.auth, f.context)
        q.dispatch(PasswordSignInIntent.SignIn("bob", "pw"))
        runCurrent()
        assertIs<ChallengeStep.MfaEnrollRequired>(assertIs<PasswordSignInState.Challenge>(q.state.value).step)
    }

    @Test
    fun wrongPasswordIsTypedAndWritesNothing() = runTest {
        val f = Fixture(this).start()
        f.transport.on(Ops.PASSWORD_LOGIN) { refusal("error.401.invalid_credentials", 401) }
        val p = PasswordSignInPresenter(f.auth, f.context)
        p.dispatch(PasswordSignInIntent.SignIn("alice", "nope"))
        runCurrent()
        assertEquals(AuthErrorReason.InvalidCredentials, assertIs<PasswordSignInState.Failed>(p.state.value).error.reason)
        assertNull(f.sessions.currentTokens())
    }

    @Test
    fun registrationIsUnavailableWhenThePostureKeepsItClosed() = runTest {
        val closed = Fixture(this).start()
        val p = PasswordRegisterPresenter(closed.auth, closed.context)
        runCurrent()
        assertEquals(PasswordRegisterState.Unavailable, p.state.value)

        val open = Fixture(this).start()
        open.transport.on(Ops.CAPABILITIES) { FakeAnswer.Value(capabilities(passwordRegistration = true)) }
        val q = PasswordRegisterPresenter(open.auth, open.context)
        runCurrent()
        assertEquals(PasswordRegisterState.Idle, q.state.value)
    }

    @Test
    fun guestPolicyOffRefusesWithoutACall() = runTest {
        val f = Fixture(this, config = { AuthConfig(guest = GuestPolicy.Off, clock = it) }).start()
        val p = GuestPresenter(f.auth, f.context)
        p.dispatch(GuestIntent.ContinueAsGuest)
        runCurrent()
        assertEquals(AuthErrorReason.Unsupported, assertIs<GuestState.Failed>(p.state.value).error.reason)
        assertEquals(0, f.calls(Ops.ANONYMOUS))
    }
}
