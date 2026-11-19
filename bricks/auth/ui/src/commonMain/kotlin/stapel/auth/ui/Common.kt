package stapel.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import stapel.i18n.StringKey
import stapel.result.StapelError
import stapel.result.text.ErrorCatalog
import stapel.result.text.LocaleTag
import stapel.ui.LocalStapelErrorText
import stapel.ui.LocalStapelStrings
import stapel.ui.StapelSizeClass
import stapel.ui.StapelSurface
import stapel.ui.StapelText
import stapel.ui.StapelTheme
import stapel.ui.skin.ElevationStep
import stapel.ui.skin.SurfaceTone
import stapel.auth.api.error.AuthErrorCatalog
import stapel.ui.stapelPlural
import stapel.ui.stapelString
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The auth error catalogs to register with the app's `ErrorTextResolver`, after the app's own
 * catalogs (the first catalog that has a text wins): the English contract texts of the domain library
 * and the Russian texts of this library.
 */
public fun authErrorCatalogs(): List<ErrorCatalog> = listOf(AuthErrorCatalog, AuthErrorTextsRu)

/** The sentence for [error] in the current locale, through the error-text ladder of `stapel-result`. */
@Composable
public fun authErrorText(error: StapelError): String {
    val strings = LocalStapelStrings.current
    val locale by strings.locale.collectAsState()
    return LocalStapelErrorText.current.message(error, LocaleTag(locale))
}

/** Stable test tags of the default screens (UI tests and end-to-end runs find nodes by them). */
public object AuthTestTags {
    /** The sign-in screen root. */
    public const val SIGN_IN: String = "auth.sign_in"

    /** The sign-in title. */
    public const val TITLE: String = "auth.sign_in.title"

    /** The notice banner. */
    public const val NOTICE: String = "auth.sign_in.notice"

    /** The tab strip of the main methods. */
    public const val METHOD_TABS: String = "auth.sign_in.methods"

    /** A method button of the bottom row / overflow list: suffix `.<method>` in lower case. */
    public const val METHOD_BUTTON: String = "auth.sign_in.method"

    /** "More ways to sign in". */
    public const val MORE_METHODS: String = "auth.sign_in.more"

    /** The identifier field of the code methods. */
    public const val IDENTIFIER: String = "auth.sign_in.identifier"

    /** "Send code". */
    public const val SEND_CODE: String = "auth.sign_in.send_code"

    /** The login field of the password method. */
    public const val LOGIN: String = "auth.sign_in.login"

    /** The password field. */
    public const val PASSWORD: String = "auth.sign_in.password"

    /** The repeated password field (registration, forced change). */
    public const val PASSWORD_CONFIRM: String = "auth.sign_in.password_confirm"

    /** The password submit action. */
    public const val SUBMIT: String = "auth.sign_in.submit"

    /** The form's error banner. */
    public const val FORM_ERROR: String = "auth.sign_in.error"

    /** The register / login switch. */
    public const val VARIANT_SWITCH: String = "auth.sign_in.variant_switch"

    /** "Continue as guest". */
    public const val GUEST: String = "auth.sign_in.guest"

    /** The code step root. */
    public const val CODE_STEP: String = "auth.code"

    /** The code field. */
    public const val CODE_FIELD: String = "auth.code.field"

    /** The code step's error. */
    public const val CODE_ERROR: String = "auth.code.error"

    /** The mock-delivery hint. */
    public const val MOCK_HINT: String = "auth.code.mock_hint"

    /** "Resend code". */
    public const val RESEND: String = "auth.code.resend"

    /** "Change" (edit identifier). */
    public const val EDIT_IDENTIFIER: String = "auth.code.edit"

    /** The TOTP step root. */
    public const val TOTP_STEP: String = "auth.totp"

    /** The TOTP / backup code field. */
    public const val TOTP_FIELD: String = "auth.totp.field"

    /** The backup-code toggle. */
    public const val TOTP_TOGGLE: String = "auth.totp.toggle"

