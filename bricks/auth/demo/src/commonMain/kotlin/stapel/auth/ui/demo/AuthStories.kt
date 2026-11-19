package stapel.auth.ui.demo

import androidx.compose.runtime.Composable
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * One state of one auth screen.
 *
 * @property id stable id (`sign_in.code.error`).
 * @property title what the state shows.
 * @property content the screen in that state (events are dropped).
 */
public class AuthStory(val id: String, val title: String, val content: @Composable () -> Unit)

private fun refusal(code: String, status: Int, vararg params: Pair<String, String>) =
    ServerRefusal(ErrorEnvelope(ErrorCode(code), status, ErrorParams(params.toMap())))

private val three = MethodZones(main = listOf(SignInMethod.Email, SignInMethod.Phone, SignInMethod.Password))
private val now = Instant.parse("2026-11-19T12:00:00Z")
private val code = OtpCodeState(SignInMethod.Email, "a***@example.com", codeLength = 4, resendIn = 42.seconds, isMock = true)

private fun signIn(state: SignInState): @Composable () -> Unit = { SignInScreen(state, {}) }

/** Every auth story, in the order of the sign-in journey. */
val AuthStories: List<AuthStory> = listOf(
    AuthStory("sign_in.loading", "Sign-in, capabilities loading", signIn(SignInState(three, loading = true))),
    AuthStory("sign_in.load_failed", "Sign-in, capabilities failed", signIn(SignInState(three, loadError = refusal("error.500.internal", 500)))),
    AuthStory("sign_in.no_methods", "Sign-in, no method this app can complete", signIn(SignInState(MethodZones(emptyList())))),
    AuthStory("sign_in.email", "Method chooser, e-mail", signIn(SignInState(three, guest = GuestEntry(), canRegister = true))),
    AuthStory("sign_in.phone", "Method chooser, phone", signIn(SignInState(three, selected = SignInMethod.Phone))),
    AuthStory("sign_in.password", "Password sign-in", signIn(SignInState(three, selected = SignInMethod.Password))),
    AuthStory(
        "sign_in.password.refused", "Password sign-in, wrong password",
        signIn(SignInState(three, selected = SignInMethod.Password, step = SignInStep.Form(error = refusal("error.401.invalid_credentials", 401)))),
    ),
    AuthStory(
        "sign_in.request_failed", "Code request refused",
        signIn(SignInState(three, selected = SignInMethod.Phone, step = SignInStep.Form(error = refusal("error.400.invalid_phone_format", 400), identifier = "12"))),
    ),
    AuthStory(
        "sign_in.zones", "Bottom row and overflow",
        signIn(SignInState(MethodZones(listOf(SignInMethod.Email), bottom = listOf(SignInMethod.Phone), overflow = listOf(SignInMethod.Password)))),
    ),
    AuthStory("sign_in.register", "Registration", signIn(SignInState(three, selected = SignInMethod.Password, variant = SignInVariant.Register, canRegister = true))),
    AuthStory("sign_in.notice.revoked", "Signed out on another device", signIn(SignInState(three, notice = AuthNotice.SignedOutElsewhere))),
    AuthStory("sign_in.notice.expired", "Session expired", signIn(SignInState(three, notice = AuthNotice.SessionExpired))),
    AuthStory("sign_in.guest.creating", "Guest being created", signIn(SignInState(three, guest = GuestEntry(creating = true)))),
    AuthStory("code.sent", "Code sent (prototype, cooldown)", signIn(SignInState(three, step = SignInStep.Code(code)))),
    AuthStory("code.verifying", "Code being checked", signIn(SignInState(three, step = SignInStep.Code(code.copy(verifying = true))))),
    AuthStory(
        "code.error", "Wrong code, attempts left",
        signIn(SignInState(three, step = SignInStep.Code(code.copy(resendIn = 0.seconds, error = refusal("error.400.invalid_code_attempts", 400, "attempts_remaining" to "2"))))),
    ),
    AuthStory("totp", "TOTP challenge", signIn(SignInState(three, step = SignInStep.Totp(TotpChallengeState())))),
    AuthStory(
        "totp.error", "TOTP refused",
        signIn(SignInState(three, step = SignInStep.Totp(TotpChallengeState(error = refusal("error.400.invalid_code", 400))))),
    ),
    AuthStory("forced_change", "Forced password change", signIn(SignInState(three, step = SignInStep.ForcedPasswordChange(ForcedPasswordChangeState())))),
    AuthStory("mfa_enroll", "Two-step enrolment required", signIn(SignInState(three, step = SignInStep.MfaEnrollRequired))),
    AuthStory(
        "locked", "Account locked",
        signIn(SignInState(three, step = SignInStep.Locked(LockedState(refusal("error.423.account_locked", 423, "retry_after_minutes" to "15"), 15.minutes)))),
    ),
    AuthStory(
        "blocked", "Blocked, no known end",
        signIn(SignInState(three, step = SignInStep.Locked(LockedState(refusal("error.422.blocked", 422))))),
    ),
    AuthStory("guest_wall", "Guest wall") { GuestUpgradeScreen(GuestUpgradeState(), {}) },
    AuthStory("sessions", "Sessions and devices") { SessionsScreen(SessionsState(Resource.Ready(sessions, Freshness.network(0)), now), {}) },
    AuthStory("sessions.confirm", "Revoke confirmation") {
        SessionsScreen(SessionsState(Resource.Ready(sessions, Freshness.network(0)), now, confirmRevoke = "s3"), {})
    },
    AuthStory("sessions.loading", "Sessions loading") { SessionsScreen(SessionsState(Resource.Loading, now), {}) },
    AuthStory("sessions.failed", "Sessions failed") { SessionsScreen(SessionsState(Resource.Failed(refusal("error.500.internal", 500)), now), {}) },
    AuthStory("sign_out", "Sign-out confirmation") { SignOutConfirmDialog(SignOutConfirmState(), {}) },
    AuthStory("sign_out.guest", "Sign-out confirmation, guest") { SignOutConfirmDialog(SignOutConfirmState(isGuest = true), {}) },
    AuthStory("account_menu", "Account control") { AccountMenu("ann@example.com", isGuest = false, onSignOut = {}) },
)

private val sessions = listOf(
    SessionRowState("s1", "Desktop app", "macOS", "127.0.0.1", now - 1.minutes, isCurrent = true, isSuspicious = false, kind = SessionDeviceKind.Desktop),
    SessionRowState("s2", "iOS app", "iPhone", null, now - 3.hours, isCurrent = false, isSuspicious = true, kind = SessionDeviceKind.Phone),
    SessionRowState("s3", "Android app", "Pixel", null, now - 50.hours, isCurrent = false, isSuspicious = false, kind = SessionDeviceKind.Phone),
)
