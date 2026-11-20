package stapel.profiles.error

import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.result.Remediation
import stapel.result.StapelError
import stapel.result.TransportCarrier
import stapel.result.TransportFailure
import stapel.result.retryAfter
import kotlin.time.Duration

/**
 * What went wrong in a profiles operation, classified for the presenters and screens. [reason] is the
 * typed part a flow branches on; [cause] keeps the original error (its code, params and the server's
 * text) for the error-text resolver. Text is never produced in this library.
 */
public data class ProfilesError(
    /** Classification. */
    val reason: ProfilesErrorReason,
    /** The underlying error; null only for errors the client raises itself. */
    val cause: StapelError?,
) : StapelError {
    /** The cause's code, or `stapel.profiles.<reason>` for a client-raised error. */
    override val code: ErrorCode get() = cause?.code ?: ErrorCode("stapel.profiles.${reason.name.lowercase()}")

    /** HTTP status, null for transport failures and client-raised errors. */
    override val status: Int? get() = cause?.status

    /** The server's params (`field`, `attempts_remaining`, `retry_after`, …). */
    override val params: ErrorParams get() = cause?.params ?: ErrorParams.Empty

    /** What the user can do. */
    override val remediation: Remediation get() = cause?.remediation ?: Remediation.Unknown

    /** The server's envelope, when there was one. */
    override val envelope: ErrorEnvelope? get() = cause?.envelope ?: (cause as? TransportCarrier)?.failure?.envelope

    /** The field a validation error names, when the server sent one. */
    val field: String? get() = params["field"]

    /** `attempts_remaining` of a wrong contact code, when the server sent it. */
    val attemptsRemaining: Int? get() = params.int("attempts_remaining")

    /** How long a rate limit lasts, when the server said (`retry_after`, `retry_after_minutes`, header). */
    val retryAfter: Duration? get() = cause?.retryAfter

    /** True when retrying the same action later may succeed without user input. */
    val isTransient: Boolean get() = reason == ProfilesErrorReason.Network || reason == ProfilesErrorReason.Unavailable

    /** Builders. */
    public companion object {
        /** Classifies [error] (any error a generated profiles operation or the avatar uploader returns). */
        public fun of(error: StapelError): ProfilesError = error as? ProfilesError ?: ProfilesError(classify(error), error)

        /** A client-raised error of [reason] with no server cause. */
        public fun client(reason: ProfilesErrorReason): ProfilesError = ProfilesError(reason, null)
    }
}

/** Typed classification of a [ProfilesError]. */
public enum class ProfilesErrorReason {
    /** Input the server refused (`error.400.*` field, display-name and format errors); see [ProfilesError.field]. */
    InvalidInput,

    /** The avatar reference or file was refused (`error.400.avatar_*`, `invalid_avatar_format`, an upload refusal). */
    InvalidAvatar,

    /** The person does not exist or has no profile (`error.404.profile_not_found`, `not_found`). */
    NotFound,

    /** Following or blocking oneself (`cannot_follow_self`, `cannot_block_self`). */
    Self,

    /** The phone number is already listed (`error.409.contacts_duplicate`). */
    Duplicate,

    /** Wrong or expired contact verification code (`contacts_invalid_code`, `contacts_code_expired`). */
    InvalidCode,

    /** Contacts need a registered account (`error.403.contacts_registration_required`). */
    RegistrationRequired,

    /** Too many requests (`error.429.*`); see [ProfilesError.retryAfter]. */
    RateLimited,

    /** A session is required and there is none (or it ended). */
    Unauthenticated,

    /** Not allowed (`error.403.*` other than the cases above). */
    Forbidden,

    /** No answer: offline, timeout, connection failed. Retryable. */
    Network,

    /** The server is busy or failing (5xx, 503). Retryable. */
    Unavailable,

    /** The client cannot do this: no avatar uploader is installed. */
    Unsupported,

    /** Anything else; [ProfilesError.cause] carries the code. */
    Other,
}

private fun classify(error: StapelError): ProfilesErrorReason {
    val failure = (error as? TransportCarrier)?.failure ?: (error as? TransportFailure)
    val code = error.code.value
    return when {
        code.startsWith("error.400.avatar_") || code == "error.400.invalid_avatar_format" -> ProfilesErrorReason.InvalidAvatar
        code == "error.404.profile_not_found" || code == "error.404.not_found" -> ProfilesErrorReason.NotFound
        code == "error.400.cannot_follow_self" || code == "error.400.cannot_block_self" -> ProfilesErrorReason.Self
        code == "error.409.contacts_duplicate" -> ProfilesErrorReason.Duplicate
        code == "error.400.contacts_invalid_code" || code == "error.400.contacts_code_expired" -> ProfilesErrorReason.InvalidCode
        code == "error.403.contacts_registration_required" -> ProfilesErrorReason.RegistrationRequired
        code.startsWith("error.429.") -> ProfilesErrorReason.RateLimited
        failure is TransportFailure.NoResponse -> ProfilesErrorReason.Network
        failure is TransportFailure.RateLimited -> ProfilesErrorReason.RateLimited
        failure is TransportFailure.Unavailable || failure is TransportFailure.ServerFault -> ProfilesErrorReason.Unavailable
        failure is TransportFailure.Unauthenticated || code == "error.401.unauthorized" -> ProfilesErrorReason.Unauthenticated
        code.startsWith("error.403.") -> ProfilesErrorReason.Forbidden
        code.startsWith("error.400.") -> ProfilesErrorReason.InvalidInput
        else -> ProfilesErrorReason.Other
    }
}
