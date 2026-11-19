package stapel.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import stapel.auth.StapelAuth
import stapel.auth.error.AuthError
import stapel.auth.model.AuthMethod
import stapel.auth.model.AuthMethodId
import stapel.auth.model.OtpChannel
import stapel.auth.model.SessionDevice
import stapel.auth.model.TotpProof
import stapel.auth.model.DeviceType
import stapel.auth.presentation.AuthEffect
import stapel.auth.presentation.ChallengeIntent
import stapel.auth.presentation.ChallengeStep
import stapel.auth.presentation.GuestIntent
import stapel.auth.presentation.GuestPresenter
import stapel.auth.presentation.GuestState
import stapel.auth.presentation.MethodChooserIntent
import stapel.auth.presentation.MethodChooserPresenter
import stapel.auth.presentation.MethodChooserState
import stapel.auth.presentation.OtpSignInIntent
import stapel.auth.presentation.OtpSignInPresenter
import stapel.auth.presentation.OtpSignInState
import stapel.auth.presentation.PasswordRegisterIntent
import stapel.auth.presentation.PasswordRegisterPresenter
import stapel.auth.presentation.PasswordRegisterState
import stapel.auth.presentation.PasswordRegistration
import stapel.auth.presentation.PasswordSignInIntent
import stapel.auth.presentation.PasswordSignInPresenter
import stapel.auth.presentation.PasswordSignInState
import stapel.auth.presentation.SessionsIntent
import stapel.auth.presentation.SessionsPresenter
import stapel.auth.presentation.SignOutIntent
import stapel.auth.presentation.SignOutPresenter
import stapel.auth.presentation.SignOutState
import stapel.auth.session.AuthState
import stapel.auth.session.SignOutReason
import stapel.mvi.Presenter
import stapel.mvi.PresenterContext
import stapel.mvi.compose.CollectEffects
import stapel.mvi.compose.LocalPresenterFactory
import stapel.mvi.compose.PresenterFactory
import stapel.mvi.compose.collectState
import stapel.mvi.compose.rememberPresenter
import stapel.result.state.map
import kotlin.reflect.KClass
import kotlin.time.Clock
import kotlin.time.Duration

/**
 * Creates this library's presenters for `rememberPresenter` from one [StapelAuth]; other types go
 * to [next] (the app's own factory, e.g. a Koin one). The auth bindings install it themselves, so an
 * app without a presenter factory needs no wiring.
 */
public class AuthPresenterFactory(private val auth: StapelAuth, private val next: PresenterFactory? = null) : PresenterFactory {
    /** The auth presenter of [type] over this factory's [StapelAuth], or [next]'s for any other type. */
    @Suppress("UNCHECKED_CAST")
    override fun <P : Presenter<*, *, *>> create(type: KClass<P>, context: PresenterContext, args: List<Any?>): P = when (type) {
        MethodChooserPresenter::class -> MethodChooserPresenter(auth, context)
        OtpSignInPresenter::class -> OtpSignInPresenter(auth, context)
        PasswordSignInPresenter::class -> PasswordSignInPresenter(auth, context)
        PasswordRegisterPresenter::class -> PasswordRegisterPresenter(auth, context)
        GuestPresenter::class -> GuestPresenter(auth, context)
        SignOutPresenter::class -> SignOutPresenter(auth, context)
        SessionsPresenter::class -> SessionsPresenter(auth, context)
        else -> next?.create(type, context, args) ?: error("AuthPresenterFactory cannot create ${type.simpleName}")
    } as P
}

/** Provides an [AuthPresenterFactory] over [auth] (chained to the factory already provided) to [content]. */
@Composable
public fun ProvideAuthPresenters(auth: StapelAuth, content: @Composable () -> Unit) {
    val outer = LocalPresenterFactory.current
    val factory = remember(auth, outer) { AuthPresenterFactory(auth, outer) }
    CompositionLocalProvider(LocalPresenterFactory provides factory, content = content)
}

/** The notice the sign-in screen shows for how the last session ended. */
public fun SignOutReason.toNotice(): AuthNotice = when (this) {
    SignOutReason.UserRequested -> AuthNotice.SignedOut
    SignOutReason.Revoked -> AuthNotice.SignedOutElsewhere
    SignOutReason.AccountDisabled -> AuthNotice.AccountDisabled
    SignOutReason.Expired, SignOutReason.StorageLost, SignOutReason.Other -> AuthNotice.SessionExpired
}

