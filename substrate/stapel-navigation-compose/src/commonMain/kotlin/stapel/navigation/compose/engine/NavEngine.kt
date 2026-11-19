package stapel.navigation.compose.engine

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.ui.NavDisplay
import stapel.navigation.CapabilityKey
import stapel.navigation.EmbedSpec
import stapel.navigation.NavState
import stapel.navigation.Navigator
import stapel.navigation.PaneRole
import stapel.navigation.Presentation
import stapel.navigation.ResolvedDestination
import stapel.navigation.ResolvedNav
import stapel.navigation.SectionRoute
import stapel.navigation.SlotId
import stapel.navigation.StackEntry
import stapel.navigation.StapelNavigator
import stapel.navigation.compose.EmbedContent
import stapel.navigation.compose.EmbedScopeImpl
import stapel.navigation.compose.LocalNavigator
import stapel.navigation.compose.NavBackInterceptor
import stapel.navigation.compose.NavContribution
import stapel.navigation.compose.OverlayHost
import stapel.navigation.compose.ScreenContent
import stapel.navigation.compose.ScreenScope
import stapel.navigation.compose.visibleTo

private const val SHEET = "stapel.presentation.sheet"
private const val PANE = "stapel.pane"

/** Owner key of an entry: its flow when it has one, else its uid. */
internal fun ownerKey(nav: ResolvedNav, entry: StackEntry): String =
    nav.destinationOf(entry.route)?.spec?.flow?.let { "flow:${it.value}" } ?: "entry:${entry.uid}"

/** Every entry on any stack (not only the visible ones): tab switches keep state alive. */
internal fun NavState.allEntries(): List<StackEntry> = tabStacks.values.flatten() + root + overlays

@Composable
internal fun NavEngine(
    navigator: StapelNavigator,
    contributions: List<NavContribution>,
    modifier: Modifier,
    capabilities: Set<CapabilityKey>,
    overlay: OverlayHost,
    unavailable: @Composable (ResolvedDestination, Set<CapabilityKey>) -> Unit,
    waiting: @Composable () -> Unit,
    section: @Composable (ResolvedDestination) -> Unit,
    twoPane: Boolean,
    detailPlaceholder: @Composable () -> Unit,
) {
    val state by navigator.state.collectAsState()
    val nav = navigator.nav
    val screens = remember(contributions) { contributions.flatMap { it.screens.entries }.associate { it.key to it.value } }
    val embeds = remember(contributions) { contributions.flatMap { it.embedContents.entries }.associate { it.key to it.value } }
    val saveable = rememberSaveableStateHolder()
    val stores = rememberViewModelStoreProvider(key = "stapel.navigation.entries")
    ReleaseLeftEntries(navigator, saveable, stores)

    CompositionLocalProvider(LocalNavigator provides navigator) {
        val visible = state.visible
        if (visible.isEmpty()) {
            waiting()
            return@CompositionLocalProvider
        }
        val decorator = remember(saveable, stores) { entryDecorator(nav, saveable, stores) }
        val placeholder by rememberUpdatedState(detailPlaceholder)
        val wide = rememberUpdatedState(twoPane)
        val strategies = remember(overlay) {
            listOf(DialogSceneStrategy<StackEntry>(), SheetSceneStrategy(overlay), ListDetailSceneStrategy(wide) { placeholder() }, SinglePaneSceneStrategy())
        }
        NavDisplay(
            backStack = visible,
            modifier = modifier,
            onBack = { navigator.back() },
            entryDecorators = listOf(decorator),
            sceneStrategies = strategies,
            entryProvider = { entry -> navEntry(entry, nav, navigator, screens, embeds, capabilities, unavailable, section) },
        )
    }
}

