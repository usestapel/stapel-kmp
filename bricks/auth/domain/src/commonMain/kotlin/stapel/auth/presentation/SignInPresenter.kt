package stapel.auth.presentation

import stapel.auth.StapelAuth
import stapel.auth.error.AuthError
import stapel.auth.model.SignInOutcome
import stapel.auth.model.TotpProof
import stapel.auth.session.Completion
import stapel.mvi.Presenter
import stapel.mvi.PresenterContext

/** Intents of the challenge tail, accepted by every sign-in presenter. */
public sealed interface ChallengeIntent : OtpSignInIntent, PasswordSignInIntent, LoginChallengeIntent {
    /** Answers a [ChallengeStep.Totp]. */
    public data class SubmitTotp(
        /** The code or backup code. */
        val proof: TotpProof,
    ) : ChallengeIntent

    /** Answers a [ChallengeStep.ForcedPasswordChange]. */
    public data class SubmitNewPassword(
        /** The new password. */
        val newPassword: String,
    ) : ChallengeIntent
}

/**
 * Shared machinery of the sign-in presenters: turning a login answer into `Done` / `Challenge` /
 * a refusal, and running the challenge tail. Not a public extension point.
 */
public abstract class SignInPresenter<S : Any, I : Any> internal constructor(
    initial: S,
    context: PresenterContext,
    internal val auth: StapelAuth,
) : Presenter<S, I, AuthEffect>(initial, context) {
    private val tail = ChallengeTail(auth)

    internal abstract fun challengeState(step: ChallengeStep): S

    internal abstract fun stepOf(state: S): ChallengeStep?

    internal abstract fun doneState(outcome: SignInOutcome.Session): S

    internal fun settle(completion: Completion, refused: (AuthError) -> S) {
        when (completion) {
            is Completion.Refused -> reduce { refused(completion.error) }
            is Completion.Done -> when (val outcome = completion.outcome) {
                is SignInOutcome.Session -> finish(outcome)
                else -> reduce { challengeState(outcome.toStepOrNull()!!) }
            }
        }
    }

    internal fun finish(outcome: SignInOutcome.Session) {
        reduce { doneState(outcome) }
        emit(AuthEffect.SignedIn(outcome))
    }

    internal fun handleChallenge(intent: ChallengeIntent) {
        val step = stepOf(state.value) ?: return
        when (intent) {
            is ChallengeIntent.SubmitTotp -> {
                if (step !is ChallengeStep.Totp || step.submitting) return
                reduce { challengeState(step.submitting()) }
                launch(KEY_CHALLENGE) { apply(tail.submitTotp(step, intent.proof)) }
            }
            is ChallengeIntent.SubmitNewPassword -> {
                if (step !is ChallengeStep.ForcedPasswordChange || step.submitting) return
                reduce { challengeState(step.submitting()) }
                launch(KEY_CHALLENGE) { apply(tail.submitNewPassword(step, intent.newPassword)) }
            }
        }
    }

    private fun apply(result: TailResult) {
        when (result) {
            is TailResult.Session -> finish(result.outcome)
            is TailResult.Next -> reduce { challengeState(result.step) }
            is TailResult.Error -> reduce { challengeState(result.step.failed(result.error)) }
        }
    }

    internal companion object {
        const val KEY_CHALLENGE: String = "challenge"
        const val KEY_WORK: String = "work"
    }
}
