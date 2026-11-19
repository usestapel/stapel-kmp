package stapel.auth.ui

import androidx.compose.runtime.Immutable
import stapel.result.StapelError
import stapel.result.state.Resource
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The sign-in methods the v0 screens draw (web channel ids `email`, `phone`, `password`). Methods the
 * client cannot complete yet (OAuth, magic link, SSO, passkey, QR) have no member: a method that
 * cannot finish is never rendered.
 */
public enum class SignInMethod {
    /** One-time code by e-mail (registers on first use). */
    Email,

    /** One-time code by SMS (registers on first use). */
    Phone,

    /** Login + password. */
    Password,
}

/**
 * The server's method layout cut into zones (the web `computeZones`): [main] are tabs (or a lone
 * form), [bottom] full-width buttons under the form, [overflow] the "more ways" list.
 */
@Immutable
public data class MethodZones(
    /** Tabs, in server order; at most three. */
    val main: List<SignInMethod>,
    /** Buttons under the divider. */
    val bottom: List<SignInMethod> = emptyList(),
    /** Behind "More ways to sign in". */
    val overflow: List<SignInMethod> = emptyList(),
) {
    /** Every method of every zone. */
    val all: List<SignInMethod> get() = main + bottom + overflow
}

/** Which door of the sign-in screen is shown. */
public enum class SignInVariant {
    /** Sign in to an existing account (the code methods also register on first use). */
    Login,

    /** Create an account. */
    Register,
}

/** Why the sign-in screen is shown after a session ended; one sentence above the form. */
public enum class AuthNotice {
    /** The user signed out here. */
    SignedOut,

    /** The session could not be renewed (refresh token expired or missing). */
    SessionExpired,

    /** The session was revoked elsewhere (another device, an administrator). */
    SignedOutElsewhere,

    /** The account is disabled. */
    AccountDisabled,

    /** The account is closed. */
    AccountClosed,
}

/** The guest entry under the sign-in form; present only when the server allows guests. */
@Immutable
public data class GuestEntry(
    /** A guest is being created. */
    val creating: Boolean = false,
    /** The last attempt failed. */
    val error: StapelError? = null,
)

/**
 * Everything the sign-in screen shows. Built by the binding from the capabilities and the flow
 * presenters; the screen holds only what was typed.
 */
@Immutable
public data class SignInState(
    /** The methods, already filtered to what this app can complete. */
    val zones: MethodZones,
    /** The method whose form is shown; null = the first main method. */
    val selected: SignInMethod? = null,
    /** Login or register. */
    val variant: SignInVariant = SignInVariant.Login,
    /** The register door exists (some method can register); false hides the switch. */
    val canRegister: Boolean = false,
    /** Guest entry, when the server allows it; null hides it. */
    val guest: GuestEntry? = null,
    /** Why the user is here, if a session just ended. */
    val notice: AuthNotice? = null,
    /** Capabilities are still loading. */
    val loading: Boolean = false,
    /** Capabilities could not be loaded. */
    val loadError: StapelError? = null,
    /** Where the flow of the selected method stands. */
    val step: SignInStep = SignInStep.Form(),
) {
    /** The method actually shown. */
    val activeMethod: SignInMethod? get() = selected?.takeIf { it in zones.all } ?: zones.main.firstOrNull() ?: zones.all.firstOrNull()
}

/** Where a sign-in flow stands. Each step but [Form] and [Done] has its own screen in [AuthScreens]. */
public sealed interface SignInStep {
    /**
     * The method's form (identifier entry, password, or registration).
     *
     * @property busy a request is in flight.
     * @property error the last request failed (shown under the form).
     * @property fieldErrors server field errors by field name (`email`, `password`, …).
     * @property identifier what to prefill (kept when the user goes back from the code step).
     */
    @Immutable
    public data class Form(
        val busy: Boolean = false,
        val error: StapelError? = null,
        val fieldErrors: Map<String, StapelError> = emptyMap(),
        val identifier: String = "",
    ) : SignInStep

    /** The one-time code was sent; [state] is the code step. */
    @Immutable
    public data class Code(
        /** The code step. */
        val state: OtpCodeState,
    ) : SignInStep

