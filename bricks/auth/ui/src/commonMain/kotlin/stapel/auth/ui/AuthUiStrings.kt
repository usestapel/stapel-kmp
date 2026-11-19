package stapel.auth.ui

import stapel.i18n.I18nBundle
import stapel.i18n.PluralKey
import stapel.i18n.StringKey

/**
 * Typed keys of this library's UI copy. Key names follow the web pair (`auth.*` of
 * `@stapel/auth-react`) so one translation override applies to both platforms; keys that exist only
 * on mobile live under `auth.ui.*` too and are listed in [AuthUiMobileOnlyKeys].
 */
public object AuthUiStrings {
    /** "Sign in" — the sign-in screen title. */
    public val LoginTitle: StringKey = StringKey("auth.ui.login_title")

    /** "Create account" — the registration variant's title. */
    public val RegisterTitle: StringKey = StringKey("auth.ui.register_title")

    /** "or" — the divider above the alternative methods. */
    public val Or: StringKey = StringKey("auth.ui.or")

    /** "More ways to sign in". */
    public val MoreMethods: StringKey = StringKey("auth.ui.more_methods")

    /** "Close". */
    public val Close: StringKey = StringKey("auth.ui.close")

    /** "Continue as guest". */
    public val ContinueAsGuest: StringKey = StringKey("auth.ui.continue_as_guest")

    /** "You can sign in later — your data will be kept." */
    public val ContinueAsGuestHint: StringKey = StringKey("auth.ui.continue_as_guest_hint")

    /** "Continuing…". */
    public val ContinueAsGuestPending: StringKey = StringKey("auth.ui.continue_as_guest_pending")

    /** "Already have an account? Sign in". */
    public val SwitchToLogin: StringKey = StringKey("auth.ui.switch_to_login")

    /** "New here? Create an account". */
    public val SwitchToRegister: StringKey = StringKey("auth.ui.switch_to_register")

    /** "Email" — the e-mail method's tab label. */
    public val ChannelEmail: StringKey = StringKey("auth.ui.channel_email")

    /** "Phone" — the phone method's tab label. */
    public val ChannelPhone: StringKey = StringKey("auth.ui.channel_phone")

    /** "Password" — the password method's tab label. */
    public val ChannelPassword: StringKey = StringKey("auth.ui.channel_password")

    /** "Email" — the e-mail field label. */
    public val EmailLabel: StringKey = StringKey("auth.ui.email_label")

    /** "you@example.com". */
    public val EmailPlaceholder: StringKey = StringKey("auth.ui.email_placeholder")

    /** "Phone" — the phone field label. */
    public val PhoneLabel: StringKey = StringKey("auth.ui.phone_label")

    /** "+1 555 000 0000". */
    public val PhonePlaceholder: StringKey = StringKey("auth.ui.phone_placeholder")

    /** "Your password". */
    public val PasswordPlaceholder: StringKey = StringKey("auth.ui.password_placeholder")

    /** "Send code". */
    public val SendCode: StringKey = StringKey("auth.ui.send_code")

    /** "Sign in" — the submit action. */
    public val Submit: StringKey = StringKey("auth.ui.submit")

    /** "Create account" — the registration submit action. */
    public val RegisterSubmit: StringKey = StringKey("auth.ui.register_submit")

    /** "Confirm password". */
    public val RegisterConfirmLabel: StringKey = StringKey("auth.ui.register_confirm_label")

    /** "Passwords don't match." */
    public val RegisterMismatch: StringKey = StringKey("auth.ui.register_mismatch")

    /** "Resend in {s}s" — why the resend action is not available yet. */
    public val ResendIn: StringKey = StringKey("auth.ui.resend_in")

    /** "Try again". */
    public val Retry: StringKey = StringKey("auth.ui.retry")

    /** "Enter the code we sent you". */
    public val OtpEnterCode: StringKey = StringKey("auth.otp.enter_code")

