package stapel.auth.presentation

import stapel.auth.StapelAuth
import stapel.auth.api.model.PasswordLogin
import stapel.auth.api.model.PasswordRegister
import stapel.auth.error.AuthError
import stapel.auth.model.LoginChallenge
import stapel.auth.model.SignInOutcome
import stapel.auth.session.Completion
import stapel.mvi.PresenterContext
import stapel.result.StapelResult

/** State of a password sign-in (`auth.password_login`, `auth.first_login`). */
public sealed interface PasswordSignInState {
    /** Waiting for credentials. */
    public data object Idle : PasswordSignInState

    /** Checking [login]. */
    public data class Authenticating(
        /** The login being checked. */
        val login: String,
    ) : PasswordSignInState

    /** Refused: invalid credentials, account locked, SSO required, network. */
    public data class Failed(
        /** The login that was refused. */
        val login: String,
        /** Why. */
        val error: AuthError,
    ) : PasswordSignInState

    /** A second factor or first-login step is required. */
    public data class Challenge(
        /** The step. */
        val step: ChallengeStep,
    ) : PasswordSignInState

    /** Signed in. */
    public data class Done(
        /** The session. */
        val outcome: SignInOutcome.Session,
    ) : PasswordSignInState
}

/** Intents of [PasswordSignInPresenter]. */
public sealed interface PasswordSignInIntent {
    /** Signs in with [login] (username, e-mail, phone or `org/username`) and [password]. */
    public data class SignIn(
        /** Login identifier. */
        val login: String,
        /** Password. Never kept in state. */
        val password: String,
    ) : PasswordSignInIntent

    /** Back to [PasswordSignInState.Idle]. */
    public data object Reset : PasswordSignInIntent
}

/** Password sign-in. Passwords never enter state or saved state. */
public class PasswordSignInPresenter(auth: StapelAuth, context: PresenterContext) :
    SignInPresenter<PasswordSignInState, PasswordSignInIntent>(PasswordSignInState.Idle, context, auth) {

    override suspend fun handle(intent: PasswordSignInIntent) {
        when (intent) {
            is PasswordSignInIntent.SignIn -> {
                if (state.value is PasswordSignInState.Authenticating) return
                val login = intent.login.trim()
                reduce { PasswordSignInState.Authenticating(login) }
                launch(KEY_WORK) {
                    when (val r = auth.api.passwordLogin(PasswordLogin(login = login, password = intent.password))) {
                        is StapelResult.Ok -> settle(auth.sessionImpl.complete(r.value)) { PasswordSignInState.Failed(login, it) }
                        is StapelResult.Err -> reduce { PasswordSignInState.Failed(login, AuthError.of(r.error)) }
                    }
                }
            }
            PasswordSignInIntent.Reset -> {
                launch(KEY_WORK) {}
                reduce { PasswordSignInState.Idle }
            }
            is ChallengeIntent -> handleChallenge(intent)
        }
    }

    override fun challengeState(step: ChallengeStep): PasswordSignInState = PasswordSignInState.Challenge(step)

    override fun stepOf(state: PasswordSignInState): ChallengeStep? = (state as? PasswordSignInState.Challenge)?.step

    override fun doneState(outcome: SignInOutcome.Session): PasswordSignInState = PasswordSignInState.Done(outcome)
}

/** What a password registration sends. */
public data class PasswordRegistration(
    /** The password. */
    val password: String,
    /** E-mail (one of e-mail / phone is required by the server). */
    val email: String? = null,
    /** Phone in E.164. */
    val phone: String? = null,
    /** Optional username. */
    val username: String? = null,
) {
    /** Redacted: the password is never printed. */
    override fun toString(): String = "PasswordRegistration(email=$email, phone=$phone, username=$username)"
}

/** State of password registration. */
public sealed interface PasswordRegisterState {
    /** Reading whether the deployment allows password sign-up. */
    public data object Checking : PasswordRegisterState

    /** The deployment does not offer password sign-up (posture / configuration); nothing to submit. */
    public data object Unavailable : PasswordRegisterState

    /** Ready for input. */
    public data object Idle : PasswordRegisterState

    /** Submitting. */
    public data object Registering : PasswordRegisterState

