package stapel.navigation.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import stapel.navigation.CapabilityKey
import stapel.navigation.DeepLinkConfig
import stapel.navigation.DeepLinkResolver
import stapel.navigation.ExternalUris
import stapel.navigation.NavAuthSource
import stapel.navigation.NavOverrides
import stapel.navigation.NavPolicy
import stapel.navigation.NavStateCodec
import stapel.navigation.Presentation
import stapel.navigation.ResolvedDestination
import stapel.navigation.StapelNavigator
import stapel.navigation.WidthClass
import stapel.navigation.compose.engine.NavEngine
import stapel.navigation.resolveNav

/**
 * Draws a modal overlay ([Presentation.Sheet]) around `content`; `onDismiss` pops it. The shell
 * supplies a skinned one; [DefaultOverlay] is a Material-free fallback.
 */
public typealias OverlayHost = @Composable (presentation: Presentation, onDismiss: () -> Unit, content: @Composable () -> Unit) -> Unit

/** A scrim and the content aligned to the bottom: the unskinned sheet. Tapping the scrim dismisses it. */
public val DefaultOverlay: OverlayHost = { _, onDismiss, content ->
    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
            .semantics { onClick(label = "Dismiss") { onDismiss(); true } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Taps on the sheet itself must not reach the scrim.
        Box(Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }) { content() }
    }
}

/**
 * Creates the app's navigator from the installed [contributions], saved and restored with the
 * process (the state is encoded with the contributions' route serializers). With [deepLinks], links
 * offered to `ExternalUris` are opened.
 */
@Composable
public fun rememberStapelNavigator(
    contributions: List<NavContribution>,
    policy: NavPolicy,
    auth: NavAuthSource,
    deepLinks: DeepLinkConfig? = null,
    overrides: NavOverrides = NavOverrides(),
): StapelNavigator {
    // Keyed by the overrides' content: a caller recomposing with an equal (new) NavOverrides, like the
    // default argument, must not rebuild the navigation and reset the navigator.
    val nav = remember(contributions, overrides.byId) { resolveNav(contributions.map { it.manifest }, overrides) }
    val codec = remember(nav) { NavStateCodec(nav.manifests) }
    val links = remember(nav, deepLinks) { deepLinks?.let { DeepLinkResolver(nav, it) } }
    val scope = rememberCoroutineScope()
    val navigator = rememberSaveable(
        nav, policy, links,
        saver = Saver<StapelNavigator, String>(
            save = { codec.encode(it.state.value) },
            restore = { StapelNavigator(nav, policy, auth, scope, links, initial = codec.decode(it)) },
        ),
    ) { StapelNavigator(nav, policy, auth, scope, links) }
    if (links != null) {
        LaunchedEffect(navigator) { ExternalUris.incoming.collect { navigator.open(it) } }
    }
    return navigator
}

/**
 * Renders the visible stack of [navigator] with the screens of [contributions].
 *
 * Every entry gets its own saveable state and `ViewModelStoreOwner` (destinations that share a
 * `FlowId` share one), kept while the entry is on any stack — tab switches keep them — and cleared
 * when it leaves the last one. Back from the platform (Android back, iOS edge swipe, desktop Esc)
 * pops through the navigator.
 *
 * @param capabilities platform capabilities present; a destination requiring others shows [unavailable].
 * @param unavailable names a destination that cannot be shown and why (never a blank screen).
 * @param waiting shown while the navigation state is empty (auth unresolved at start).
 * @param overlay draws sheets.
 * @param section draws a container root (`account.root`, `admin.root`): by default its children as
 *   plain rows; the shell supplies a skinned section screen.
 * @param width the window's width class (the shell passes the token breakpoint's). From
 *   [WidthClass.Tablet] up, a `PaneRole.List` destination is drawn beside the `PaneRole.Detail`
 *   destination opened from it; on a phone each is a full screen. The stack is the same either way,
 *   so back, deep links and entry state behave alike and survive a width change.
 * @param detailPlaceholder fills the detail pane while a list is shown without a detail (wide only).
 */
@Composable
public fun StapelNavHost(
    navigator: StapelNavigator,
    contributions: List<NavContribution>,
    modifier: Modifier = Modifier,
    capabilities: Set<CapabilityKey> = emptySet(),
    overlay: OverlayHost = DefaultOverlay,
    unavailable: @Composable (destination: ResolvedDestination, missing: Set<CapabilityKey>) -> Unit,
    waiting: @Composable () -> Unit,
    section: @Composable (destination: ResolvedDestination) -> Unit = { DefaultSection(navigator, it) },
    width: WidthClass = WidthClass.Phone,
    detailPlaceholder: @Composable () -> Unit = {},
) {
    NavEngine(navigator, contributions, modifier, capabilities, overlay, unavailable, waiting, section, width != WidthClass.Phone, detailPlaceholder)
}

/** Unskinned section screen: one row per child, opening it. */
@Composable
private fun DefaultSection(navigator: StapelNavigator, destination: ResolvedDestination) {
    Column {
        for (child in destination.children) {
            val route = navigator.nav.defaultRoute(child.id) ?: continue
            BasicText(child.labelKey, Modifier.clickable { navigator.navigate(route) })
        }
    }
}