    /** The account has two-step verification: a TOTP or backup code is needed ([state]). */
    @Immutable
    public data class Totp(
        /** The challenge. */
        val state: TotpChallengeState,
    ) : SignInStep

    /** The account must replace an organization-issued password ([state]). */
    @Immutable
    public data class ForcedPasswordChange(
        /** The change form. */
        val state: ForcedPasswordChangeState,
    ) : SignInStep

    /** The account must enrol a second factor first; done on the web until the enrol screens ship. */
    public data object MfaEnrollRequired : SignInStep

    /** Sign-in is refused for a while (blocked, locked, too many attempts): [state]. */
    @Immutable
    public data class Locked(
        /** The lockout. */
        val state: LockedState,
    ) : SignInStep

    /** Signed in; the host navigates away. */
    public data object Done : SignInStep
}

/**
 * The code step.
 *
 * @property method [SignInMethod.Email] or [SignInMethod.Phone].
 * @property maskedTarget where the code went, masked by the server.
 * @property codeLength digits in the code (capabilities).
 * @property resendIn time until resend is allowed; zero = allowed now.
 * @property isMock prototype posture: nothing was delivered (the hint says so; the code is never shown).
 * @property verifying a code is being checked.
 * @property error the last code was refused (carries `attempts_remaining`); a new error clears the field.
 */
@Immutable
public data class OtpCodeState(
    val method: SignInMethod,
    val maskedTarget: String,
    val codeLength: Int,
    val resendIn: Duration = Duration.ZERO,
    val isMock: Boolean = false,
    val verifying: Boolean = false,
    val error: StapelError? = null,
)

/**
 * The TOTP challenge.
 *
 * @property codeLength digits of an authenticator code (capabilities).
 * @property submitting a code is being checked.
 * @property error the last code was refused.
 */
@Immutable
public data class TotpChallengeState(
    val codeLength: Int = 6,
    val submitting: Boolean = false,
    val error: StapelError? = null,
)

/**
 * The forced password change.
 *
 * @property submitting the new password is being set.
 * @property error the server refused it (policy, expired challenge).
 */
@Immutable
public data class ForcedPasswordChangeState(
    val submitting: Boolean = false,
    val error: StapelError? = null,
)

/**
 * A refusal for a while.
 *
 * @property error what the server said (`error.422.blocked`, `error.423.account_locked`, `error.429.rate_limit`).
 * @property retryIn how long until another attempt may succeed; null when unknown.
 */
@Immutable
public data class LockedState(
    val error: StapelError,
    val retryIn: Duration? = null,
)

/** Events of the sign-in screen and its steps. */
public sealed interface SignInEvent {
    /** Show [method]'s form. */
    public data class SelectMethod(
        /** The method to show. */
        val method: SignInMethod,
    ) : SignInEvent

    /** Switch between sign-in and registration. */
    public data class SwitchVariant(
        /** The door to show. */
        val variant: SignInVariant,
    ) : SignInEvent

    /** Send a one-time code to [identifier] by [method]. */
    public data class RequestCode(
        /** E-mail or phone. */
        val method: SignInMethod,
        /** The address or number, trimmed. */
        val identifier: String,
    ) : SignInEvent

    /** Check [code]. */
    public data class SubmitCode(
        /** The digits typed. */
        val code: String,
    ) : SignInEvent

    /** Send the code again (only offered when the cooldown is over). */
    public data object Resend : SignInEvent

    /** Back from the code step to the identifier, keeping it. */
    public data object EditIdentifier : SignInEvent

    /** Sign in with [login] and [password]. */
    public data class SignInWithPassword(
        /** Username, e-mail or phone, trimmed. */
        val login: String,
        /** The password, verbatim. */
        val password: String,
    ) : SignInEvent

    /** Create an account with [email] and [password]. */
    public data class RegisterWithPassword(
        /** The e-mail address, trimmed. */
        val email: String,
        /** The chosen password (already repeated correctly). */
        val password: String,
    ) : SignInEvent

    /** Answer the TOTP challenge; [backup] = it is a backup code. */
    public data class SubmitTotp(
        /** The code typed. */
        val code: String,
        /** It is a backup code. */
        val backup: Boolean,
    ) : SignInEvent

