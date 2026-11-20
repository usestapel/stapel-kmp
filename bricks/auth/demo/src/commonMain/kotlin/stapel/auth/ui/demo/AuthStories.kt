package stapel.auth.ui.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import stapel.auth.ui.AccountMenu
import stapel.auth.ui.AuthNotice
import stapel.auth.ui.ForcedPasswordChangeState
import stapel.auth.ui.GuestEntry
import stapel.auth.ui.GuestUpgradeScreen
import stapel.auth.ui.GuestUpgradeState
import stapel.auth.ui.LockedState
import stapel.auth.ui.MethodZones
import stapel.auth.ui.OtpCodeState
import stapel.auth.ui.SessionDeviceKind
import stapel.auth.ui.SessionRowState
import stapel.auth.ui.SessionsScreen
import stapel.auth.ui.SessionsState
import stapel.auth.ui.SignInMethod
import stapel.auth.ui.SignInScreen
import stapel.auth.ui.SignInState
import stapel.auth.ui.SignInStep
import stapel.auth.ui.SignInVariant
import stapel.auth.ui.SignOutConfirmDialog
import stapel.auth.ui.SignOutConfirmState
import stapel.auth.ui.TotpChallengeState
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.result.ServerRefusal
import stapel.result.state.Freshness
import stapel.result.state.Resource
import stapel.result.text.ErrorTextResolver
import stapel.showcase.Story
import stapel.showcase.StoryCatalog
import stapel.showcase.StoryEnvironment
import stapel.showcase.StoryKind
import stapel.showcase.StoryScope
import stapel.showcase.StoryState
import stapel.showcase.StoryTarget
import stapel.ui.LocalStapelErrorText
import stapel.auth.ui.AuthUiBundle
import stapel.auth.ui.authErrorCatalogs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** One auth screen in one state: a [Story] of kind Screen whose content reports the screen's events. */
private fun auth(
    id: String,
    title: String,
    group: String,
    screens: List<String>,
    states: List<StoryState>,
    content: @Composable StoryScope.() -> Unit,
): Story = Story(id, title, group, StoryKind.Screen, states = states, covers = screens.map { StoryTarget.Screen(it) }.toSet(), content = content)

/** One auth screen in its default state only. */
private fun auth(id: String, title: String, group: String, screens: List<String>, content: @Composable StoryScope.() -> Unit): Story =
    auth(id, title, group, screens, emptyList(), content)

/**
 * The default state and its right-to-left twin (`ar`): the layout mirrors; the copy is English because
 * no Arabic bundle ships (a locale without a bundle falls back to the default locale).
 */
private val withRtl = listOf(StoryState.Default, StoryState("rtl", "Right to left (ar)", rtl = true, locale = "ar"))

private fun refusal(code: String, status: Int, vararg params: Pair<String, String>) =
    ServerRefusal(ErrorEnvelope(ErrorCode(code), status, ErrorParams(params.toMap())))

private val three = MethodZones(main = listOf(SignInMethod.Email, SignInMethod.Phone, SignInMethod.Password))
private val now = Instant.parse("2026-11-19T12:00:00Z")
private val code = OtpCodeState(SignInMethod.Email, "a***@example.com", codeLength = 4, resendIn = 42.seconds, isMock = true)

private fun signIn(state: SignInState): @Composable StoryScope.() -> Unit = { SignInScreen(state, { event(it.toString()) }) }