internal fun AuthMethodId.toSignInMethod(): SignInMethod? = when (this) {
    AuthMethodId.Email -> SignInMethod.Email
    AuthMethodId.Phone -> SignInMethod.Phone
    AuthMethodId.Password -> SignInMethod.Password
    else -> null
}

private fun List<AuthMethod>.methods(): List<SignInMethod> = mapNotNull { it.id.toSignInMethod() }.distinct()

private fun SignInMethod.channel(): OtpChannel = if (this == SignInMethod.Phone) OtpChannel.Phone else OtpChannel.Email

private fun OtpChannel.method(): SignInMethod = if (this == OtpChannel.Phone) SignInMethod.Phone else SignInMethod.Email

internal fun ChallengeStep.toStep(): SignInStep = when (this) {
    is ChallengeStep.Totp -> SignInStep.Totp(TotpChallengeState(submitting = submitting, error = error))
    is ChallengeStep.ForcedPasswordChange -> SignInStep.ForcedPasswordChange(ForcedPasswordChangeState(submitting, error))
    is ChallengeStep.MfaEnrollRequired -> SignInStep.MfaEnrollRequired
}

internal fun OtpSignInState.toStep(method: SignInMethod, now: kotlin.time.Instant): SignInStep = when (this) {
    is OtpSignInState.Idle -> SignInStep.Form(identifier = identifier.takeIf { channel.method() == method }.orEmpty())
    is OtpSignInState.Requesting -> SignInStep.Form(busy = true, identifier = identifier)
    is OtpSignInState.RequestFailed -> SignInStep.Form(error = error, fieldErrors = fieldOf(error), identifier = identifier)
    is OtpSignInState.CodeSent -> SignInStep.Code(OtpCodeState(channel.method(), maskedTarget, codeLength, resendIn, isMock, verifying, error))
    is OtpSignInState.Locked -> SignInStep.Locked(LockedState(error, until?.let { (it - now).coerceAtLeast(Duration.ZERO) } ?: error.retryAfter))
    is OtpSignInState.Challenge -> step.toStep()
    is OtpSignInState.Done -> SignInStep.Done
}

internal fun PasswordSignInState.toStep(): SignInStep = when (this) {
    PasswordSignInState.Idle -> SignInStep.Form()
    is PasswordSignInState.Authenticating -> SignInStep.Form(busy = true, identifier = login)
    is PasswordSignInState.Failed -> SignInStep.Form(error = error, identifier = login)
    is PasswordSignInState.Challenge -> step.toStep()
    is PasswordSignInState.Done -> SignInStep.Done
}

internal fun PasswordRegisterState.toStep(): SignInStep = when (this) {
    PasswordRegisterState.Checking, PasswordRegisterState.Unavailable, PasswordRegisterState.Idle -> SignInStep.Form()
    PasswordRegisterState.Registering -> SignInStep.Form(busy = true)
    is PasswordRegisterState.Failed -> fieldOf(error).let { f -> SignInStep.Form(error = error.takeIf { f.isEmpty() }, fieldErrors = f) }
    is PasswordRegisterState.Done -> SignInStep.Done
}

private fun fieldOf(error: AuthError): Map<String, stapel.result.StapelError> = error.field?.let { mapOf(it to error) }.orEmpty()

/**
 * The sign-in screen bound to the domain presenters: method chooser over the capabilities, the OTP,
 * password and registration flows, guest entry, and the notice for how the last session ended.
 * [onSignedIn] runs once a session (or a guest session) exists.
 */
@Composable
public fun AuthSignIn(
    auth: StapelAuth,
    onSignedIn: () -> Unit,
    slots: AuthSlots = AuthSlots(),
    clock: Clock = auth.config.clock,
) {
    ProvideAuthPresenters(auth) { BoundSignIn(auth, onSignedIn, slots, clock) }
}

