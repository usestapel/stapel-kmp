package stapel.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import stapel.ui.skin.SkinExtensionKey

/**
 * The screen table: every auth screen as a stateless composable of its state and an event callback.
 * A project replaces one screen by delegation and keeps every flow, because states and events are
 * untouched:
 *
 * ```kotlin
 * object MyScreens : AuthScreens by DefaultAuthScreens {
 *     @Composable override fun OtpCode(state: OtpCodeState, onEvent: (SignInEvent) -> Unit) { MyCodeScreen(state, onEvent) }
 * }
 * authNavContribution(…, screens = MyScreens)
 * ```
 *
 * The sign-in screen draws its steps through [LocalAuthScreens], so replacing a step replaces it
 * inside the default sign-in screen too.
 */
@Stable
public interface AuthScreens {
    /** The sign-in screen: method chooser, the selected method's form, and the current step. */
    @Composable
    public fun SignIn(state: SignInState, onEvent: (SignInEvent) -> Unit, slots: AuthSlots)

    /** The one-time code step. */
    @Composable
    public fun OtpCode(state: OtpCodeState, onEvent: (SignInEvent) -> Unit)

    /** The TOTP challenge step. */
    @Composable
    public fun TotpChallenge(state: TotpChallengeState, onEvent: (SignInEvent) -> Unit)

    /** The forced password change step. */
    @Composable
    public fun ForcedPasswordChange(state: ForcedPasswordChangeState, onEvent: (SignInEvent) -> Unit)

    /** A lockout (blocked, locked, rate-limited). */
    @Composable
    public fun Locked(state: LockedState, onEvent: (SignInEvent) -> Unit)

    /** The guest wall. */
    @Composable
    public fun GuestUpgrade(state: GuestUpgradeState, onEvent: (GuestUpgradeEvent) -> Unit)

    /** The sessions and devices list. */
    @Composable
    public fun Sessions(state: SessionsState, onEvent: (SessionsEvent) -> Unit)

    /** The sign-out confirmation (a dialog). */
    @Composable
    public fun SignOutConfirm(state: SignOutConfirmState, onEvent: (SignOutEvent) -> Unit)
}

/** The default screens of this library. */
public object DefaultAuthScreens : AuthScreens {
    @Composable
    override fun SignIn(state: SignInState, onEvent: (SignInEvent) -> Unit, slots: AuthSlots) {
        SignInScreen(state, onEvent, slots = slots)
    }

    @Composable
    override fun OtpCode(state: OtpCodeState, onEvent: (SignInEvent) -> Unit) {
        OtpCodeScreen(state, onEvent)
    }

    @Composable
    override fun TotpChallenge(state: TotpChallengeState, onEvent: (SignInEvent) -> Unit) {
        TotpChallengeScreen(state, onEvent)
    }

    @Composable
    override fun ForcedPasswordChange(state: ForcedPasswordChangeState, onEvent: (SignInEvent) -> Unit) {
        ForcedPasswordChangeScreen(state, onEvent)
    }

    @Composable
    override fun Locked(state: LockedState, onEvent: (SignInEvent) -> Unit) {
        LockedScreen(state, onEvent)
    }

    @Composable
    override fun GuestUpgrade(state: GuestUpgradeState, onEvent: (GuestUpgradeEvent) -> Unit) {
        GuestUpgradeScreen(state, onEvent)
    }

    @Composable
    override fun Sessions(state: SessionsState, onEvent: (SessionsEvent) -> Unit) {
        SessionsScreen(state, onEvent)
    }

    @Composable
    override fun SignOutConfirm(state: SignOutConfirmState, onEvent: (SignOutEvent) -> Unit) {
        SignOutConfirmDialog(state, onEvent)
    }
}

/** The screen table of the subtree ([DefaultAuthScreens] unless a host provides another). */
public val LocalAuthScreens: ProvidableCompositionLocal<AuthScreens> = staticCompositionLocalOf { DefaultAuthScreens }

/**
 * Per-screen content slots of the sign-in screen (the web `brand` / `legal` props).
 *
 * @property brand drawn above the title (a wordmark).
 * @property belowForm drawn under the selected method's form.
 * @property footer drawn at the bottom (legal links).
 */
public class AuthSlots(
    public val brand: (@Composable () -> Unit)? = null,
    public val belowForm: (@Composable () -> Unit)? = null,
    public val footer: (@Composable () -> Unit)? = null,
)

/**
 * Auth's own widgets, restyled by a skin through `StapelSkin.extension(AuthSkinKey)` (the web
 * skin's auth-specific components). Everything else the screens draw goes through the StapelSkin
 * slots, so a skin that replaces `Button` restyles the auth screens with no auth-specific code.
 */
@Stable
public interface AuthSkin {
    /** One row of the sessions list with its actions. */
    @Composable
    public fun SessionRow(row: SessionRowState, actions: SessionRowActions, modifier: Modifier)

    /** A full-width button that picks [method] (bottom row and the "more ways" list). */
    @Composable
    public fun MethodButton(method: SignInMethod, label: String, onClick: () -> Unit, modifier: Modifier)
}

/**
 * What a session row can do; a null action is not offered.
 *
 * @property lastUsed the "last used …" line, already localized.
 * @property busy an action on this row is in flight.
 * @property onRevoke revoke this session (absent for the current one).
 * @property onConfirmMine mark a suspicious session as recognized.
 */
public class SessionRowActions(
    public val lastUsed: String,
    public val busy: Boolean,
    public val onRevoke: (() -> Unit)?,
    public val onConfirmMine: (() -> Unit)?,
)

/** Auth's default widgets (drawn with the StapelSkin slots). */
public object DefaultAuthSkin : AuthSkin {
    @Composable
    override fun SessionRow(row: SessionRowState, actions: SessionRowActions, modifier: Modifier) {
        DefaultSessionRow(row, actions, modifier)
    }

    @Composable
    override fun MethodButton(method: SignInMethod, label: String, onClick: () -> Unit, modifier: Modifier) {
        DefaultMethodButton(method, label, onClick, modifier)
    }
}

/** The key under which a skin may supply its own [AuthSkin]. */
public val AuthSkinKey: SkinExtensionKey<AuthSkin> = SkinExtensionKey("stapel.auth", DefaultAuthSkin)