    /** The prototype-posture hint: nothing was sent, use the configured code (never shown). */
    public val OtpMockDelivery: StringKey = StringKey("auth.otp.mock_delivery")

    /** "Resend code". */
    public val OtpResend: StringKey = StringKey("auth.otp.resend")

    /** "Code sent to {target}". */
    public val OtpSentTo: StringKey = StringKey("auth.otp.sent_to")

    /** "Password" — the password field label. */
    public val PasswordLabel: StringKey = StringKey("auth.password.label")

    /** "Enter your 6-digit code". */
    public val TotpEnterCode: StringKey = StringKey("auth.totp.enter_code")

    /** "Use a backup code". */
    public val TotpUseBackup: StringKey = StringKey("auth.totp.use_backup")

    /** "Set your own password". */
    public val ForcedChangeTitle: StringKey = StringKey("auth.forcedChange.title")

    /** Why an organization-issued password has to be replaced. */
    public val ForcedChangeHint: StringKey = StringKey("auth.forcedChange.hint")

    /** "New password". */
    public val ForcedChangeNewLabel: StringKey = StringKey("auth.forcedChange.new_label")

    /** "Repeat new password". */
    public val ForcedChangeConfirmLabel: StringKey = StringKey("auth.forcedChange.confirm_label")

    /** "Passwords don't match". */
    public val ForcedChangeMismatch: StringKey = StringKey("auth.forcedChange.mismatch")

    /** "Set password and continue". */
    public val ForcedChangeSubmit: StringKey = StringKey("auth.forcedChange.submit")

    /** "Verified" — shown for the instant between a completed sign-in and the navigation away. */
    public val VerificationSuccess: StringKey = StringKey("auth.verification.success")

    /** "Active sessions". */
    public val SessionsTitle: StringKey = StringKey("auth.sec.sessions.title")

    /** What the sessions list is. */
    public val SessionsSubtitle: StringKey = StringKey("auth.sec.sessions.subtitle")

    /** "No active sessions." */
    public val SessionsEmpty: StringKey = StringKey("auth.sec.sessions.empty")

    /** Where sessions come from. */
    public val SessionsEmptyHint: StringKey = StringKey("auth.sec.sessions.empty_hint")

    /** "Last used {when}". */
    public val SessionsLastUsed: StringKey = StringKey("auth.sec.sessions.last_used")

    /** "Sign out" — revoke one session. */
    public val SessionsSignOut: StringKey = StringKey("auth.sec.sessions.sign_out")

    /** "Sign out everyone else". */
    public val SessionsSignOutAll: StringKey = StringKey("auth.sec.sessions.sign_out_all")

    /** "Sign out all other devices?" */
    public val SessionsSignOutAllConfirmTitle: StringKey = StringKey("auth.sec.sessions.sign_out_all_confirm_title")

    /** "Sign out this device?" */
    public val SessionsSignOutConfirmTitle: StringKey = StringKey("auth.sec.sessions.sign_out_confirm_title")

    /** "This was me" — confirms a session flagged as suspicious. */
    public val SessionsConfirmMe: StringKey = StringKey("auth.sec.sessions.confirm_me")

    /** "This device". */
    public val SessionThisDevice: StringKey = StringKey("auth.session.this_device")

    /** "Unrecognized sign-in". */
    public val SessionSuspicious: StringKey = StringKey("auth.session.suspicious")

    /** "Account" — opens the account section from the account menu. */
    public val AccountMenuOpen: StringKey = StringKey("auth.account_menu.open")

    /** "Sign out" — the account menu's sign-out action. */
    public val AccountMenuSignOut: StringKey = StringKey("auth.account_menu.sign_out")

    /** "Account menu, signed in as {name}" — the account menu's accessible name. */
    public val AccountMenuLabelNamed: StringKey = StringKey("auth.account_menu.label_named")

    /** "Sign in" — the nav label of `auth.login`. */
    public val NavLogin: StringKey = StringKey("auth.nav.login")

