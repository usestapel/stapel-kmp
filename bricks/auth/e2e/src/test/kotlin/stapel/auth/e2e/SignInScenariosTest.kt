package stapel.auth.e2e

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import stapel.auth.api.AuthContract
import stapel.auth.api.model.PasswordOtpMethod
import stapel.auth.api.model.PasswordOtpRequest
import stapel.auth.api.model.PasswordOtpVerify
import stapel.auth.error.AuthErrorReason
import stapel.auth.model.AuthMethodId
import stapel.auth.model.OtpChannel
import stapel.auth.model.PostureStage
import stapel.auth.model.SignInKind
import stapel.auth.presentation.MethodChooserPresenter
import stapel.auth.presentation.MethodChooserState
import stapel.auth.presentation.OtpSignInIntent
import stapel.auth.presentation.OtpSignInPresenter
import stapel.auth.presentation.OtpSignInState
import stapel.auth.presentation.PasswordSignInIntent
import stapel.auth.presentation.PasswordSignInPresenter
import stapel.auth.presentation.PasswordSignInState
import stapel.auth.session.AuthState
import stapel.result.StapelResult
import stapel.session.SessionState
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Preconditions and sign-in scenarios (frame D12, ARCH-AUTH §9.2 D-1…D-5) against the real stand. */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SignInScenariosTest {

    @Test
    @Order(0)
    fun `P1-P3 the stand is the subject - prototype posture, mock OTP, 5 s tokens, pinned version`(): Unit = runBlocking {
        Device("p").use { d ->
            val caps = (d.auth.capabilities.get() as StapelResult.Ok).value
            assertEquals(PostureStage.Prototype, caps.stage)
            assertTrue(caps.emailMock && caps.phoneMock, "both OTP channels are mocked")
            assertEquals(4, caps.otp.emailCodeLength)
            val login = caps.methods.filter { it.enabled && it.canLogin }.map { it.id }.toSet()
            assertTrue(login.containsAll(setOf(AuthMethodId.Email, AuthMethodId.Phone, AuthMethodId.Password)), "login methods $login")

            val tokens = signInByEmail(d, Stand.email()).let { d.storedTokens()!! }
            assertEquals(5, accessLifetimeSeconds(tokens.accessToken), "access token lifetime on the stand")
            assertEquals(AuthContract.VERSION, Stand.installedAuthVersion(), "stand runs the pinned contract's stapel-auth")
        }
    }

    @Test
    @Order(1)
    fun `method chooser renders exactly the completable server methods in server order`(): Unit = runBlocking {
        Device("chooser").use { d ->
            val p = MethodChooserPresenter(d.auth, d.context())
            val ready = p.state.await { it is MethodChooserState.Ready } as MethodChooserState.Ready
            assertEquals(listOf(AuthMethodId.Email, AuthMethodId.Phone, AuthMethodId.Password), ready.zones.all.map { it.id })
            assertEquals(listOf(AuthMethodId.Email, AuthMethodId.Phone), ready.zones.main.map { it.id })
            assertTrue(ready.zones.guestAvailable)
            assertFalse(ready.zones.passwordRegistration, "public posture keeps password registration closed")
        }
    }

    @Test
    @Order(2)
    fun `D-1 e-mail OTP registration - mock code, masked target, session stored, me answers the same id`(): Unit = runBlocking {
        Device("d1").use { d ->
            val email = Stand.email()
            val p = OtpSignInPresenter(d.auth, d.context())
            p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, email))
            val sent = p.state.await { it is OtpSignInState.CodeSent || it is OtpSignInState.RequestFailed }
            assertTrue(sent is OtpSignInState.CodeSent, "got $sent")
            assertTrue(sent.isMock)
            assertEquals(4, sent.codeLength)
            assertTrue('*' in sent.maskedTarget && sent.maskedTarget != email, "masked: ${sent.maskedTarget}")
            assertFalse(sent.canResend, "the resend timer runs right after a send")

            p.dispatch(OtpSignInIntent.SubmitCode(Stand.MOCK_CODE))
            val done = p.state.await { it is OtpSignInState.Done } as OtpSignInState.Done
            assertEquals(SignInKind.Registered, done.outcome.kind)
            assertEquals(email, done.outcome.user.email)
            assertTrue(d.auth.session.state.await { it is AuthState.SignedIn } is AuthState.SignedIn)
            val stored = d.storedTokens()
            assertNotNull(stored)
            val me = (d.auth.session.refreshUser() as StapelResult.Ok).value
            assertEquals(done.outcome.user.id, me.id)
        }
    }

    @Test
    @Order(3)
    fun `D-2 wrong code is a typed error with attempts remaining, then the right code succeeds`(): Unit = runBlocking {
        Device("d2").use { d ->
            val p = OtpSignInPresenter(d.auth, d.context())
            p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, Stand.email()))
            p.state.await { it is OtpSignInState.CodeSent }
            p.dispatch(OtpSignInIntent.SubmitCode("9999"))
            val wrong = p.state.await { it is OtpSignInState.CodeSent && !it.verifying && it.error != null } as OtpSignInState.CodeSent
            assertEquals(AuthErrorReason.InvalidCode, wrong.error!!.reason)
            val remaining = assertNotNull(wrong.error!!.attemptsRemaining, "attempts_remaining is an integer param")
            assertTrue(d.sessions.state.value is SessionState.Unauthenticated, "no session after a wrong code")
            assertNull(d.storedTokens())

            p.dispatch(OtpSignInIntent.SubmitCode("9998"))
            val second = p.state.await { it is OtpSignInState.CodeSent && !it.verifying && it.error?.attemptsRemaining != remaining }
            assertTrue((second as OtpSignInState.CodeSent).error!!.attemptsRemaining!! < remaining, "attempts go down")

            p.dispatch(OtpSignInIntent.SubmitCode(Stand.MOCK_CODE))
            assertTrue(p.state.await { it is OtpSignInState.Done } is OtpSignInState.Done)
        }
    }

    @Test
    @Order(4)
    fun `D-3 phone OTP - first run registers, second device signs in to the same account`(): Unit = runBlocking {
        val phone = Stand.phone()
        val first = Device("d3a").use { d -> signInByPhone(d, phone) }
        Device("d3b").use { d ->
            val second = signInByPhone(d, phone)
            assertEquals(SignInKind.LoggedIn, second.kind)
            assertEquals(first.user.id, second.user.id)
        }
    }

    @Test
    @Order(5)
    fun `D-4 resend is ignored while the cooldown runs - no request leaves`(): Unit = runBlocking {
        Device("d4").use { d ->
            val p = OtpSignInPresenter(d.auth, d.context())
            p.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, Stand.email()))
            p.state.await { it is OtpSignInState.CodeSent }
            val before = d.log.count(EMAIL_REQUEST)
            p.dispatch(OtpSignInIntent.Resend)
            p.dispatch(OtpSignInIntent.SubmitCode("1"))
            p.state.await { it is OtpSignInState.CodeSent && it.error != null }
            assertEquals(before, d.log.count(EMAIL_REQUEST), "resend during cooldown sent nothing")
        }
    }

    @Test
    @Order(6)
    fun `D-5 password sign-in after a password was set by OTP, wrong password is typed and writes nothing`(): Unit = runBlocking {
        val email = Stand.email()
        val password = "e2e-" + java.util.UUID.randomUUID()
        Device("d5-setup").use { d ->
            signInByEmail(d, email)
            Thread.sleep(2_500) // the stand's per-identifier resend cooldown also covers this code
            assertTrue(d.api.passwordChangeOtpRequest(PasswordOtpRequest(PasswordOtpMethod.Email)) is StapelResult.Ok)
            val set = d.api.passwordChangeOtpVerify(PasswordOtpVerify(PasswordOtpMethod.Email, Stand.MOCK_CODE, password))
            assertTrue(set is StapelResult.Ok, "password set through /password/change/otp/: $set")
        }
        Device("d5").use { d ->
            val p = PasswordSignInPresenter(d.auth, d.context())
            p.dispatch(PasswordSignInIntent.SignIn(email, "definitely-wrong-1"))
            val failed = p.state.await { it is PasswordSignInState.Failed } as PasswordSignInState.Failed
            assertEquals(AuthErrorReason.InvalidCredentials, failed.error.reason)
            assertNull(d.storedTokens(), "a refused sign-in writes no tokens")

            p.dispatch(PasswordSignInIntent.SignIn(email, password))
            val done = p.state.await { it is PasswordSignInState.Done } as PasswordSignInState.Done
            assertEquals(SignInKind.LoggedIn, done.outcome.kind)
            assertEquals(email, done.outcome.user.email)
            assertNotNull(d.storedTokens())
        }
    }

    companion object {
        const val EMAIL_REQUEST = "auth_api_v1_email_request_create"
    }
}
