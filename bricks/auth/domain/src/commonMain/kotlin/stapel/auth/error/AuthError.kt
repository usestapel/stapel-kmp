package stapel.auth.error

import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.result.Remediation
import stapel.result.StapelError
import stapel.result.TransportCarrier
import stapel.result.TransportFailure
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * What went wrong in an auth operation, classified for the flows and screens. [reason] is the typed
 * part flows branch on; [cause] keeps the original error (its code, params and the server's text) for
 * the error-text resolver. Text is never produced in this library.
 */
public data class AuthError(
    /** Classification. */
    val reason: AuthErrorReason,
    /** The underlying error; null only for errors the client raises itself ([AuthErrorReason.Unsupported]). */
    val cause: StapelError?,
) : StapelError {
    /** The cause's code, or `stapel.auth.<reason>` for a client-raised error. */
    override val code: ErrorCode get() = cause?.code ?: ErrorCode("stapel.auth.${reason.name.lowercase()}")

    /** HTTP status, null for transport failures and client-raised errors. */
    override val status: Int? get() = cause?.status

    /** The server's params (`attempts_remaining`, `retry_after_minutes`, `field`, …). */
    override val params: ErrorParams get() = cause?.params ?: ErrorParams.Empty

    /** What the user can do. */
    override val remediation: Remediation get() = cause?.remediation ?: Remediation.Unknown

    /** The server's envelope, when there was one. */
    override val envelope: ErrorEnvelope? get() = cause?.envelope ?: (cause as? TransportCarrier)?.failure?.envelope

    /** `attempts_remaining` of a wrong-code answer, when the server sent it. */
    val attemptsRemaining: Int? get() = params.int("attempts_remaining")

    /** `retry_after_minutes` of a lockout or rate limit, when the server sent it. */
    val retryAfter: Duration?
        get() = params.int("retry_after_minutes")?.minutes
            ?: (((cause as? TransportCarrier)?.failure ?: cause) as? TransportFailure.RateLimited)?.retryAfter

    /** The field a validation error names, when the server sent one. */
    val field: String? get() = params["field"]

    /** True when retrying the same action later may succeed without user input (network, server busy). */
    val isTransient: Boolean get() = reason == AuthErrorReason.Network || reason == AuthErrorReason.Unavailable

    /** Builders. */
    public companion object {
        /** Classifies [error] (any error a generated auth operation returns). */
        public fun of(error: StapelError): AuthError = AuthError(classify(error), error)
    }
}

/** Typed classification of an [AuthError]. */
public enum class AuthErrorReason {
    /** Wrong one-time code (`invalid_code`, `invalid_code_attempts`; see [AuthError.attemptsRemaining]). */
    InvalidCode,

    /** The code expired or no code was requested for the identifier (`code_expired`). */
    CodeExpired,

    /** Too many wrong codes: blocked for a while (`error.422.blocked`). */
    Blocked,

    /** Too many failed attempts on the account (`error.423.account_locked`). */
    AccountLocked,

    /** Too many requests (`error.429.*`); see [AuthError.retryAfter]. */
    RateLimited,

    /** Wrong login or password (`error.401.invalid_credentials`). */
    InvalidCredentials,

    /** The account must use SSO (`error.403.sso_required`). */
    SsoRequired,

    /** Sign-up is closed (`error.403.registration_closed`). */
    RegistrationClosed,

    /** The e-mail, phone or username is taken (`error.409.*_taken`, `not_available`). */
    AlreadyTaken,

    /** Input the server refused (`error.400.*` field and format errors); see [AuthError.field]. */
    InvalidInput,

    /** The operation needs a captcha token and none could be supplied (`captcha_required`). */
    CaptchaRequired,

    /** The captcha token was refused (`captcha_invalid`). */
    CaptchaInvalid,

    /** A login challenge expired or is invalid; the sign-in must start over. */
    ChallengeExpired,

    /** A first-login password change is required (`error.403.password_change_required`). */
    PasswordChangeRequired,

    /** The account is disabled (`error.401.account_disabled`). */
    AccountDisabled,

    /** OTP sign-in to an admin account in mock mode (`error.403.mock_otp_admin`). */
    MockOtpAdmin,

    /** No answer: offline, timeout, connection failed. Retryable. */
    Network,

    /** The server is busy or failing (5xx, 503). Retryable. */
    Unavailable,

    /** A session is required and there is none (or it ended). */
    Unauthenticated,

    /** The user cancelled a step (step-up). */
    Cancelled,

    /** The client cannot complete this (no captcha solver installed, method not supported). */
    Unsupported,

    /** Anything else; [AuthError.cause] carries the code. */
    Other,
}

private val takenCodes = setOf(
    "error.409.email_taken", "error.409.phone_taken", "error.409.username_taken", "error.400.not_available",
    "error.409.email_reserved", "error.409.phone_reserved",
)

private fun classify(error: StapelError): AuthErrorReason {
    val failure = (error as? TransportCarrier)?.failure ?: (error as? TransportFailure)
    val code = error.code.value
    return when {
        code == "error.400.invalid_code" || code == "error.400.invalid_code_attempts" -> AuthErrorReason.InvalidCode
        code == "error.400.code_expired" -> AuthErrorReason.CodeExpired
        code == "error.422.blocked" -> AuthErrorReason.Blocked
        code == "error.423.account_locked" -> AuthErrorReason.AccountLocked
        code.startsWith("error.429.") -> AuthErrorReason.RateLimited
        code == "error.401.invalid_credentials" -> AuthErrorReason.InvalidCredentials
        code == "error.403.sso_required" -> AuthErrorReason.SsoRequired
        code == "error.403.registration_closed" -> AuthErrorReason.RegistrationClosed
        code in takenCodes -> AuthErrorReason.AlreadyTaken
        code == "error.400.captcha_required" -> AuthErrorReason.CaptchaRequired
        code == "error.400.captcha_invalid" -> AuthErrorReason.CaptchaInvalid
        code == "error.400.first_login_challenge_invalid" || code == "error.401.token_invalid" -> AuthErrorReason.ChallengeExpired
        code == "error.403.password_change_required" -> AuthErrorReason.PasswordChangeRequired
        code == "error.401.account_disabled" -> AuthErrorReason.AccountDisabled
        code == "error.403.mock_otp_admin" -> AuthErrorReason.MockOtpAdmin
        failure is TransportFailure.NoResponse -> AuthErrorReason.Network
        failure is TransportFailure.RateLimited -> AuthErrorReason.RateLimited
        failure is TransportFailure.Unavailable || failure is TransportFailure.ServerFault -> AuthErrorReason.Unavailable
        failure is TransportFailure.Unauthenticated -> AuthErrorReason.Unauthenticated
        code.startsWith("error.400.") -> AuthErrorReason.InvalidInput
        else -> AuthErrorReason.Other
    }
}
