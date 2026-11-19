package stapel.auth.ui

import stapel.auth.error.AuthError
import stapel.auth.model.AuthMethodId
import stapel.auth.model.LoginChallenge
import stapel.auth.model.OtpChannel
import stapel.auth.presentation.ChallengeStep
import stapel.auth.presentation.OtpSignInState
import stapel.auth.presentation.PasswordRegisterState
import stapel.auth.session.SignOutReason
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.result.ServerRefusal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class BindingMappingTest {
    private val now = Instant.parse("2026-11-19T12:00:00Z")
    private fun err(code: String, status: Int, vararg p: Pair<String, String>) =
        AuthError.of(ServerRefusal(ErrorEnvelope(ErrorCode(code), status, ErrorParams(p.toMap()))))

    @Test
    fun onlyCompletableMethodsMapToScreens() {
        assertEquals(SignInMethod.Email, AuthMethodId.Email.toSignInMethod())
        assertEquals(SignInMethod.Password, AuthMethodId.Password.toSignInMethod())
        for (id in listOf(AuthMethodId.Passkey, AuthMethodId.Qr, AuthMethodId.OAuth, AuthMethodId.Sso, AuthMethodId.MagicLink)) {
            assertNull(id.toSignInMethod(), "$id must never render")
        }
    }

    @Test
    fun otpStatesMapToSteps() {
        val sent = OtpSignInState.CodeSent(OtpChannel.Phone, "+12025550100", "+1 *** 0100", 4, now + 10.minutes, 30.seconds, true)
        val code = assertIs<SignInStep.Code>(sent.toStep(SignInMethod.Phone, now)).state
        assertEquals(OtpCodeState(SignInMethod.Phone, "+1 *** 0100", 4, 30.seconds, isMock = true), code)
        val locked = OtpSignInState.Locked(OtpChannel.Email, "a@b.c", now + 5.minutes, err("error.423.account_locked", 423, "retry_after_minutes" to "5"))
        assertEquals(5.minutes, assertIs<SignInStep.Locked>(locked.toStep(SignInMethod.Email, now)).state.retryIn)
        val failed = OtpSignInState.RequestFailed(OtpChannel.Email, "x", err("error.400.field.invalid", 400, "field" to "email"))
        val form = assertIs<SignInStep.Form>(failed.toStep(SignInMethod.Email, now))
        assertEquals(setOf("email"), form.fieldErrors.keys)
        assertEquals("x", form.identifier)
        assertEquals(SignInStep.Form(identifier = ""), OtpSignInState.Idle(OtpChannel.Phone, "+1").toStep(SignInMethod.Email, now))
    }

    @Test
    fun challengeTailMapsToTheSharedSteps() {
        val c = LoginChallenge("t", now + 5.minutes)
        assertIs<SignInStep.Totp>(OtpSignInState.Challenge(ChallengeStep.Totp(c)).toStep(SignInMethod.Email, now))
        assertIs<SignInStep.ForcedPasswordChange>(ChallengeStep.ForcedPasswordChange(c, submitting = true).toStep())
        assertEquals(SignInStep.MfaEnrollRequired, ChallengeStep.MfaEnrollRequired(c).toStep())
    }

    @Test
    fun registrationFieldErrorsGoToTheirField() {
        val step = assertIs<SignInStep.Form>(PasswordRegisterState.Failed(err("error.400.field.min_length", 400, "field" to "password", "min_length" to "8")).toStep())
        assertNull(step.error)
        assertEquals(setOf("password"), step.fieldErrors.keys)
    }

    @Test
    fun signOutReasonsNameTheNotice() {
        assertEquals(AuthNotice.SignedOutElsewhere, SignOutReason.Revoked.toNotice())
        assertEquals(AuthNotice.SessionExpired, SignOutReason.Expired.toNotice())
        assertEquals(AuthNotice.SignedOut, SignOutReason.UserRequested.toNotice())
        assertEquals(AuthNotice.AccountDisabled, SignOutReason.AccountDisabled.toNotice())
    }
}