@Composable
private fun BoundSignIn(auth: StapelAuth, onSignedIn: () -> Unit, slots: AuthSlots, clock: Clock) {
    val chooser = rememberPresenter<MethodChooserPresenter>()
    val otp = rememberPresenter<OtpSignInPresenter>()
    val password = rememberPresenter<PasswordSignInPresenter>()
    val register = rememberPresenter<PasswordRegisterPresenter>()
    val guest = rememberPresenter<GuestPresenter>()
    val chooserState by chooser.collectState()
    val otpState by otp.collectState()
    val passwordState by password.collectState()
    val registerState by register.collectState()
    val guestState by guest.collectState()
    val authState by auth.session.state.collectAsState()
    var selected by rememberSaveable { mutableStateOf<SignInMethod?>(null) }
    var variant by rememberSaveable { mutableStateOf(SignInVariant.Login) }
    var noticeDismissed by rememberSaveable { mutableStateOf(false) }

    val signedIn: suspend (AuthEffect) -> Unit = { effect -> if (effect is AuthEffect.SignedIn) onSignedIn() }
    otp.CollectEffects(signedIn)
    password.CollectEffects(signedIn)
    register.CollectEffects(signedIn)
    LaunchedEffect(guestState) { if (guestState is GuestState.Done) onSignedIn() }

    val ready = chooserState as? MethodChooserState.Ready
    val loginMethods = ready?.zones?.let { MethodZones(it.main.methods(), it.bottom.methods(), it.overflow.methods()) } ?: MethodZones(emptyList())
    val registerMethods = ready?.zones?.let { z ->
        val codes = z.all.filter { it.canRegister && (it.id == AuthMethodId.Email || it.id == AuthMethodId.Phone) }.methods()
        MethodZones(codes + listOfNotNull(SignInMethod.Password.takeIf { z.passwordRegistration }))
    } ?: MethodZones(emptyList())
    val zones = if (variant == SignInVariant.Register) registerMethods else loginMethods
    val base = SignInState(zones, selected = selected, variant = variant)
    val active = base.activeMethod
    val step = when {
        active == null -> SignInStep.Form()
        active == SignInMethod.Password && variant == SignInVariant.Register -> registerState.toStep()
        active == SignInMethod.Password -> passwordState.toStep()
        else -> {
            val otpMethod = when (val s = otpState) {
                is OtpSignInState.Idle -> active
                is OtpSignInState.Requesting -> s.channel.method()
                is OtpSignInState.RequestFailed -> s.channel.method()
                is OtpSignInState.CodeSent -> s.channel.method()
                is OtpSignInState.Locked -> s.channel.method()
                else -> active
            }
            if (otpMethod == active) otpState.toStep(active, clock.now()) else SignInStep.Form()
        }
    }
    val notice = (authState as? AuthState.SignedOut)?.reason?.toNotice()?.takeUnless { noticeDismissed }
    val state = base.copy(
        canRegister = registerMethods.all.isNotEmpty(),
        guest = GuestEntry(guestState == GuestState.Creating, (guestState as? GuestState.Failed)?.error)
            .takeIf { ready?.zones?.guestAvailable == true && authState !is AuthState.Guest },
        notice = notice,
        loading = chooserState == MethodChooserState.Loading,
        loadError = (chooserState as? MethodChooserState.Failed)?.error,
        step = step,
    )
    val onEvent: (SignInEvent) -> Unit = { event ->
        when (event) {
            is SignInEvent.SelectMethod -> selected = event.method
            is SignInEvent.SwitchVariant -> {
                variant = event.variant
                selected = null
            }
            is SignInEvent.RequestCode -> otp.dispatch(OtpSignInIntent.RequestCode(event.method.channel(), event.identifier))
            is SignInEvent.SubmitCode -> otp.dispatch(OtpSignInIntent.SubmitCode(event.code))
            SignInEvent.Resend -> otp.dispatch(OtpSignInIntent.Resend)
            SignInEvent.EditIdentifier -> otp.dispatch(OtpSignInIntent.EditIdentifier)
            is SignInEvent.SignInWithPassword -> password.dispatch(PasswordSignInIntent.SignIn(event.login, event.password))
            is SignInEvent.RegisterWithPassword ->
                register.dispatch(PasswordRegisterIntent.Register(PasswordRegistration(password = event.password, email = event.email)))
            is SignInEvent.SubmitTotp -> {
                val intent = ChallengeIntent.SubmitTotp(if (event.backup) TotpProof.BackupCode(event.code) else TotpProof.Code(event.code))
                if (active == SignInMethod.Password) password.dispatch(intent) else otp.dispatch(intent)
            }
            is SignInEvent.SubmitNewPassword -> {
                val intent = ChallengeIntent.SubmitNewPassword(event.password)
                if (active == SignInMethod.Password) password.dispatch(intent) else otp.dispatch(intent)
            }
            SignInEvent.BackToForm ->
                if (active == SignInMethod.Password) password.dispatch(PasswordSignInIntent.Reset) else otp.dispatch(OtpSignInIntent.EditIdentifier)
            SignInEvent.ContinueAsGuest -> guest.dispatch(GuestIntent.ContinueAsGuest)
            SignInEvent.DismissNotice -> noticeDismissed = true
            SignInEvent.Retry -> chooser.dispatch(MethodChooserIntent.Refresh)
        }
    }
    LocalAuthScreens.current.SignIn(state, onEvent, slots)
}