    /** Set [password] as the account's own password. */
    public data class SubmitNewPassword(
        /** The new password (already repeated correctly). */
        val password: String,
    ) : SignInEvent

    /** Leave a challenge, a lockout or a dead end and return to the form. */
    public data object BackToForm : SignInEvent

    /** Continue as a guest. */
    public data object ContinueAsGuest : SignInEvent

    /** Hide the notice. */
    public data object DismissNotice : SignInEvent

    /** Reload the capabilities after a failed load. */
    public data object Retry : SignInEvent
}

/** The guest wall: a guest opened something that needs an account. */
@Immutable
public data class GuestUpgradeState(
    /** The "not now" door exists (the guest can go back to what they were doing). */
    val canDismiss: Boolean = true,
)

/** Events of the guest wall. */
public enum class GuestUpgradeEvent {
    /** Open sign-in (upgrades the guest in place or merges into the account). */
    SignIn,

    /** Open registration. */
    Register,

    /** Stay a guest. */
    NotNow,
}

/** Kind of device behind a session (the server's guess from the user agent). */
public enum class SessionDeviceKind {
    /** A phone. */
    Phone,

    /** A tablet. */
    Tablet,

    /** A desktop or laptop browser. */
    Desktop,

    /** An API client. */
    Api,

    /** Unknown. */
    Unknown,
}

/**
 * One row of the sessions list.
 *
 * @property id session id.
 * @property name device name (`"iPhone · Safari"`, `"Android app"`).
 * @property details browser / OS line.
 * @property ipAddress last address, when the server reports it.
 * @property lastUsedAt last activity.
 * @property isCurrent this device.
 * @property isSuspicious flagged as an unrecognized sign-in.
 * @property kind device kind.
 */
@Immutable
public data class SessionRowState(
    val id: String,
    val name: String,
    val details: String,
    val ipAddress: String?,
    val lastUsedAt: Instant,
    val isCurrent: Boolean,
    val isSuspicious: Boolean,
    val kind: SessionDeviceKind = SessionDeviceKind.Unknown,
)

/**
 * The sessions screen.
 *
 * @property sessions the list (loading, failed or ready).
 * @property now the instant "last used" is measured from.
 * @property busy session ids with a revoke or confirm in flight.
 * @property confirmRevoke the session whose revoke awaits confirmation.
 * @property confirmRevokeOthers "sign out everyone else" awaits confirmation.
 * @property revokingOthers "sign out everyone else" is in flight.
 * @property actionError the last revoke / confirm failed.
 */
@Immutable
public data class SessionsState(
    val sessions: Resource<List<SessionRowState>>,
    val now: Instant,
    val busy: Set<String> = emptySet(),
    val confirmRevoke: String? = null,
    val confirmRevokeOthers: Boolean = false,
    val revokingOthers: Boolean = false,
    val actionError: StapelError? = null,
)

/** Events of the sessions screen. */
public sealed interface SessionsEvent {
    /** Ask to revoke [id] (opens the confirmation). */
    public data class AskRevoke(
        /** Session id. */
        val id: String,
    ) : SessionsEvent

    /** Ask to sign out every other device (opens the confirmation). */
    public data object AskRevokeOthers : SessionsEvent

    /** Confirm the pending revoke. */
    public data object ConfirmRevoke : SessionsEvent

    /** Close the confirmation without revoking. */
    public data object CancelRevoke : SessionsEvent

    /** Mark [id] as recognized ("this was me"). */
    public data class ConfirmMine(
        /** Session id. */
        val id: String,
    ) : SessionsEvent

    /** Reload the list. */
    public data object Refresh : SessionsEvent

    /** Hide the action error. */
    public data object DismissError : SessionsEvent
}

/** The sign-out confirmation. */
@Immutable
public data class SignOutConfirmState(
    /** The session is a guest (signing out loses its data). */
    val isGuest: Boolean = false,
    /** Signing out is in progress. */
    val busy: Boolean = false,
)

/** Events of the sign-out confirmation. */
public enum class SignOutEvent {
    /** Sign out. */
    Confirm,

    /** Keep the session. */
    Cancel,
}
