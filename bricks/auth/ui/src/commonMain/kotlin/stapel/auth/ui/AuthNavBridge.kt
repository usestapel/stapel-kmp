package stapel.auth.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import stapel.auth.StapelAuth
import stapel.auth.session.AuthState
import stapel.auth.session.SignOutReason
import stapel.navigation.NavAuthSource
import stapel.navigation.NavAuthState
import stapel.navigation.NavCommand
import stapel.navigation.Route
import stapel.navigation.StapelNavigator

/** The navigation image of an auth state. */
public fun AuthState.toNavAuthState(): NavAuthState = when (this) {
    AuthState.Restoring -> NavAuthState.Unresolved
    is AuthState.SignedOut -> NavAuthState.Anonymous
    is AuthState.Guest -> NavAuthState.Guest
    is AuthState.SignedIn -> NavAuthState.Member
}

/**
 * Adapts the auth session to navigation ([NavAuthSource]) and brings the user back after an
 * involuntary sign-out: when a session ends for any reason but the user's own (expired, revoked,
 * disabled), the screens that were open are remembered before navigation resets to sign-in, and are
 * reopened (through the guard) as soon as a session exists again. A voluntary sign-out forgets them.
 * No step of the sign-in destination's flow is ever remembered.
 *
 * Navigation's reducer resets the stack on every sign-out (it cannot tell the reasons apart); this
 * bridge is where the reason is known.
 */
public class AuthNavBridge(private val auth: StapelAuth, private val scope: CoroutineScope) : NavAuthSource {
    private val nav = MutableStateFlow(auth.session.state.value.toNavAuthState())
    private var navigator: StapelNavigator? = null
    private val pending = MutableStateFlow<List<Route>?>(null)

    /** The screens to reopen after the next sign-in, if any (for tests and diagnostics). */
    public val returnTo: StateFlow<List<Route>?> = pending.asStateFlow()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            auth.session.state.collect { s ->
                if (s is AuthState.SignedOut) {
                    val reason = s.reason
                    pending.value = if (reason != null && reason != SignOutReason.UserRequested) openScreens() ?: pending.value else null
                }
                nav.value = s.toNavAuthState()
            }
        }
    }

    /** The navigation image of the session state, set after the open screens were remembered. */
    override fun authState(): StateFlow<NavAuthState> = nav.asStateFlow()

    /** Connects the app's navigator (call once, after creating it). */
    public fun attach(navigator: StapelNavigator) {
        this.navigator = navigator
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            navigator.state.collect { st ->
                val routes = pending.value
                if (routes != null && (st.auth == NavAuthState.Member || st.auth == NavAuthState.Guest)) {
                    pending.value = null
                    navigator.dispatch(NavCommand.OpenStack(routes))
                }
            }
        }
    }

    private fun openScreens(): List<Route>? {
        val n = navigator ?: return null
        val signIn = n.policy.signIn
        val signInFlow = signIn?.let { n.nav.byId[it]?.spec?.flow }
        // Every step of the sign-in flow is left out, not only its first screen: the navigator removes the
        // whole flow on sign-in, and restoring a step would reopen sign-in for a signed-in user.
        val routes = n.state.value.visible.map { it.route }.filter { route ->
            val dest = n.nav.destinationOf(route)
            dest?.id != signIn && (signInFlow == null || dest?.spec?.flow != signInFlow)
        }
        return routes.takeIf { it.isNotEmpty() }
    }
}
