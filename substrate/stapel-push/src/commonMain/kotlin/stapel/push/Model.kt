package stapel.push

import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.result.Remediation
import stapel.result.StapelError
import kotlin.time.Instant

/** What the OS allows for notifications. */
public enum class PushPermission {
    /** The person was never asked. */
    NotDetermined,

    /** Notifications are allowed. */
    Granted,

    /** iOS provisional authorization: delivered quietly to the notification centre, no prompt was shown. */
    Provisional,

    /** The person refused, or switched notifications off in the system settings. */
    Denied,

    /** The platform or provider has no notifications (desktop, no configuration). */
    Unsupported,
}

/** True for the permissions under which a token is worth registering. */
public val PushPermission.allowsDelivery: Boolean
    get() = this == PushPermission.Granted || this == PushPermission.Provisional

/**
 * The kind of a push token, as the backend's device registry names it.
 *
 * @property wire the registry's word.
 */
public enum class TokenKind(public val wire: String) {
    /** A Firebase Cloud Messaging registration token (Android and iOS). */
    Fcm("fcm"),

    /** A raw APNs device token. */
    Apns("apns"),
}

/**
 * A push token. It is a bearer credential for this device's push channel: never logged, never
 * shown; [toString] is redacted and [fingerprint] is what may be compared or stored.
 *
 * @property value the token.
 * @property kind what minted it.
 * @property provider the id of the [PushProvider] that minted it.
 */
public class PushToken(
    public val value: String,
    public val kind: TokenKind,
    public val provider: String,
) {
    /** SHA-256 hex of [value]: the backend's `token_fingerprint`. */
    public val fingerprint: String by lazy { sha256Hex(value) }

    /** Equal when value, kind and provider are equal. */
    override fun equals(other: Any?): Boolean =
        other is PushToken && other.value == value && other.kind == kind && other.provider == provider

    /** Consistent with [equals]. */
    override fun hashCode(): Int = (value.hashCode() * 31 + kind.hashCode()) * 31 + provider.hashCode()

    /** Redacted: a token is never printed. */
    override fun toString(): String = "PushToken($provider, ${kind.wire}, ***)"
}

/**
 * A notification as the app sees it, whichever SDK delivered it.
 *
 * @property id the provider's message id, when it has one.
 * @property title the visible title.
 * @property body the visible body.
 * @property data the backend's `data` map; every value is a string.
 * @property provider the id of the provider that delivered it.
 * @property sentAt when it was sent, when the provider says.
 */
public data class PushMessage(
    public val id: String?,
    public val title: String?,
    public val body: String?,
    public val data: Map<String, String>,
    public val provider: String,
    public val sentAt: Instant? = null,
) {
    /** The `notification_type` field, or `unknown`: what the log markers name. */
    public val type: String get() = data["notification_type"] ?: "unknown"
}

/** Something happened to a notification. */
public sealed interface PushEvent {
    /** The notification this is about. */
    public val message: PushMessage

    /**
     * Arrived while the process is alive.
     *
     * @property foreground the app was on screen.
     */
    public data class Received(override val message: PushMessage, val foreground: Boolean) : PushEvent

    /**
     * The person tapped it — also the notification that launched the process, delivered once.
     *
     * @property actionId the notification action, null for the default tap.
     */
    public data class Opened(override val message: PushMessage, val actionId: String? = null) : PushEvent
}

/** Whether a provider can work here. */
public sealed interface ProviderAvailability {
    /** Configured and usable. */
    public data object Ready : ProviderAvailability

    /**
     * The project chose this provider but its configuration is absent (no `google-services.json`,
     * no `GoogleService-Info.plist`).
     *
     * @property reason what is missing.
     */
    public data class Unconfigured(val reason: String) : ProviderAvailability

    /**
     * This platform has no implementation (desktop) or lacks a prerequisite.
     *
     * @property reason why.
     */
    public data class Unsupported(val reason: String) : ProviderAvailability
}

/** Why a provider could not do what was asked. Client-synthesized: no response is involved. */
public sealed interface PushError : StapelError {
    /** Always null: no response was involved. */
    override val status: Int? get() = null

    /** Always empty. */
    override val params: ErrorParams get() = ErrorParams.Empty

    /** Always null: no response was involved. */
    override val envelope: ErrorEnvelope? get() = null

    /** The OS refuses notifications. */
    public data object PermissionDenied : PushError {
        /** See [PushErrorCodes]. */
        override val code: ErrorCode get() = PushErrorCodes.PermissionDenied
        /** What can be done about it. */
        override val remediation: Remediation get() = Remediation.FixInput
    }

    /**
     * The provider cannot work here.
     *
     * @property availability why.
     */
    public data class Unavailable(val availability: ProviderAvailability) : PushError {
        /** See [PushErrorCodes]. */
        override val code: ErrorCode get() = PushErrorCodes.Unavailable
        /** What can be done about it. */
        override val remediation: Remediation get() = Remediation.ContactSupport
    }

    /**
     * Not now: no APNs token yet, a simulator without APNs, no network. Retryable.
     *
     * @property detail a diagnostic for logs; never shown to a person and never a token.
     */
    public data class Transient(val detail: String?) : PushError {
        /** See [PushErrorCodes]. */
        override val code: ErrorCode get() = PushErrorCodes.Transient
        /** What can be done about it. */
        override val remediation: Remediation get() = Remediation.Retry
    }
}

/** The client-synthesized codes of [PushError]. */
public object PushErrorCodes {
    /** [PushError.PermissionDenied]. */
    public val PermissionDenied: ErrorCode = ErrorCode("stapel.push.permission_denied")

    /** [PushError.Unavailable]. */
    public val Unavailable: ErrorCode = ErrorCode("stapel.push.unavailable")

    /** [PushError.Transient]. */
    public val Transient: ErrorCode = ErrorCode("stapel.push.transient")
}

/** What the OS shows when a notification arrives while the app is on screen. */
public enum class ForegroundPresentation {
    /** The system banner, as in the background. */
    System,

    /** No banner: [PushEvent.Received] with `foreground = true` reaches the app, which shows its own message. */
    InApp,

    /** Nothing. */
    Silent,
}

/** Importance of an Android notification channel. */
public enum class Importance {
    /** No sound, no heads-up. */
    Low,

    /** Sound, no heads-up. */
    Default,

    /** Sound and heads-up. */
    High,
}

/**
 * One Android notification channel. No-op on other platforms.
 *
 * @property id the channel id; equal to the payload's routing group.
 * @property name the user-visible channel name, already localized.
 * @property importance its importance.
 */
public data class PushChannelSpec(
    public val id: String,
    public val name: String,
    public val importance: Importance = Importance.Default,
)