    /** The forced change step root. */
    public const val FORCED_CHANGE: String = "auth.forced_change"

    /** The lockout root. */
    public const val LOCKED: String = "auth.locked"

    /** "Back to sign in". */
    public const val BACK_TO_FORM: String = "auth.back_to_form"

    /** The guest wall root. */
    public const val GUEST_WALL: String = "auth.guest_wall"

    /** The sessions screen root. */
    public const val SESSIONS: String = "auth.sessions"

    /** A session row: suffix `.<id>`. */
    public const val SESSION_ROW: String = "auth.sessions.row"

    /** "Sign out everyone else". */
    public const val SIGN_OUT_OTHERS: String = "auth.sessions.sign_out_others"

    /** The revoke confirmation dialog. */
    public const val REVOKE_CONFIRM: String = "auth.sessions.confirm"

    /** The sign-out confirmation dialog. */
    public const val SIGN_OUT_CONFIRM: String = "auth.sign_out.confirm"

    /** The account menu button. */
    public const val ACCOUNT_MENU: String = "auth.account_menu"

    /** The account menu's sign-out action. */
    public const val ACCOUNT_MENU_SIGN_OUT: String = "auth.account_menu.sign_out"
}

/** The label key of a method's tab / button. */
internal fun SignInMethod.labelKey(): StringKey = when (this) {
    SignInMethod.Email -> AuthUiStrings.ChannelEmail
    SignInMethod.Phone -> AuthUiStrings.ChannelPhone
    SignInMethod.Password -> AuthUiStrings.ChannelPassword
}

/** The sentence of a notice. */
internal fun AuthNotice.textKey(): StringKey = when (this) {
    AuthNotice.SignedOut -> AuthUiStrings.NoticeSignedOut
    AuthNotice.SessionExpired -> AuthUiStrings.NoticeExpired
    AuthNotice.SignedOutElsewhere -> AuthUiStrings.NoticeRevoked
    AuthNotice.AccountDisabled -> AuthUiStrings.NoticeAccountDisabled
    AuthNotice.AccountClosed -> AuthUiStrings.NoticeAccountClosed
}

/**
 * The column every auth screen draws its content in: on a phone the page itself (the gutter comes
 * from `StapelPage`), on wider windows a bordered card.
 */
@Composable
internal fun AuthCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val spacing = StapelTheme.spacing
    if (StapelTheme.layout.sizeClass == StapelSizeClass.Phone) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing.s4), content = content)
    } else {
        StapelSurface(modifier.fillMaxWidth(), tone = SurfaceTone.Raised, elevation = ElevationStep.Low, bordered = true) {
            Column(
                Modifier.fillMaxWidth().padding(spacing.s6),
                verticalArrangement = Arrangement.spacedBy(spacing.s4),
                content = content,
            )
        }
    }
}

/** A heading in the title style. */
@Composable
internal fun AuthHeading(text: String, modifier: Modifier = Modifier) {
    StapelText(text, modifier.semantics { heading() }, style = StapelTheme.type.heading)
}

/** Muted secondary text. */
@Composable
internal fun AuthHint(text: String, modifier: Modifier = Modifier) {
    StapelText(text, modifier, style = StapelTheme.type.bodySmall, color = StapelTheme.colors.textMuted)
}

/** "just now", "5 minutes ago", … for [then] relative to [now]. */
@Composable
internal fun relativeTime(then: Instant, now: Instant): String {
    val d: Duration = now - then
    return when {
        d.inWholeMinutes < 1 -> stapelString(AuthUiStrings.TimeJustNow)
        d.inWholeHours < 1 -> stapelPlural(AuthUiStrings.TimeMinutesAgo, d.inWholeMinutes)
        d.inWholeDays < 1 -> stapelPlural(AuthUiStrings.TimeHoursAgo, d.inWholeHours)
        else -> stapelPlural(AuthUiStrings.TimeDaysAgo, d.inWholeDays)
    }
}