/** Clears saveable state and ViewModel stores of entries (and flows) that left every stack. */
@Composable
private fun ReleaseLeftEntries(navigator: StapelNavigator, saveable: SaveableStateHolder, stores: ViewModelStoreProvider) {
    val known = remember { HashSet<String>() }
    val knownOwners = remember { HashSet<String>() }
    LaunchedEffect(navigator, saveable, stores) {
        navigator.state.collect { s ->
            val entries = s.allEntries()
            val uids = entries.mapTo(HashSet()) { it.uid }
            val owners = entries.mapTo(HashSet()) { ownerKey(navigator.nav, it) }
            for (uid in known - uids) saveable.removeState(uid)
            for (owner in knownOwners - owners) stores.clearKey(owner)
            known.clear(); known += uids
            knownOwners.clear(); knownOwners += owners
        }
    }
}

private fun entryDecorator(nav: ResolvedNav, saveable: SaveableStateHolder, stores: ViewModelStoreProvider): NavEntryDecorator<StackEntry> =
    NavEntryDecorator(
        // Lifetime is decided by the navigation state (ReleaseLeftEntries), not by the visible list:
        // an entry of a tab that is not selected leaves the display but keeps its state.
        onPop = { },
        decorate = { entry ->
            saveable.SaveableStateProvider(entry.contentKey) {
                val key = entry.metadata[OWNER_KEY] as String
                val owner = rememberViewModelStoreOwner(key, stores)
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) { entry.Content() }
            }
        },
    )

private const val OWNER_KEY = "stapel.owner"

private fun navEntry(
    entry: StackEntry,
    nav: ResolvedNav,
    navigator: StapelNavigator,
    screens: Map<stapel.navigation.DestinationId, ScreenContent>,
    embeds: Map<stapel.navigation.EmbedId, EmbedContent>,
    capabilities: Set<CapabilityKey>,
    unavailable: @Composable (ResolvedDestination, Set<CapabilityKey>) -> Unit,
    section: @Composable (ResolvedDestination) -> Unit,
): NavEntry<StackEntry> {
    val destination = nav.destinationOf(entry.route)
    val metadata = buildMap<String, Any> {
        put(OWNER_KEY, ownerKey(nav, entry))
        when (destination?.presentation) {
            Presentation.Dialog -> putAll(DialogSceneStrategy.dialog())
            Presentation.Sheet -> put(SHEET, Presentation.Sheet)
            else -> Unit
        }
        destination?.spec?.pane?.takeIf { it != PaneRole.Single }?.let { put(PANE, it) }
    }
    return NavEntry(key = entry, contentKey = entry.uid, metadata = metadata) {
        val scope = ScreenScopeImpl(navigator, destination!!, entry, nav, embeds)
        val missing = destination.spec?.requires.orEmpty() - capabilities
        val content = screens[destination.id]
        when {
            missing.isNotEmpty() -> unavailable(destination, missing)
            content != null -> scope.content(entry.route)
            entry.route is SectionRoute -> section(destination)
            else -> unavailable(destination, emptySet())
        }
    }
}

internal class ScreenScopeImpl(
    override val navigator: Navigator,
    override val destination: ResolvedDestination,
    override val entry: StackEntry,
    private val nav: ResolvedNav,
    private val embeds: Map<stapel.navigation.EmbedId, EmbedContent>,
) : ScreenScope {
    @Composable
    override fun PageSlot(slot: SlotId, modifier: Modifier) {
        val auth = (navigator as? StapelNavigator)?.state?.collectAsState()?.value?.auth
        val address = remember(entry) { nav.addressOf(entry.route)?.path.orEmpty() }
        for (spec in nav.embeds) {
            if (spec.slot != slot || spec.scope != destination.id) continue
            if (auth != null && !visibleTo(spec, auth)) continue
            val content = embeds[spec.id] ?: continue
            EmbedScopeImpl(navigator, spec, paramsFor(spec, address)).content()
        }
    }

    private fun paramsFor(spec: EmbedSpec, address: Map<String, String>): Map<String, String> =
        spec.routeParams.mapNotNull { (embedParam, routeParam) -> address[routeParam]?.let { embedParam to it } }.toMap()
}

