package stapel.navigation

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * What navigation knows about the user. [Unresolved] is a state of its own: a guard never answers
 * "you may not" for "we could not ask yet".
 */
@Serializable
public enum class NavAuthState {
    /** The session is still being restored. */
    Unresolved,

    /** No session. */
    Anonymous,

    /** A session without a membership mandate (a guest account). */
    Guest,

    /** A member. */
    Member,
}

/** Source of [NavAuthState]; the app adapts its session to it, so navigation knows no auth library. */
public fun interface NavAuthSource {
    /** Current state, updated on every change. */
    public fun authState(): StateFlow<NavAuthState>
}

/** What happens when a signed-out user opens a destination that needs a session. */
public enum class UnauthenticatedMode {
    /** Push the sign-in destination; the target is opened after sign-in. */
    Redirect,

    /** Show sign-in as a sheet over the current screen, which stays intact underneath. */
    Wall,
}

/**
 * App-level navigation policy.
 *
 * @property unauthenticated redirect or wall when a session is needed.
 * @property signIn the sign-in destination named by the app; null = the app has no sign-in screen.
 * @property home where a completed sign-in lands without a return target; null = the first tab.
 * @property maxTabs how many tabs the phone bar shows before the rest move to "More".
 */
public class NavPolicy(
    public val signIn: DestinationId?,
    public val unauthenticated: UnauthenticatedMode = UnauthenticatedMode.Redirect,
    public val home: DestinationId? = null,
    public val maxTabs: Int = 5,
)

/** Verdict of a [RouteGuard]. */
public sealed interface GuardDecision {
    /** Open it. */
    public data object Allow : GuardDecision

    /** Auth is unresolved: keep the target and show the wait screen until it resolves. */
    public data object Wait : GuardDecision

    /**
     * Sign in first; [returnTo] is opened afterwards (the reducer fills it with the intended stack).
     *
     * @property mode redirect or wall.
     * @property returnTo the stack to open after sign-in.
     */
    public data class SignIn(public val mode: UnauthenticatedMode, public val returnTo: List<Route>) : GuardDecision

    /**
     * Refused for a reason the shell names.
     *
     * @property reasonKey i18n key of the reason.
     */
    public data class Deny(public val reasonKey: String) : GuardDecision
}

/** Decides whether a destination may be opened. */
public fun interface RouteGuard {
    /** Verdict for opening [target] as [auth] under [policy]. */
    public fun check(target: ResolvedDestination, auth: NavAuthState, policy: NavPolicy): GuardDecision
}

/** Reason key when a guest opens a member destination. */
public const val REASON_NO_MANDATE: String = "nav.no_mandate"

/** Reason key when a destination needs a session but the app has no sign-in destination. */
public const val REASON_SIGN_IN_UNAVAILABLE: String = "nav.sign_in_unavailable"

/** Reason key when no installed library declares the route's type. */
public const val REASON_UNKNOWN_DESTINATION: String = "nav.unknown_destination"

/** Reason key when [NavCommand.SelectTab] names something that is not a tab for the current user. */
public const val REASON_NOT_A_TAB: String = "nav.not_a_tab"

/**
 * The default guard: surface × requiresAuth, the two axes kept independent (the web
 * `navSurfaceVisibleTo` rule).
 *
 * | target | Anonymous | Guest | Member | Unresolved |
 * |---|---|---|---|---|
 * | Public, requiresAuth=false | Allow | Allow | Allow | Allow |
 * | Public, requiresAuth=true | SignIn | Allow | Allow | Wait |
 * | Member | SignIn | Deny(no mandate) | Allow | Wait |
 *
 * SignIn becomes Deny([REASON_SIGN_IN_UNAVAILABLE]) when the policy names no sign-in destination.
 */
public val DefaultRouteGuard: RouteGuard = RouteGuard { target, auth, policy ->
    val signIn: GuardDecision = if (policy.signIn == null) {
        GuardDecision.Deny(REASON_SIGN_IN_UNAVAILABLE)
    } else {
        GuardDecision.SignIn(policy.unauthenticated, emptyList())
    }
    when {
        target.surface == Surface.Public && !target.requiresAuth -> GuardDecision.Allow
        auth == NavAuthState.Unresolved -> GuardDecision.Wait
        auth == NavAuthState.Anonymous -> signIn
        target.surface == Surface.Public -> GuardDecision.Allow
        auth == NavAuthState.Guest -> GuardDecision.Deny(REASON_NO_MANDATE)
        else -> GuardDecision.Allow
    }
}