/** Every auth story, in the order of the sign-in journey. */
private val authStories: List<Story> = listOf(
    auth("sign_in.loading", "Sign-in, capabilities loading", "Sign-in", listOf("SignIn"), signIn(SignInState(three, loading = true))),
    auth("sign_in.load_failed", "Sign-in, capabilities failed", "Sign-in", listOf("SignIn"), signIn(SignInState(three, loadError = refusal("error.500.internal", 500)))),
    auth("sign_in.no_methods", "Sign-in, no method this app can complete", "Sign-in", listOf("SignIn"), signIn(SignInState(MethodZones(emptyList())))),
    auth("sign_in.email", "Method chooser, e-mail", "Sign-in", listOf("SignIn"), withRtl, signIn(SignInState(three, guest = GuestEntry(), canRegister = true))),
    auth("sign_in.phone", "Method chooser, phone", "Sign-in", listOf("SignIn"), signIn(SignInState(three, selected = SignInMethod.Phone))),
    auth("sign_in.password", "Password sign-in", "Sign-in", listOf("SignIn"), signIn(SignInState(three, selected = SignInMethod.Password))),
    auth("sign_in.password.refused", "Password sign-in, wrong password", "Sign-in", listOf("SignIn"),
        signIn(SignInState(three, selected = SignInMethod.Password, step = SignInStep.Form(error = refusal("error.401.invalid_credentials", 401)))),
    ),
    auth("sign_in.request_failed", "Code request refused", "Sign-in", listOf("SignIn"),
        signIn(SignInState(three, selected = SignInMethod.Phone, step = SignInStep.Form(error = refusal("error.400.invalid_phone_format", 400), identifier = "12"))),
    ),
    auth("sign_in.zones", "Bottom row and overflow", "Sign-in", listOf("SignIn"),
        signIn(SignInState(MethodZones(listOf(SignInMethod.Email), bottom = listOf(SignInMethod.Phone), overflow = listOf(SignInMethod.Password)))),
    ),
    auth("sign_in.register", "Registration", "Sign-in", listOf("SignIn"), signIn(SignInState(three, selected = SignInMethod.Password, variant = SignInVariant.Register, canRegister = true))),
    auth("sign_in.notice.revoked", "Signed out on another device", "Sign-in", listOf("SignIn"), signIn(SignInState(three, notice = AuthNotice.SignedOutElsewhere))),
    auth("sign_in.notice.expired", "Session expired", "Sign-in", listOf("SignIn"), signIn(SignInState(three, notice = AuthNotice.SessionExpired))),
    auth("sign_in.guest.creating", "Guest being created", "Sign-in", listOf("SignIn"), signIn(SignInState(three, guest = GuestEntry(creating = true)))),
    auth("code.sent", "Code sent (prototype, cooldown)", "Sign-in/Steps", listOf("SignIn", "OtpCode"), withRtl, signIn(SignInState(three, step = SignInStep.Code(code)))),
    auth("code.verifying", "Code being checked", "Sign-in/Steps", listOf("SignIn", "OtpCode"), signIn(SignInState(three, step = SignInStep.Code(code.copy(verifying = true))))),
    auth("code.error", "Wrong code, attempts left", "Sign-in/Steps", listOf("SignIn", "OtpCode"),
        signIn(SignInState(three, step = SignInStep.Code(code.copy(resendIn = 0.seconds, error = refusal("error.400.invalid_code_attempts", 400, "attempts_remaining" to "2"))))),
    ),
    auth("totp", "TOTP challenge", "Sign-in/Steps", listOf("SignIn", "TotpChallenge"), signIn(SignInState(three, step = SignInStep.Totp(TotpChallengeState())))),
    auth("totp.error", "TOTP refused", "Sign-in/Steps", listOf("SignIn", "TotpChallenge"),
        signIn(SignInState(three, step = SignInStep.Totp(TotpChallengeState(error = refusal("error.400.invalid_code", 400))))),
    ),
    auth("forced_change", "Forced password change", "Sign-in/Steps", listOf("SignIn", "ForcedPasswordChange"), signIn(SignInState(three, step = SignInStep.ForcedPasswordChange(ForcedPasswordChangeState())))),
    auth("mfa_enroll", "Two-step enrolment required", "Sign-in/Steps", listOf("SignIn"), signIn(SignInState(three, step = SignInStep.MfaEnrollRequired))),
    auth("locked", "Account locked", "Sign-in/Steps", listOf("SignIn", "Locked"),
        signIn(SignInState(three, step = SignInStep.Locked(LockedState(refusal("error.423.account_locked", 423, "retry_after_minutes" to "15"), 15.minutes)))),
    ),
    auth("blocked", "Blocked, no known end", "Sign-in/Steps", listOf("SignIn", "Locked"),
        signIn(SignInState(three, step = SignInStep.Locked(LockedState(refusal("error.422.blocked", 422))))),
    ),
    auth("guest_wall", "Guest wall", "Guest", listOf("GuestUpgrade")) { GuestUpgradeScreen(GuestUpgradeState(), { event(it.toString()) }) },
    auth("sessions", "Sessions and devices", "Account/Sessions", listOf("Sessions"), withRtl) { SessionsScreen(SessionsState(Resource.Ready(sessions, Freshness.network(0)), now), { event(it.toString()) }) },
    auth("sessions.confirm", "Revoke confirmation", "Account/Sessions", listOf("Sessions")) {
        SessionsScreen(SessionsState(Resource.Ready(sessions, Freshness.network(0)), now, confirmRevoke = "s3"), { event(it.toString()) })
    },
    auth("sessions.loading", "Sessions loading", "Account/Sessions", listOf("Sessions")) { SessionsScreen(SessionsState(Resource.Loading, now), { event(it.toString()) }) },
    auth("sessions.failed", "Sessions failed", "Account/Sessions", listOf("Sessions")) { SessionsScreen(SessionsState(Resource.Failed(refusal("error.500.internal", 500)), now), { event(it.toString()) }) },
    auth("sign_out", "Sign-out confirmation", "Account/Sign-out", listOf("SignOutConfirm")) { SignOutConfirmDialog(SignOutConfirmState(), { event(it.toString()) }) },
    auth("sign_out.guest", "Sign-out confirmation, guest", "Account/Sign-out", listOf("SignOutConfirm")) { SignOutConfirmDialog(SignOutConfirmState(isGuest = true), { event(it.toString()) }) },
    auth("account_menu", "Account control", "Account", emptyList()) { AccountMenu("ann@example.com", isGuest = false, onSignOut = { event("sign out") }) },
)

private val sessions = listOf(
    SessionRowState("s1", "Desktop app", "macOS", "127.0.0.1", now - 1.minutes, isCurrent = true, isSuspicious = false, kind = SessionDeviceKind.Desktop),
    SessionRowState("s2", "iOS app", "iPhone", null, now - 3.hours, isCurrent = false, isSuspicious = true, kind = SessionDeviceKind.Phone),
    SessionRowState("s3", "Android app", "Pixel", null, now - 50.hours, isCurrent = false, isSuspicious = false, kind = SessionDeviceKind.Phone),
)

/** What the auth stories need around them: the auth strings (en, ru) and the auth error catalogs. */
val authStoryEnvironment: StoryEnvironment = StoryEnvironment(
    bundles = listOf(AuthUiBundle),
    locales = listOf("en", "ru"),
    provide = { content -> CompositionLocalProvider(LocalStapelErrorText provides ErrorTextResolver(authErrorCatalogs())) { content() } },
)

/** The auth catalogue: every screen state of stapel-auth-ui, no backend. */
val storyCatalog: StoryCatalog = StoryCatalog("auth", "Auth", authStories, authStoryEnvironment)