/** Shows a [Presentation.Sheet] entry through the [overlay] host above the entries below it. */
private class SheetSceneStrategy(private val overlay: OverlayHost) : SceneStrategy<StackEntry> {
    override fun SceneStrategyScope<StackEntry>.calculateScene(entries: List<NavEntry<StackEntry>>): Scene<StackEntry>? {
        val last = entries.lastOrNull() ?: return null
        if (last.metadata[SHEET] == null) return null
        return SheetScene(last, entries.dropLast(1), overlay, onBack)
    }
}

private class SheetScene(
    private val entry: NavEntry<StackEntry>,
    override val previousEntries: List<NavEntry<StackEntry>>,
    private val overlay: OverlayHost,
    private val onBack: () -> Unit,
) : OverlayScene<StackEntry> {
    override val key: Any = entry.contentKey
    override val entries: List<NavEntry<StackEntry>> = listOf(entry)
    override val overlaidEntries: List<NavEntry<StackEntry>> = previousEntries
    override val content: @Composable () -> Unit = {
        // The display's own back handler only sees the scene underneath; the sheet handles back itself
        // (as the dialog window does for a dialog).
        NavBackInterceptor(enabled = true, onBack = onBack)
        overlay(Presentation.Sheet, onBack) { entry.Content() }
    }

    override fun equals(other: Any?): Boolean =
        other is SheetScene && other.entry == entry && other.previousEntries == previousEntries

    override fun hashCode(): Int = entry.hashCode() * 31 + previousEntries.hashCode()
}

/**
 * List-detail from `PaneRole`: a list entry and the detail entry right above it (or the list alone)
 * form one scene at every width. Wide ([twoPane]) it draws both side by side, or the list beside
 * [placeholder]; narrow it draws only the top one. The scene's key does not depend on the width, so
 * crossing the breakpoint re-lays the same scene out in place — no scene transition, and every
 * entry keeps its state. Back and deep links need nothing of their own: the stack is the same in
 * both layouts (back pops the detail; a link's synthetic stack, list then detail, fills both panes).
 */
private class ListDetailSceneStrategy(private val twoPane: State<Boolean>, private val placeholder: @Composable () -> Unit) : SceneStrategy<StackEntry> {
    override fun SceneStrategyScope<StackEntry>.calculateScene(entries: List<NavEntry<StackEntry>>): Scene<StackEntry>? {
        val last = entries.lastOrNull() ?: return null
        return when (last.metadata[PANE]) {
            PaneRole.List -> ListDetailScene(last, null, entries.dropLast(1), twoPane, placeholder)
            PaneRole.Detail -> {
                val list = entries.getOrNull(entries.size - 2)?.takeIf { it.metadata[PANE] == PaneRole.List } ?: return null
                ListDetailScene(list, last, entries.dropLast(1), twoPane, placeholder)
            }
            else -> null
        }
    }
}

private class ListDetailScene(
    private val list: NavEntry<StackEntry>,
    private val detail: NavEntry<StackEntry>?,
    override val previousEntries: List<NavEntry<StackEntry>>,
    private val twoPane: State<Boolean>,
    private val placeholder: @Composable () -> Unit,
) : Scene<StackEntry> {
    // Keyed by both entries: the scene for "list alone" (what back returns to) differs from "list + detail".
    override val key: Any = "list-detail:" + list.contentKey + ":" + detail?.contentKey
    override val entries: List<NavEntry<StackEntry>> = listOfNotNull(list, detail)
    override val content: @Composable () -> Unit = {
        if (twoPane.value) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(LIST_WEIGHT).fillMaxHeight()) { list.Content() }
                Box(Modifier.weight(1f - LIST_WEIGHT).fillMaxHeight()) { if (detail != null) detail.Content() else placeholder() }
            }
        } else {
            Box(Modifier.fillMaxSize()) { (detail ?: list).Content() }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ListDetailScene && other.list == list && other.detail == detail && other.previousEntries == previousEntries

    override fun hashCode(): Int = (list.hashCode() * 31 + detail.hashCode()) * 31 + previousEntries.hashCode()
}

private const val LIST_WEIGHT = 0.4f