    /** "Security" — the nav label of `auth.security`. */
    public val NavSecurity: StringKey = StringKey("auth.nav.security")

    /** Mobile: "Change" — back from the code step to the identifier, keeping what was typed. */
    public val EditIdentifier: StringKey = StringKey("auth.ui.edit_identifier")

    /** Mobile: "Two-step verification" — the TOTP challenge title. */
    public val TotpTitle: StringKey = StringKey("auth.ui.totp_title")

    /** Mobile: "Use the code from your authenticator app" — back from the backup-code entry. */
    public val TotpUseApp: StringKey = StringKey("auth.ui.totp_use_app")

    /** Mobile: "Back to sign in". */
    public val BackToSignIn: StringKey = StringKey("auth.ui.back_to_sign_in")

    /** Mobile: "Sign-in is paused" — the locked/blocked title. */
    public val LockedTitle: StringKey = StringKey("auth.ui.locked_title")

    /** Mobile: "Try again in {count} minutes." — a plural family. */
    public val LockedMinutes: PluralKey = PluralKey("auth.ui.locked_minutes")

    /** Mobile: "Try again later." — a lockout with no known end. */
    public val LockedLater: StringKey = StringKey("auth.ui.locked_later")

    /** Mobile: "You have signed out." */
    public val NoticeSignedOut: StringKey = StringKey("auth.ui.notice_signed_out")

    /** Mobile: "Your session has expired. Please sign in again." */
    public val NoticeExpired: StringKey = StringKey("auth.ui.notice_expired")

    /** Mobile: "You were signed out on another device." — a revoked session. */
    public val NoticeRevoked: StringKey = StringKey("auth.ui.notice_revoked")

    /** Mobile: "This account is disabled." */
    public val NoticeAccountDisabled: StringKey = StringKey("auth.ui.notice_account_disabled")

    /** Mobile: "This account has been closed." */
    public val NoticeAccountClosed: StringKey = StringKey("auth.ui.notice_account_closed")

    /** Mobile: the server enables no sign-in method this app can complete. */
    public val NoMethods: StringKey = StringKey("auth.ui.no_methods")

    /** Mobile: two-step enrolment is required and is finished on the web (until the enrol screens ship). */
    public val MfaEnrollOnWeb: StringKey = StringKey("auth.ui.mfa_enroll_on_web")

    /** Mobile: "Create an account to continue" — the guest wall title. */
    public val GuestTitle: StringKey = StringKey("auth.ui.guest_title")

    /** Mobile: why the guest is asked to create an account. */
    public val GuestBody: StringKey = StringKey("auth.ui.guest_body")

    /** Mobile: "Sign in" — the guest wall's sign-in action. */
    public val GuestSignIn: StringKey = StringKey("auth.ui.guest_sign_in")

    /** Mobile: "Create account" — the guest wall's register action. */
    public val GuestRegister: StringKey = StringKey("auth.ui.guest_register")

    /** Mobile: "Not now". */
    public val GuestNotNow: StringKey = StringKey("auth.ui.guest_not_now")

    /** Mobile: "Sign out?" — the sign-out confirmation title. */
    public val SignOutConfirmTitle: StringKey = StringKey("auth.ui.sign_out_confirm_title")

    /** Mobile: what signing out does. */
    public val SignOutConfirmBody: StringKey = StringKey("auth.ui.sign_out_confirm_body")

    /** Mobile: what signing out a guest loses. */
    public val SignOutGuestBody: StringKey = StringKey("auth.ui.sign_out_guest_body")

    /** Mobile: "Cancel". */
    public val Cancel: StringKey = StringKey("auth.ui.cancel")

    /** Mobile: "Refresh" — reloads the sessions list. */
    public val SessionsRefresh: StringKey = StringKey("auth.ui.sessions_refresh")

    /** Mobile: "Back". */
    public val Back: StringKey = StringKey("auth.ui.back")

    /** Mobile: "just now". */
    public val TimeJustNow: StringKey = StringKey("auth.ui.time_just_now")

