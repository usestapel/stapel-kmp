package stapel.auth.model

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The signed-in (or guest) user as the auth library exposes it: a hand-written projection of the
 * generated `User` DTO, so a contract regeneration does not ripple into screens.
 */
public data class AuthUser(
    /** Stable user id (a UUID string). Changes only when a guest is merged into an existing account. */
    val id: String,
    /** Server-assigned username. */
    val username: String,
    /** E-mail address, when the account has one. */
    val email: String?,
    /** Phone number in E.164, when the account has one. */
    val phone: String?,
    /** How the account was created. */
    val authType: AuthType,
    /** True for a guest (`is_anonymous` on the wire). */
    val isGuest: Boolean,
    /** Whether [email] is verified. */
    val isEmailVerified: Boolean,
    /** Whether [phone] is verified. */
    val isPhoneVerified: Boolean,
    /** Staff flag (operator console access on the web). */
    val isStaff: Boolean,
    /** OAuth provider of an OAuth-created account. */
    val oauthProvider: String?,
    /** Avatar URL. */
    val avatarUrl: String?,
    /** When the account was created. */
    val createdAt: Instant,
    /** Last sign-in, if any. */
    val lastLogin: Instant?,
)

/** How an account was created; [Unknown] for a value this client version does not know. */
public enum class AuthType {
    /** E-mail OTP. */
    Email,

    /** Phone OTP. */
    Phone,

    /** Social sign-in. */
    OAuth,

    /** Enterprise SSO. */
    Sso,

    /** Guest. */
    Anonymous,

    /** Username / password. */
    Login,

    /** A value this client does not know. */
    Unknown,
}

/** How a sign-in ended (`AuthResponse.status`). */
public enum class SignInKind {
    /** An existing account signed in. */
    LoggedIn,

    /** A new account was created (or a guest was promoted in place). */
    Registered,

    /** A guest was folded into an existing account: the user id changed. */
    Merged,

    /** The account was modified (contact change flows). */
    Modified,

    /** The server refused the sign-in without an error code. */
    Rejected,

    /** A value this client does not know. */
    Other,
}

/** A second-step challenge returned instead of a session. */
public data class LoginChallenge(
    /** Opaque challenge token the next call presents. */
    val token: String,
    /** When the challenge stops being accepted. */
    val expiresAt: Instant,
)

/** What a first-login challenge requires. */
public enum class FirstLoginStep {
    /** The password must be changed before the account can be used. */
    PasswordChange,

    /** Two-factor enrolment is required (finished on the web until the enrolment slice exists). */
    MfaEnroll,

    /** A value this client does not know. */
    Unknown,
}

/** What a session-minting call produced. */
public sealed interface SignInOutcome {
    /** A session was issued and adopted. */
    public data class Session(
        /** The user now acting. */
        val user: AuthUser,
        /** How the sign-in ended. */
        val kind: SignInKind,
    ) : SignInOutcome

    /** A TOTP code is required to finish. */
    public data class TotpRequired(
        /** The challenge to answer. */
        val challenge: LoginChallenge,
    ) : SignInOutcome

    /** A first-login step is required to finish. */
    public data class FirstLoginRequired(
        /** What is required. */
        val requires: FirstLoginStep,
        /** The challenge to answer. */
        val challenge: LoginChallenge,
    ) : SignInOutcome
}

/** Channel of a one-time code. */
public enum class OtpChannel {
    /** Code sent by e-mail. */
    Email,

    /** Code sent by SMS. */
    Phone,
}

/** A proof answering a TOTP challenge. */
public sealed interface TotpProof {
    /** A code from the authenticator app. */
    public data class Code(
        /** The digits. */
        val value: String,
    ) : TotpProof

    /** One of the account's backup codes. */
    public data class BackupCode(
        /** The backup code. */
        val value: String,
    ) : TotpProof
}

/** Sign-in method ids of `capabilities.methods[]`. */
public enum class AuthMethodId {
    /** E-mail one-time code. */
    Email,

    /** Phone one-time code. */
    Phone,

    /** Username / e-mail / phone with a password. */
    Password,

    /** WebAuthn passkey. */
    Passkey,

    /** QR login. */
    Qr,

