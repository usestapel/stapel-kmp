package stapel.auth.presentation

import stapel.auth.StapelAuth
import stapel.auth.api.model.ForcedPasswordChange
import stapel.auth.api.model.TOTPChallengeVerify
import stapel.auth.error.AuthError
import stapel.auth.error.AuthErrorReason
import stapel.auth.model.FirstLoginStep
import stapel.auth.model.LoginChallenge
import stapel.auth.model.SignInOutcome
import stapel.auth.model.TotpProof
import stapel.auth.session.Completion
import stapel.result.StapelResult

/**
 * The tail every session-minting flow shares: a TOTP second factor or a first-login step. One
 * sub-state instead of a copy per flow.
 */
public sealed interface ChallengeStep {
    /** The challenge being answered. */
    public val challenge: LoginChallenge

    /** A TOTP (or backup) code is required. */
    public data class Totp(
        override val challenge: LoginChallenge,
        /** True while the proof is checked. */
        val submitting: Boolean = false,
        /** The previous attempt's error. */
        val error: AuthError? = null,
    ) : ChallengeStep

    /** The account must set a new password before it can be used. */
    public data class ForcedPasswordChange(
        override val challenge: LoginChallenge,
        /** True while the new password is submitted. */
        val submitting: Boolean = false,
        /** The previous attempt's error (password policy, expired challenge). */
        val error: AuthError? = null,
    ) : ChallengeStep

    /**
     * Two-factor enrolment is required. Enrolment is a later slice: the state tells the user to finish
     * it on the web; nothing can be submitted from here.
     */
    public data class MfaEnrollRequired(override val challenge: LoginChallenge) : ChallengeStep
}

/** Effects every sign-in presenter emits. */
public sealed interface AuthEffect {
    /** A session was adopted; the UI navigates on. */
    public data class SignedIn(
        /** The session. */
        val outcome: SignInOutcome.Session,
    ) : AuthEffect
}

/** Where a challenge-tail step led. */
internal sealed interface TailResult {
    data class Next(val step: ChallengeStep) : TailResult

    data class Session(val outcome: SignInOutcome.Session) : TailResult

    data class Error(val step: ChallengeStep, val error: AuthError) : TailResult
}

internal fun SignInOutcome.toStepOrNull(): ChallengeStep? = when (this) {
    is SignInOutcome.Session -> null
    is SignInOutcome.TotpRequired -> ChallengeStep.Totp(challenge)
    is SignInOutcome.FirstLoginRequired -> when (requires) {
        FirstLoginStep.PasswordChange -> ChallengeStep.ForcedPasswordChange(challenge)
        FirstLoginStep.MfaEnroll, FirstLoginStep.Unknown -> ChallengeStep.MfaEnrollRequired(challenge)
    }
}

internal fun Completion.toTail(current: ChallengeStep): TailResult = when (this) {
    is Completion.Refused -> TailResult.Error(current, error)
    is Completion.Done -> when (val outcome = outcome) {
        is SignInOutcome.Session -> TailResult.Session(outcome)
        else -> TailResult.Next(outcome.toStepOrNull()!!)
    }
}

/** Calls of the challenge tail, shared by every sign-in presenter. */
internal class ChallengeTail(private val auth: StapelAuth) {
    suspend fun submitTotp(step: ChallengeStep.Totp, proof: TotpProof): TailResult {
        if (expired(step)) return TailResult.Error(step, AuthError(AuthErrorReason.ChallengeExpired, null))
        val body = when (proof) {
            is TotpProof.Code -> TOTPChallengeVerify(challengeToken = step.challenge.token, code = proof.value)
            is TotpProof.BackupCode -> TOTPChallengeVerify(challengeToken = step.challenge.token, backupCode = proof.value)
        }
        return when (val r = auth.api.totpChallengeVerify(body)) {
            is StapelResult.Ok -> auth.sessionImpl.complete(r.value).toTail(step)
            is StapelResult.Err -> TailResult.Error(step, AuthError.of(r.error))
        }
    }

    suspend fun submitNewPassword(step: ChallengeStep.ForcedPasswordChange, newPassword: String): TailResult {
        if (expired(step)) return TailResult.Error(step, AuthError(AuthErrorReason.ChallengeExpired, null))
        return when (val r = auth.api.passwordForcedChange(ForcedPasswordChange(step.challenge.token, newPassword))) {
            is StapelResult.Ok -> auth.sessionImpl.complete(r.value).toTail(step)
            is StapelResult.Err -> TailResult.Error(step, AuthError.of(r.error))
        }
    }

    private fun expired(step: ChallengeStep): Boolean = auth.config.clock.now() >= step.challenge.expiresAt
}

internal fun ChallengeStep.submitting(): ChallengeStep = when (this) {
    is ChallengeStep.Totp -> copy(submitting = true, error = null)
    is ChallengeStep.ForcedPasswordChange -> copy(submitting = true, error = null)
    is ChallengeStep.MfaEnrollRequired -> this
}

internal fun ChallengeStep.failed(error: AuthError): ChallengeStep = when (this) {
    is ChallengeStep.Totp -> copy(submitting = false, error = error)
    is ChallengeStep.ForcedPasswordChange -> copy(submitting = false, error = error)
    is ChallengeStep.MfaEnrollRequired -> this
}