    /** Mobile: "{count} minutes ago" — a plural family. */
    public val TimeMinutesAgo: PluralKey = PluralKey("auth.ui.time_minutes_ago")

    /** Mobile: "{count} hours ago" — a plural family. */
    public val TimeHoursAgo: PluralKey = PluralKey("auth.ui.time_hours_ago")

    /** Mobile: "{count} days ago" — a plural family. */
    public val TimeDaysAgo: PluralKey = PluralKey("auth.ui.time_days_ago")
}

/** The plural families of [AuthUiBundle] (for the completeness audit). */
public val AuthUiPluralFamilies: Set<String> = setOf(
    AuthUiStrings.LockedMinutes.key,
    AuthUiStrings.TimeMinutesAgo.key,
    AuthUiStrings.TimeHoursAgo.key,
    AuthUiStrings.TimeDaysAgo.key,
)

/** Keys this library adds that the web pair does not have (everything else mirrors a web key). */
public val AuthUiMobileOnlyKeys: Set<String> = setOf(
    "auth.ui.edit_identifier", "auth.ui.totp_title", "auth.ui.totp_use_app", "auth.ui.back_to_sign_in",
    "auth.ui.locked_title", "auth.ui.locked_minutes", "auth.ui.locked_later", "auth.ui.notice_signed_out",
    "auth.ui.notice_expired", "auth.ui.notice_revoked", "auth.ui.notice_account_disabled",
    "auth.ui.notice_account_closed", "auth.ui.no_methods", "auth.ui.mfa_enroll_on_web", "auth.ui.guest_title",
    "auth.ui.guest_body", "auth.ui.guest_sign_in", "auth.ui.guest_register", "auth.ui.guest_not_now",
    "auth.ui.sign_out_confirm_title", "auth.ui.sign_out_confirm_body", "auth.ui.sign_out_guest_body",
    "auth.ui.cancel", "auth.ui.sessions_refresh", "auth.ui.back", "auth.ui.time_just_now",
    "auth.ui.time_minutes_ago", "auth.ui.time_hours_ago", "auth.ui.time_days_ago",
)

/**
 * This library's UI copy in en and ru. Register it after `CoreUiBundle` and before the app's own
 * overrides: `StapelStrings { register(CoreUiBundle); register(AuthUiBundle); override("ru", …) }`.
 */
public val AuthUiBundle: I18nBundle = I18nBundle(
    namespace = "auth",
    locales = mapOf("en" to { AUTH_UI_EN }, "ru" to { AUTH_UI_RU }),
)

