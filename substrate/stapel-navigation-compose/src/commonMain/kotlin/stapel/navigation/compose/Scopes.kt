package stapel.navigation.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import stapel.navigation.EmbedSpec
import stapel.navigation.EmbedSurface
import stapel.navigation.NavAuthState
import stapel.navigation.Navigator
import stapel.navigation.ResolvedDestination
import stapel.navigation.ResultKey
import stapel.navigation.Route
import stapel.navigation.SlotId
import stapel.navigation.StapelNavigator
import stapel.navigation.StackEntry

/** What a destination's content can reach. */
@Stable
public interface ScreenScope {
    /** The app's navigator. */
    public val navigator: Navigator

    /** This destination after resolution (path, labels, parent). */
    public val destination: ResolvedDestination

    /** The stack entry being drawn; [StackEntry.uid] identifies this instance. */
    public val entry: StackEntry

    /** Renders the embeds other libraries placed into page slot [slot] of this destination. */
    @Composable
    public fun PageSlot(slot: SlotId, modifier: Modifier)
}

/** What an embed's content can reach. */
@Stable
public interface EmbedScope {
    /** The app's navigator. */
    public val navigator: Navigator

    /** The embed's declaration. */
    public val spec: EmbedSpec

    /** The embed's route parameters, resolved from the page it sits on (empty for chrome slots). */
    public val params: Map<String, String>

    /** Navigates to the destination the embed declares in `opens`; does nothing without one. */
    public fun open()
}

/** The app's [Navigator]; provided by [StapelNavHost]. */
public val LocalNavigator: ProvidableCompositionLocal<Navigator> = staticCompositionLocalOf {
    error("No Navigator: content must be inside StapelNavHost (or provide LocalNavigator in a test).")
}

/**
 * Renders the embeds contributed to the chrome slot [slot] that the current auth state may see,
 * in (order, id) order, one after another inside a `Box` each. [filter] narrows further (the shell
 * filters by width class).
 */
@Composable
public fun EmbedSlot(
    slot: SlotId,
    navigator: StapelNavigator,
    contributions: List<NavContribution>,
    modifier: Modifier = Modifier,
    filter: (EmbedSpec) -> Boolean = { true },
) {
    val state by navigator.state.collectAsState()
    val contents = remember(contributions) { contributions.flatMap { it.embedContents.entries }.associate { it.key to it.value } }
    for (spec in navigator.nav.embeds) {
        if (spec.slot != slot || spec.scope != null || !filter(spec) || !visibleTo(spec, state.auth)) continue
        val content = contents[spec.id] ?: continue
        Box(modifier) { EmbedScopeImpl(navigator, spec, emptyMap()).content() }
    }
}

internal fun visibleTo(spec: EmbedSpec, auth: NavAuthState): Boolean = when (spec.surface) {
    EmbedSurface.Any -> true
    EmbedSurface.Member -> auth == NavAuthState.Member
    EmbedSurface.Public -> auth != NavAuthState.Member
}

internal class EmbedScopeImpl(
    override val navigator: Navigator,
    override val spec: EmbedSpec,
    override val params: Map<String, String>,
) : EmbedScope {
    override fun open() {
        val target = spec.opens ?: return
        val nav = (navigator as? StapelNavigator)?.nav ?: return
        val route: Route = nav.defaultRoute(target) ?: return
        navigator.navigate(route)
    }
}

/**
 * Calls [onResult] for every result delivered under [key] to this destination (see
 * `Navigator.backWithResult`). A result is delivered once, even if it arrived while this
 * destination was not composed.
 */
@Composable
public fun <T : Any> ResultEffect(key: ResultKey<T>, onResult: (T) -> Unit) {
    val navigator = LocalNavigator.current
    val handler by rememberUpdatedState(onResult)
    LaunchedEffect(navigator, key.name) {
        navigator.results(key).collect { handler(it) }
    }
}

/**
 * While [enabled], the platform back action (Android back, iOS edge swipe, desktop Esc) calls
 * [onBack] instead of popping the stack — for a multi-step screen drawn in one destination.
 */
@Composable
public fun NavBackInterceptor(enabled: Boolean, onBack: () -> Unit) {
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(state = state, isBackEnabled = enabled, onBackCompleted = onBack)
}