    /** Refused; [AuthError.field] names the field for input errors. */
    public data class Failed(
        /** Why. */
        val error: AuthError,
    ) : PasswordRegisterState

    /** Registered and signed in. */
    public data class Done(
        /** The session. */
        val outcome: SignInOutcome.Session,
    ) : PasswordRegisterState
}

/** Intents of [PasswordRegisterPresenter]. */
public sealed interface PasswordRegisterIntent {
    /** Registers. */
    public data class Register(
        /** What to send. */
        val registration: PasswordRegistration,
    ) : PasswordRegisterIntent
}

/**
 * Password registration, offered only when `capabilities.registration.password` is true (the
 * `public` posture keeps it closed). Starts in [PasswordRegisterState.Checking].
 */
public class PasswordRegisterPresenter(private val auth: StapelAuth, context: PresenterContext) :
    stapel.mvi.Presenter<PasswordRegisterState, PasswordRegisterIntent, AuthEffect>(PasswordRegisterState.Checking, context) {

    init {
        launch {
            val open = (auth.capabilities.get() as? StapelResult.Ok)?.value?.passwordRegistration ?: false
            reduce { if (this == PasswordRegisterState.Checking) (if (open) PasswordRegisterState.Idle else PasswordRegisterState.Unavailable) else this }
        }
    }

    override suspend fun handle(intent: PasswordRegisterIntent) {
        when (intent) {
            is PasswordRegisterIntent.Register -> {
                val current = state.value
                if (current != PasswordRegisterState.Idle && current !is PasswordRegisterState.Failed) return
                reduce { PasswordRegisterState.Registering }
                val r = intent.registration
                launch("register") {
                    val answer = auth.api.passwordRegister(
                        PasswordRegister(password = r.password, email = r.email, phone = r.phone, username = r.username),
                    )
                    when (answer) {
                        is StapelResult.Ok -> when (val c = auth.sessionImpl.sessionOrRefusal(answer.value)) {
                            is Completion.Refused -> reduce { PasswordRegisterState.Failed(c.error) }
                            is Completion.Done -> {
                                val outcome = c.outcome as SignInOutcome.Session
                                reduce { PasswordRegisterState.Done(outcome) }
                                emit(AuthEffect.SignedIn(outcome))
                            }
                        }
                        is StapelResult.Err -> reduce { PasswordRegisterState.Failed(AuthError.of(answer.error)) }
                    }
                }
            }
        }
    }
}

/** State of a challenge reached outside a sign-in screen (a deep link, a resumed flow). */
public sealed interface LoginChallengeState {
    /** The step being answered. */
    public data class Challenge(
        /** The step. */
        val step: ChallengeStep,
    ) : LoginChallengeState

    /** Signed in. */
    public data class Done(
        /** The session. */
        val outcome: SignInOutcome.Session,
    ) : LoginChallengeState
}

/** Intents of [LoginChallengePresenter]: only the [ChallengeIntent]s. */
public sealed interface LoginChallengeIntent

/**
 * The TOTP challenge / forced password change on its own, for a challenge that arrives without its
 * sign-in screen (e.g. `/login?challenge_token=…`). Sign-in presenters embed the same tail.
 */
public class LoginChallengePresenter(
    auth: StapelAuth,
    context: PresenterContext,
    initial: SignInOutcome,
) : SignInPresenter<LoginChallengeState, LoginChallengeIntent>(
    when (initial) {
        is SignInOutcome.Session -> LoginChallengeState.Done(initial)
        else -> LoginChallengeState.Challenge(initial.toStepOrNull()!!)
    },
    context,
    auth,
) {
    override suspend fun handle(intent: LoginChallengeIntent) {
        when (intent) {
            is ChallengeIntent -> handleChallenge(intent)
        }
    }

    override fun challengeState(step: ChallengeStep): LoginChallengeState = LoginChallengeState.Challenge(step)

    override fun stepOf(state: LoginChallengeState): ChallengeStep? = (state as? LoginChallengeState.Challenge)?.step

    override fun doneState(outcome: SignInOutcome.Session): LoginChallengeState = LoginChallengeState.Done(outcome)

    /** Builders. */
    public companion object {
        /** A TOTP challenge from a deep link's token. */
        public fun totp(challenge: LoginChallenge): SignInOutcome = SignInOutcome.TotpRequired(challenge)
    }
}