private val AUTH_UI_EN: Map<String, String> = mapOf(
    "auth.ui.login_title" to "Sign in",
    "auth.ui.register_title" to "Create account",
    "auth.ui.or" to "or",
    "auth.ui.more_methods" to "More ways to sign in",
    "auth.ui.close" to "Close",
    "auth.ui.continue_as_guest" to "Continue as guest",
    "auth.ui.continue_as_guest_hint" to "You can sign in later — your data will be kept.",
    "auth.ui.continue_as_guest_pending" to "Continuing…",
    "auth.ui.switch_to_login" to "Already have an account? Sign in",
    "auth.ui.switch_to_register" to "New here? Create an account",
    "auth.ui.channel_email" to "Email",
    "auth.ui.channel_phone" to "Phone",
    "auth.ui.channel_password" to "Password",
    "auth.ui.email_label" to "Email",
    "auth.ui.email_placeholder" to "you@example.com",
    "auth.ui.phone_label" to "Phone",
    "auth.ui.phone_placeholder" to "+1 555 000 0000",
    "auth.ui.password_placeholder" to "Your password",
    "auth.ui.send_code" to "Send code",
    "auth.ui.submit" to "Sign in",
    "auth.ui.register_submit" to "Create account",
    "auth.ui.register_confirm_label" to "Confirm password",
    "auth.ui.register_mismatch" to "Passwords don't match.",
    "auth.ui.resend_in" to "Resend in {s}s",
    "auth.ui.retry" to "Try again",
    "auth.otp.enter_code" to "Enter the code we sent you",
    "auth.otp.mock_delivery" to "Test mode: no code was actually sent. Use the code this environment is configured with.",
    "auth.otp.resend" to "Resend code",
    "auth.otp.sent_to" to "Code sent to {target}",
    "auth.password.label" to "Password",
    "auth.totp.enter_code" to "Enter your 6-digit code",
    "auth.totp.use_backup" to "Use a backup code",
    "auth.forcedChange.title" to "Set your own password",
    "auth.forcedChange.hint" to
        "Your organization issued a temporary password. Choose your own to continue — you'll use it from now on.",
    "auth.forcedChange.new_label" to "New password",
    "auth.forcedChange.confirm_label" to "Repeat new password",
    "auth.forcedChange.mismatch" to "Passwords don't match",
    "auth.forcedChange.submit" to "Set password and continue",
    "auth.verification.success" to "Verified",
    "auth.sec.sessions.title" to "Active sessions",
    "auth.sec.sessions.subtitle" to "Where you're currently signed in. Signing out revokes that device immediately.",
    "auth.sec.sessions.empty" to "No active sessions.",
    "auth.sec.sessions.empty_hint" to "Sessions appear here as you sign in on other browsers and devices.",
    "auth.sec.sessions.last_used" to "Last used {when}",
    "auth.sec.sessions.sign_out" to "Sign out",
    "auth.sec.sessions.sign_out_all" to "Sign out everyone else",
    "auth.sec.sessions.sign_out_all_confirm_title" to "Sign out all other devices?",
    "auth.sec.sessions.sign_out_confirm_title" to "Sign out this device?",
    "auth.sec.sessions.confirm_me" to "This was me",
    "auth.session.this_device" to "This device",
    "auth.session.suspicious" to "Unrecognized sign-in",
    "auth.account_menu.open" to "Account",
    "auth.account_menu.sign_out" to "Sign out",
    "auth.account_menu.label_named" to "Account menu, signed in as {name}",
    "auth.nav.login" to "Sign in",
    "auth.nav.security" to "Security",
    "auth.ui.edit_identifier" to "Change",
    "auth.ui.totp_title" to "Two-step verification",
    "auth.ui.totp_use_app" to "Use the code from your authenticator app",
    "auth.ui.back_to_sign_in" to "Back to sign in",
    "auth.ui.locked_title" to "Sign-in is paused",
    "auth.ui.locked_minutes.one" to "Try again in {count} minute.",
    "auth.ui.locked_minutes.other" to "Try again in {count} minutes.",
    "auth.ui.locked_later" to "Try again later.",
    "auth.ui.notice_signed_out" to "You have signed out.",
    "auth.ui.notice_expired" to "Your session has expired. Please sign in again.",
    "auth.ui.notice_revoked" to "You were signed out on another device.",
    "auth.ui.notice_account_disabled" to "This account is disabled.",
    "auth.ui.notice_account_closed" to "This account has been closed.",
    "auth.ui.no_methods" to "No sign-in method is available in this app. Please contact support.",
    "auth.ui.mfa_enroll_on_web" to "Your account needs two-step verification. Set it up on the web, then sign in here.",
    "auth.ui.guest_title" to "Create an account to continue",
    "auth.ui.guest_body" to "You're using a guest account. Sign in or create an account to keep your data and continue.",
    "auth.ui.guest_sign_in" to "Sign in",
    "auth.ui.guest_register" to "Create account",
    "auth.ui.guest_not_now" to "Not now",
    "auth.ui.sign_out_confirm_title" to "Sign out?",
    "auth.ui.sign_out_confirm_body" to "You'll need to sign in again to use your account on this device.",
    "auth.ui.sign_out_guest_body" to "You're signed in as a guest. Signing out loses what this guest account holds.",
    "auth.ui.cancel" to "Cancel",
    "auth.ui.sessions_refresh" to "Refresh",
    "auth.ui.back" to "Back",
    "auth.ui.time_just_now" to "just now",
    "auth.ui.time_minutes_ago.one" to "{count} minute ago",
    "auth.ui.time_minutes_ago.other" to "{count} minutes ago",
    "auth.ui.time_hours_ago.one" to "{count} hour ago",
    "auth.ui.time_hours_ago.other" to "{count} hours ago",
    "auth.ui.time_days_ago.one" to "{count} day ago",
    "auth.ui.time_days_ago.other" to "{count} days ago",
)