    /** E-mail magic link. */
    MagicLink,

    /** Social sign-in. */
    OAuth,

    /** Enterprise SSO. */
    Sso,

    /** A method this client does not know. */
    Unknown,
}

/** Where the server wants a method shown. */
public enum class MethodPlacement {
    /** A main tab / lone form. */
    Main,

    /** A button row under the form. */
    Bottom,

    /** Behind "more ways to sign in". */
    Overflow,

    /** A value this client does not know. */
    Unknown,
}

/** How a method is completed. */
public enum class MethodInteraction {
    /** In the form. */
    Inline,

    /** In a modal step. */
    Modal,

    /** Through a browser redirect. */
    Redirect,

    /** A value this client does not know. */
    Unknown,
}

/** One entry of `capabilities.methods[]`. */
public data class AuthMethod(
    /** Method id. */
    val id: AuthMethodId,
    /** The wire id as sent (also for [AuthMethodId.Unknown]). */
    val wireId: String,
    /** Whether the server enables it. */
    val enabled: Boolean,
    /** Where to show it. */
    val placement: MethodPlacement,
    /** Order within the placement. */
    val order: Int,
    /** How it completes. */
    val interaction: MethodInteraction,
    /** Server-supplied icon. */
    val iconSvg: String?,
    /** True when the channel is mocked (prototype posture: the code is the fixed mock code). */
    val mock: Boolean,
    /** Usable to sign in to an existing account. */
    val canLogin: Boolean,
    /** Usable to create an account. */
    val canRegister: Boolean,
)

/** Deployment stage of the backend. */
public enum class PostureStage {
    /** Prototype: OTP is mocked. */
    Prototype,

    /** Live. */
    Live,

    /** Not declared or unknown. */
    Unknown,
}

/** One-time code parameters published by the server. */
public data class OtpParameters(
    /** Digits of an e-mail code. */
    val emailCodeLength: Int,
    /** Digits of an SMS code. */
    val phoneCodeLength: Int,
    /** Digits of a TOTP code. */
    val totpCodeLength: Int,
    /** How long a code stays valid. */
    val ttl: Duration,
    /** Wait between two code requests. */
    val resendCooldown: Duration,
) {
    /** Code length of [channel]. */
    public fun codeLength(channel: OtpChannel): Int = when (channel) {
        OtpChannel.Email -> emailCodeLength
        OtpChannel.Phone -> phoneCodeLength
    }
}

/** What `GET /capabilities/` says the sign-in screen may offer. */
public data class AuthCapabilities(
    /** Methods in server order. */
    val methods: List<AuthMethod>,
    /** Whether password registration is open. */
    val passwordRegistration: Boolean,
    /** Whether guest sessions may be created. */
    val guestSessions: Boolean,
    /** Whether e-mail OTP is mocked. */
    val emailMock: Boolean,
    /** Whether phone OTP is mocked. */
    val phoneMock: Boolean,
    /** OTP parameters. */
    val otp: OtpParameters,
    /** Deployment stage. */
    val stage: PostureStage,
) {
    /** Whether [channel] is mocked. */
    public fun isMock(channel: OtpChannel): Boolean = when (channel) {
        OtpChannel.Email -> emailMock
        OtpChannel.Phone -> phoneMock
    }
}

/** Device class of a session. */
public enum class DeviceType {
    /** Phone. */
    Phone,

    /** Tablet. */
    Tablet,

    /** Desktop browser or app. */
    Desktop,

    /** API client. */
    Api,

    /** Not recognised. */
    Unknown,
}

/** One row of the signed-in user's sessions list. */
public data class SessionDevice(
    /** Session id (used to revoke it). */
    val id: String,
    /** Device class. */
    val type: DeviceType,
    /** Server-derived device label. */
    val name: String,
    /** Server-derived details (OS, browser). */
    val details: String,
    /** Last IP address. */
    val ipAddress: String?,
    /** When the session was created. */
    val createdAt: Instant?,
    /** Last use. */
    val lastUsedAt: Instant?,
    /** True for the session making this request. */
    val isCurrent: Boolean,
    /** Flagged as suspicious by the server. */
    val isSuspicious: Boolean,
)
