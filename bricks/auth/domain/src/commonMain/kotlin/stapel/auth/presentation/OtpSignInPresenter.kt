package stapel.auth.presentation

import kotlinx.coroutines.delay
import stapel.auth.StapelAuth
import stapel.auth.api.model.EmailAuthRequest
import stapel.auth.api.model.EmailAuthVerify
import stapel.auth.api.model.PhoneAuthRequest
import stapel.auth.api.model.PhoneAuthVerify
import stapel.auth.error.AuthError
import stapel.auth.error.AuthErrorReason
import stapel.auth.model.OtpChannel
import stapel.auth.model.OtpParameters
import stapel.auth.model.SignInOutcome
import stapel.mvi.PresenterContext
import stapel.result.StapelResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** State of a one-time-code sign-in (`auth.passwordless_login`), e-mail or phone. */
public sealed interface OtpSignInState {
    /** Nothing requested yet. [identifier] keeps what the user typed when they went back to edit it. */
    public data class Idle(
        /** Channel last used. */
        val channel: OtpChannel = OtpChannel.Email,
        /** Identifier last typed. */
        val identifier: String = "",
    ) : OtpSignInState

    /** The code is being requested. */
    public data class Requesting(
        /** Channel. */
        val channel: OtpChannel,
        /** E-mail or phone. */
        val identifier: String,
    ) : OtpSignInState

    /** The code was sent; waiting for it. */
    public data class CodeSent(
        /** Channel. */
        val channel: OtpChannel,
        /** E-mail or phone as typed. */
        val identifier: String,
        /** Where the code went, masked by the server. */
        val maskedTarget: String,
        /** Digits expected (from capabilities). */
        val codeLength: Int,
        /** When the code stops being valid. */
        val expiresAt: Instant,
        /** Time until [OtpSignInIntent.Resend] is accepted; zero when it is. */
        val resendIn: Duration,
        /** True in prototype posture: the code is the server's fixed mock code. */
        val isMock: Boolean,
        /** True while a code is being checked. */
        val verifying: Boolean = false,
        /** The last wrong-code / expired-code error ([AuthError.attemptsRemaining] when sent). */
        val error: AuthError? = null,
    ) : OtpSignInState {
        /** Whether a resend is accepted now. */
        val canResend: Boolean get() = resendIn <= Duration.ZERO
    }

    /** The request was refused (input, captcha, network); a rate limit is [Locked]. */
    public data class RequestFailed(
        /** Channel. */
        val channel: OtpChannel,
        /** Identifier. */
        val identifier: String,
        /** Why. */
        val error: AuthError,
    ) : OtpSignInState

    /**
     * Too many attempts: blocked, locked or rate-limited ([AuthErrorReason.Blocked],
     * [AuthErrorReason.AccountLocked], [AuthErrorReason.RateLimited]) until [until] (null when the
     * server did not say); [error] carries [AuthError.retryAfter] and [AuthError.retryAfterDisplay].
     */
    public data class Locked(
        /** Channel. */
        val channel: OtpChannel,
        /** Identifier. */
        val identifier: String,
        /** End of the lock. */
        val until: Instant?,
        /** The refusal. */
        val error: AuthError,
    ) : OtpSignInState

    /** A second factor or first-login step is required. */
    public data class Challenge(
        /** The step. */
        val step: ChallengeStep,
    ) : OtpSignInState

    /** Signed in. */
    public data class Done(
        /** The session. */
        val outcome: SignInOutcome.Session,
    ) : OtpSignInState
}

/** Intents of [OtpSignInPresenter]. */
public sealed interface OtpSignInIntent {
    /** Requests a code for [identifier] over [channel]. */
    public data class RequestCode(
        /** Channel. */
        val channel: OtpChannel,
        /** E-mail address or phone number. */
        val identifier: String,
    ) : OtpSignInIntent

    /** Requests another code; ignored while the resend timer runs. */
    public data object Resend : OtpSignInIntent

    /** Submits [code]. */
    public data class SubmitCode(
        /** The digits. */
        val code: String,
    ) : OtpSignInIntent

    /**
     * Back to [OtpSignInState.Idle] keeping the typed identifier: from the code step, a refusal, a
     * lock, or a challenge (which is abandoned; the server lets its token expire).
     */
    public data object EditIdentifier : OtpSignInIntent
}

/**
 * Sign-in (or sign-up) by a one-time code sent by e-mail or SMS. Run while a guest session is
 * active, it is the guest's upgrade: the server promotes the guest in place or merges it into the
 * existing account, and the session publishes `SessionChange.Promoted` / `Merged`.
 *
 * Transitions: `Idle → Requesting → CodeSent | RequestFailed | Locked`;
 * `CodeSent(verifying) → Done | Challenge | CodeSent(error) | Locked`;
 * [OtpSignInIntent.EditIdentifier]: `CodeSent | RequestFailed | Locked | Challenge → Idle`.
 */