private val AUTH_UI_RU: Map<String, String> = mapOf(
    "auth.ui.login_title" to "Вход",
    "auth.ui.register_title" to "Создать аккаунт",
    "auth.ui.or" to "или",
    "auth.ui.more_methods" to "Другие способы входа",
    "auth.ui.close" to "Закрыть",
    "auth.ui.continue_as_guest" to "Продолжить как гость",
    "auth.ui.continue_as_guest_hint" to "Войти можно позже — данные сохранятся.",
    "auth.ui.continue_as_guest_pending" to "Входим…",
    "auth.ui.switch_to_login" to "Уже есть аккаунт? Войти",
    "auth.ui.switch_to_register" to "Впервые здесь? Создайте аккаунт",
    "auth.ui.channel_email" to "Эл. почта",
    "auth.ui.channel_phone" to "Телефон",
    "auth.ui.channel_password" to "Пароль",
    "auth.ui.email_label" to "Эл. почта",
    "auth.ui.email_placeholder" to "you@example.com",
    "auth.ui.phone_label" to "Телефон",
    "auth.ui.phone_placeholder" to "+7 900 000 0000",
    "auth.ui.password_placeholder" to "Ваш пароль",
    "auth.ui.send_code" to "Отправить код",
    "auth.ui.submit" to "Войти",
    "auth.ui.register_submit" to "Создать аккаунт",
    "auth.ui.register_confirm_label" to "Подтвердите пароль",
    "auth.ui.register_mismatch" to "Пароли не совпадают.",
    "auth.ui.resend_in" to "Повторить через {s} с",
    "auth.ui.retry" to "Повторить",
    "auth.otp.enter_code" to "Введите код из сообщения",
    "auth.otp.mock_delivery" to "Тестовый режим: код не отправлялся. Используйте код, заданный в настройках стенда.",
    "auth.otp.resend" to "Отправить код ещё раз",
    "auth.otp.sent_to" to "Код отправлен на {target}",
    "auth.password.label" to "Пароль",
    "auth.totp.enter_code" to "Введите 6-значный код",
    "auth.totp.use_backup" to "Использовать резервный код",
    "auth.forcedChange.title" to "Задайте собственный пароль",
    "auth.forcedChange.hint" to
        "Организация выдала вам временный пароль. Придумайте свой, чтобы продолжить — дальше вы будете входить с ним.",
    "auth.forcedChange.new_label" to "Новый пароль",
    "auth.forcedChange.confirm_label" to "Повторите новый пароль",
    "auth.forcedChange.mismatch" to "Пароли не совпадают",
    "auth.forcedChange.submit" to "Задать пароль и продолжить",
    "auth.verification.success" to "Подтверждено",
    "auth.sec.sessions.title" to "Активные сеансы",
    "auth.sec.sessions.subtitle" to "Где вы сейчас вошли в систему. Выход из сеанса отзывает устройство немедленно.",
    "auth.sec.sessions.empty" to "Нет активных сеансов.",
    "auth.sec.sessions.empty_hint" to "Сеансы появятся здесь, когда вы войдёте в других браузерах и на других устройствах.",
    "auth.sec.sessions.last_used" to "Последняя активность {when}",
    "auth.sec.sessions.sign_out" to "Выйти",
    "auth.sec.sessions.sign_out_all" to "Выйти на всех других устройствах",
    "auth.sec.sessions.sign_out_all_confirm_title" to "Выйти на всех других устройствах?",
    "auth.sec.sessions.sign_out_confirm_title" to "Выйти на этом устройстве?",
    "auth.sec.sessions.confirm_me" to "Это я",
    "auth.session.this_device" to "Это устройство",
    "auth.session.suspicious" to "Неопознанный вход",
    "auth.account_menu.open" to "Аккаунт",
    "auth.account_menu.sign_out" to "Выйти",
    "auth.account_menu.label_named" to "Меню аккаунта, вы вошли как {name}",
    "auth.nav.login" to "Вход",
    "auth.nav.security" to "Безопасность",
    "auth.ui.edit_identifier" to "Изменить",
    "auth.ui.totp_title" to "Двухэтапная проверка",
    "auth.ui.totp_use_app" to "Ввести код из приложения-аутентификатора",
    "auth.ui.back_to_sign_in" to "Вернуться ко входу",
    "auth.ui.locked_title" to "Вход временно недоступен",
    "auth.ui.locked_minutes.one" to "Попробуйте через {count} минуту.",
    "auth.ui.locked_minutes.few" to "Попробуйте через {count} минуты.",
    "auth.ui.locked_minutes.many" to "Попробуйте через {count} минут.",
    "auth.ui.locked_minutes.other" to "Попробуйте через {count} минуты.",
    "auth.ui.locked_later" to "Попробуйте позже.",
    "auth.ui.notice_signed_out" to "Вы вышли из аккаунта.",
    "auth.ui.notice_expired" to "Сессия истекла. Войдите снова.",
    "auth.ui.notice_revoked" to "Вы вышли из аккаунта с другого устройства.",
    "auth.ui.notice_account_disabled" to "Этот аккаунт отключён.",
    "auth.ui.notice_account_closed" to "Этот аккаунт закрыт.",
    "auth.ui.no_methods" to "В приложении нет доступного способа входа. Обратитесь в поддержку.",
    "auth.ui.mfa_enroll_on_web" to "Для аккаунта нужна двухэтапная проверка. Настройте её в веб-версии, затем войдите здесь.",
    "auth.ui.guest_title" to "Создайте аккаунт, чтобы продолжить",
    "auth.ui.guest_body" to "Вы используете гостевой аккаунт. Войдите или создайте аккаунт, чтобы сохранить данные и продолжить.",
    "auth.ui.guest_sign_in" to "Войти",
    "auth.ui.guest_register" to "Создать аккаунт",
    "auth.ui.guest_not_now" to "Не сейчас",
    "auth.ui.sign_out_confirm_title" to "Выйти?",
    "auth.ui.sign_out_confirm_body" to "Чтобы снова пользоваться аккаунтом на этом устройстве, нужно будет войти.",
    "auth.ui.sign_out_guest_body" to "Вы вошли как гость. После выхода данные гостевого аккаунта будут потеряны.",
    "auth.ui.cancel" to "Отмена",
    "auth.ui.sessions_refresh" to "Обновить",
    "auth.ui.back" to "Назад",
    "auth.ui.time_just_now" to "только что",
    "auth.ui.time_minutes_ago.one" to "{count} минуту назад",
    "auth.ui.time_minutes_ago.few" to "{count} минуты назад",
    "auth.ui.time_minutes_ago.many" to "{count} минут назад",
    "auth.ui.time_minutes_ago.other" to "{count} минуты назад",
    "auth.ui.time_hours_ago.one" to "{count} час назад",
    "auth.ui.time_hours_ago.few" to "{count} часа назад",
    "auth.ui.time_hours_ago.many" to "{count} часов назад",
    "auth.ui.time_hours_ago.other" to "{count} часа назад",
    "auth.ui.time_days_ago.one" to "{count} день назад",
    "auth.ui.time_days_ago.few" to "{count} дня назад",
    "auth.ui.time_days_ago.many" to "{count} дней назад",
    "auth.ui.time_days_ago.other" to "{count} дня назад",
)