internal fun SessionDevice.toRow(fallbackTime: kotlin.time.Instant): SessionRowState = SessionRowState(
    id = id,
    name = name,
    details = details,
    ipAddress = ipAddress,
    lastUsedAt = lastUsedAt ?: createdAt ?: fallbackTime,
    isCurrent = isCurrent,
    isSuspicious = isSuspicious,
    kind = when (type) {
        DeviceType.Phone -> SessionDeviceKind.Phone
        DeviceType.Tablet -> SessionDeviceKind.Tablet
        DeviceType.Desktop -> SessionDeviceKind.Desktop
        DeviceType.Api -> SessionDeviceKind.Api
        else -> SessionDeviceKind.Unknown
    },
)

/**
 * The sessions screen bound to the sessions presenter: list, revoke one (confirmed), "this was me" on
 * a suspicious session, sign out every other device (confirmed), refresh.
 */
@Composable
public fun AuthSessions(auth: StapelAuth, clock: Clock = auth.config.clock) {
    ProvideAuthPresenters(auth) {
        val presenter = rememberPresenter<SessionsPresenter>()
        val s by presenter.collectState()
        var confirm by rememberSaveable { mutableStateOf<String?>(null) }
        var confirmOthers by rememberSaveable { mutableStateOf(false) }
        var errorDismissed by remember(s.error) { mutableStateOf(false) }
        val now = clock.now()
        val state = SessionsState(
            sessions = s.sessions.map { list -> list.map { it.toRow(now) } },
            now = now,
            busy = s.revoking + s.confirming,
            confirmRevoke = confirm,
            confirmRevokeOthers = confirmOthers,
            revokingOthers = s.revokingOthers,
            actionError = s.error?.takeUnless { errorDismissed },
        )
        LocalAuthScreens.current.Sessions(state) { event ->
            when (event) {
                is SessionsEvent.AskRevoke -> confirm = event.id
                SessionsEvent.AskRevokeOthers -> confirmOthers = true
                SessionsEvent.ConfirmRevoke -> {
                    confirm?.let { presenter.dispatch(SessionsIntent.Revoke(it)) } ?: presenter.dispatch(SessionsIntent.RevokeOthers)
                    confirm = null
                    confirmOthers = false
                }
                SessionsEvent.CancelRevoke -> {
                    confirm = null
                    confirmOthers = false
                }
                is SessionsEvent.ConfirmMine -> presenter.dispatch(SessionsIntent.ConfirmMine(event.id))
                SessionsEvent.Refresh -> presenter.dispatch(SessionsIntent.Refresh)
                SessionsEvent.DismissError -> errorDismissed = true
            }
        }
    }
}

/** The account control bound to the session and the sign-out presenter. Draws nothing without a session. */
@Composable
public fun AuthAccountMenu(auth: StapelAuth, modifier: Modifier = Modifier, onOpen: (() -> Unit)? = null) {
    ProvideAuthPresenters(auth) {
        val presenter = rememberPresenter<SignOutPresenter>()
        val signOut by presenter.collectState()
        val state by auth.session.state.collectAsState()
        val user = when (val s = state) {
            is AuthState.SignedIn -> s.user
            is AuthState.Guest -> s.user
            else -> return@ProvideAuthPresenters
        }
        val identity = user?.let { it.email ?: it.phone ?: it.username }.orEmpty()
        AccountMenu(
            identity = identity,
            isGuest = state is AuthState.Guest,
            onSignOut = { presenter.dispatch(SignOutIntent.SignOut) },
            modifier = modifier,
            onOpen = onOpen,
            signingOut = signOut is SignOutState.SigningOut,
        )
    }
}

/** The guest wall bound to the sign-in screen's two doors. */
@Composable
public fun AuthGuestWall(onSignIn: () -> Unit, onRegister: () -> Unit, onNotNow: (() -> Unit)?) {
    LocalAuthScreens.current.GuestUpgrade(GuestUpgradeState(canDismiss = onNotNow != null)) { event ->
        when (event) {
            GuestUpgradeEvent.SignIn -> onSignIn()
            GuestUpgradeEvent.Register -> onRegister()
            GuestUpgradeEvent.NotNow -> onNotNow?.invoke()
        }
    }
}

/** Signs out from code (a "sign out" button of the app's own); never throws. */
@Composable
public fun rememberSignOut(auth: StapelAuth): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(auth, scope) { { scope.launch { auth.session.logout() } } }
}