public class OtpSignInPresenter(auth: StapelAuth, context: PresenterContext) :
    SignInPresenter<OtpSignInState, OtpSignInIntent>(OtpSignInState.Idle(), context, auth) {

    override suspend fun handle(intent: OtpSignInIntent) {
        when (intent) {
            is OtpSignInIntent.RequestCode -> request(intent.channel, intent.identifier.trim())
            OtpSignInIntent.Resend -> (state.value as? OtpSignInState.CodeSent)?.takeIf { it.canResend && !it.verifying }?.let {
                request(it.channel, it.identifier)
            }
            is OtpSignInIntent.SubmitCode -> submit(intent.code.trim())
            OtpSignInIntent.EditIdentifier -> {
                val (channel, identifier) = when (val s = state.value) {
                    is OtpSignInState.CodeSent -> s.channel to s.identifier
                    is OtpSignInState.RequestFailed -> s.channel to s.identifier
                    is OtpSignInState.Locked -> s.channel to s.identifier
                    is OtpSignInState.Requesting -> s.channel to s.identifier
                    is OtpSignInState.Challenge -> lastRequest ?: (OtpChannel.Email to "")
                    else -> return
                }
                // A keyed launch cancels the previous one: in-flight work, a challenge step and the timer stop here.
                launch(KEY_WORK) {}
                launch(KEY_CHALLENGE) {}
                launch(KEY_TIMER) {}
                reduce { OtpSignInState.Idle(channel, identifier) }
            }
            is ChallengeIntent -> handleChallenge(intent)
        }
    }

    /** The channel and identifier of the last code request, for the way back from a challenge. */
    private var lastRequest: Pair<OtpChannel, String>? = null

    private fun request(channel: OtpChannel, identifier: String) {
        lastRequest = channel to identifier
        reduce { OtpSignInState.Requesting(channel, identifier) }
        launch(KEY_WORK) {
            val otp = auth.capabilities.get().let { (it as? StapelResult.Ok)?.value }
            val deviceId = auth.deviceIdSource()
            val answer = when (channel) {
                OtpChannel.Email -> auth.api.emailRequest(EmailAuthRequest(email = identifier, deviceId = deviceId))
                OtpChannel.Phone -> auth.api.phoneRequest(PhoneAuthRequest(phone = identifier, deviceId = deviceId))
            }
            when (answer) {
                is StapelResult.Ok -> {
                    val params = otp?.otp ?: FALLBACK_OTP
                    val now = auth.clock.now()
                    reduce {
                        OtpSignInState.CodeSent(
                            channel = channel,
                            identifier = identifier,
                            maskedTarget = answer.value.target,
                            codeLength = params.codeLength(channel),
                            expiresAt = now + params.ttl,
                            resendIn = params.resendCooldown,
                            isMock = otp?.isMock(channel) ?: false,
                        )
                    }
                    startTimer(now + params.resendCooldown)
                }
                is StapelResult.Err -> {
                    val error = AuthError.of(answer.error)
                    reduce { lockedOr(error, channel, identifier) { OtpSignInState.RequestFailed(channel, identifier, error) } }
                }
            }
        }
    }

    private fun submit(code: String) {
        val sent = state.value as? OtpSignInState.CodeSent ?: return
        if (sent.verifying) return
        reduce { sent.copy(verifying = true, error = null) }
        launch(KEY_WORK) {
            val answer = when (sent.channel) {
                OtpChannel.Email -> auth.api.emailVerify(
                    EmailAuthVerify(email = sent.identifier, code = code, attribution = auth.config.attribution()),
                )
                OtpChannel.Phone -> auth.api.phoneVerify(
                    PhoneAuthVerify(phone = sent.identifier, code = code, attribution = auth.config.attribution()),
                )
            }
            when (answer) {
                is StapelResult.Ok -> settle(auth.sessionImpl.complete(answer.value)) { error ->
                    sent.copy(verifying = false, error = error)
                }
                is StapelResult.Err -> {
                    val error = AuthError.of(answer.error)
                    reduce { lockedOr(error, sent.channel, sent.identifier) { sent.copy(verifying = false, error = error) } }
                }
            }
        }
    }

    private fun startTimer(resendAt: Instant) {
        launch(KEY_TIMER) {
            while (true) {
                val remaining = (resendAt - auth.clock.now()).coerceAtLeast(Duration.ZERO)
                reduce { (this as? OtpSignInState.CodeSent)?.copy(resendIn = remaining) ?: this }
                if (remaining == Duration.ZERO) break
                delay(remaining.coerceAtMost(1.seconds))
            }
        }
    }

    private fun lockedOr(error: AuthError, channel: OtpChannel, identifier: String, otherwise: () -> OtpSignInState): OtpSignInState =
        if (error.reason in LOCKING) {
            OtpSignInState.Locked(channel, identifier, error.retryAfter?.let { auth.clock.now() + it }, error)
        } else {
            otherwise()
        }

    override fun challengeState(step: ChallengeStep): OtpSignInState = OtpSignInState.Challenge(step)

    override fun stepOf(state: OtpSignInState): ChallengeStep? = (state as? OtpSignInState.Challenge)?.step

    override fun doneState(outcome: SignInOutcome.Session): OtpSignInState = OtpSignInState.Done(outcome)

    private companion object {
        const val KEY_TIMER = "resend-timer"

        /** Refusals that end in [OtpSignInState.Locked]: the user can only wait (or go back). */
        val LOCKING = setOf(AuthErrorReason.Blocked, AuthErrorReason.AccountLocked, AuthErrorReason.RateLimited)

        /** Used only when capabilities cannot be read; the server still enforces its own values. */
        val FALLBACK_OTP = OtpParameters(
            emailCodeLength = 6,
            phoneCodeLength = 6,
            totpCodeLength = 6,
            ttl = 300.seconds,
            resendCooldown = 60.seconds,
        )
    }
}
