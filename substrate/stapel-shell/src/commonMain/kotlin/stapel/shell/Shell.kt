package stapel.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import stapel.navigation.CapabilityKey
import stapel.navigation.DestinationId
import stapel.navigation.EmbedSpec
import stapel.navigation.GuardDecision
import stapel.navigation.NavIcon
import stapel.navigation.NavState
import stapel.navigation.Presentation
import stapel.navigation.ResolvedDestination
import stapel.navigation.StapelNavigator
import stapel.navigation.compose.EmbedSlot
import stapel.navigation.compose.LocalNavigator
import stapel.navigation.compose.OverlayHost
import stapel.navigation.compose.StapelNavHost
import stapel.navigation.compose.rememberStapelNavigator
import stapel.ui.StapelIcon
import stapel.ui.StapelIconButton
import stapel.ui.StapelIconIds
import stapel.ui.StapelIconView
import stapel.ui.StapelListItem
import stapel.ui.StapelPage
import stapel.ui.StapelProgress
import stapel.ui.StapelBanner
import stapel.ui.StapelSurface
import stapel.ui.LocalStapelChromeTitle
import stapel.ui.StapelChromeTitle
import stapel.ui.StapelScreenTitle
import stapel.ui.StapelText
import stapel.ui.StapelTheme
import stapel.ui.StapelTone
import stapel.ui.skin.BadgeSpec
import stapel.ui.skin.ElevationStep
import stapel.ui.skin.NavigationItemSpec
import stapel.ui.skin.NavigationSpec

/** Test tags of the shell's own parts (stable, for app UI tests). */
public object ShellTags {
    /** The phone bottom bar. */
    public const val BottomBar: String = "stapel.shell.bar"

    /** The tablet rail. */
    public const val Rail: String = "stapel.shell.rail"

    /** The desktop drawer. */
    public const val Drawer: String = "stapel.shell.drawer"

    /** The top bar. */
    public const val TopBar: String = "stapel.shell.topbar"

    /** The top bar's title text. */
    public const val Title: String = "stapel.shell.topbar.title"

    /** The phone "More" sheet. */
    public const val MoreSheet: String = "stapel.shell.more"

    /** The wait screen (auth unresolved). */
    public const val Waiting: String = "stapel.shell.waiting"

    /** A refusal the shell names (no mandate, sign-in unavailable). */
    public const val Refusal: String = "stapel.shell.refusal"

    /** The placeholder of a destination that cannot be shown. */
    public const val Unavailable: String = "stapel.shell.unavailable"

    /** The area between the chrome where screens are drawn. */
    public const val Content: String = "stapel.shell.content"

    /** The detail pane's placeholder while a list is shown without a detail (wide windows). */
    public const val DetailPlaceholder: String = "stapel.shell.detail_placeholder"
}

private val LocalShellLabel = staticCompositionLocalOf<@Composable (String) -> String> { { it } }

/**
 * The app shell: creates the navigator from [config] and draws chrome + navigation host.
 *
 * @param windowInsets the insets the chrome keeps clear (status and navigation bars, display
 *   cutout); see the other overload.
 */
@Composable
public fun StapelShell(config: ShellConfig, modifier: Modifier = Modifier, windowInsets: WindowInsets = ShellDefaults.windowInsets) {
    val navigator = rememberStapelNavigator(config.contributions, config.policy, config.auth, config.deepLinks, config.overrides)
    StapelShell(navigator, config, modifier, windowInsets)
}

/** Defaults of [StapelShell]. */
public object ShellDefaults {
    /** System bars and display cutout: what the chrome keeps clear. The keyboard is left to the screens. */
    public val windowInsets: WindowInsets
        @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)
}

/**
 * The app shell around an existing [navigator] (an app that keeps it, a test).
 *
 * Insets: each chrome keeps [windowInsets] clear on the sides it touches and consumes them for the
 * content, so a screen's own safe-area padding (`StapelPage`) is not applied twice — top bar: top
 * and both sides; bottom bar: bottom and both sides; rail / drawer: start, top and bottom (the top
 * bar beside it then takes only the top and the end). Sides no chrome touches (the bottom without a
 * bar, the keyboard) stay with the screen.
 */
@Composable
public fun StapelShell(
    navigator: StapelNavigator,
    config: ShellConfig,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = ShellDefaults.windowInsets,
) {
    CompositionLocalProvider(LocalShellLabel provides config.label, LocalNavigator provides navigator) {
        val state by navigator.state.collectAsState()
        val size = StapelTheme.layout.sizeClass
        val width = size.toWidthClass()
        val rendered = slotsRendered(config.template, size)
        val chrome = if (state.tabHostActive && state.root.isEmpty() && state.overlays.isEmpty()) chromeFor(config.template, size) else Chrome.None
        val embedFilter: (EmbedSpec) -> Boolean = { spec ->
            spec.slot in rendered && spec.id in config.slots.filled[spec.slot].orEmpty() && (spec.widths == null || width in spec.widths!!)
        }
        var moreOpen by rememberSaveable { mutableStateOf(false) }
        val badges = badgeCounts(config)
        val chromeTitle = remember { StapelChromeTitle() }

        val side = windowInsets.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)
        val hasSide = chrome == Chrome.Rail || chrome == Chrome.Drawer
        val topBarShown = topBarShown(navigator, state)
        val bottomBarShown = chrome == Chrome.BottomBar
        val contentConsumes = when {
            topBarShown && bottomBarShown -> windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical)
            topBarShown -> windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
            bottomBarShown -> windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
            else -> WindowInsets(0, 0, 0, 0)
        }

        Box(modifier.fillMaxSize().background(StapelTheme.colors.surface)) {
            Row(Modifier.fillMaxSize()) {
                when (chrome) {
                    Chrome.Rail -> Column(Modifier.fillMaxHeight().windowInsetsPadding(side).testTag(ShellTags.Rail)) {
                        EmbedSlot(Slots.SidenavHead, navigator, config.contributions, filter = embedFilter)
                        StapelTheme.skin.NavigationRail(navigationSpec(navigator, state.tabs, state.selectedTab?.value, badges, short = false), Modifier.weight(1f))
                        EmbedSlot(Slots.SidenavFoot, navigator, config.contributions, filter = embedFilter)
                    }
                    Chrome.Drawer -> Drawer(navigator, config, state.tabs, state.selectedTab, embedFilter, Modifier.windowInsetsPadding(side))
                    else -> Unit
                }
                // The rail / drawer took the start side (and the vertical sides on its own column).
                val main = if (hasSide) Modifier.consumeWindowInsets(windowInsets.only(WindowInsetsSides.Start)) else Modifier
                Column(main.weight(1f).fillMaxHeight()) {
                    // The bar names the hosted screen (its StapelScreenTitle, else the destination's label) unless
                    // a top-bar-center embed takes the title's place; then the screen draws its own heading.
                    val titled = topBarShown && navigator.nav.embeds.none { it.slot == Slots.TopbarCenter && embedFilter(it) }
                    if (topBarShown) {
                        TopBar(navigator, config, embedFilter, Modifier.windowInsetsPadding(windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)), chromeTitle)
                    }
                    Refusal(navigator)
                    Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(contentConsumes).testTag(ShellTags.Content)) {
                        CompositionLocalProvider(LocalStapelChromeTitle provides chromeTitle.takeIf { titled }) {
                            StapelNavHost(
                                navigator = navigator,
                                contributions = config.contributions,
                                capabilities = config.capabilities,
                                overlay = ShellOverlay,
                                unavailable = { d, missing -> UnavailableScreen(d, missing) },
                                waiting = { WaitScreen() },
                                section = { SectionScreen(it.id) },
                                width = width,
                                detailPlaceholder = { DetailPlaceholder() },
                            )
                        }
                    }
                    if (chrome == Chrome.BottomBar) {
                        EmbedSlot(Slots.Bottombar, navigator, config.contributions, filter = embedFilter)
                        val (bar, overflow) = splitTabs(state.tabs, config.policy.maxTabs.coerceAtLeast(2))
                        val selected = state.selectedTab?.let { if (it in overflow) MORE_ID else it.value }
                        val spec = navigationSpec(navigator, bar, selected, badges, short = true, more = overflow.isNotEmpty()) { id ->
                            if (id == MORE_ID) moreOpen = true else navigator.selectTab(DestinationId(id))
                        }
                        Box(Modifier.windowInsetsPadding(windowInsets.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) {
                            StapelTheme.skin.NavigationBar(spec, Modifier.testTag(ShellTags.BottomBar))
                        }
                    }
                }
            }
            if (moreOpen && chrome == Chrome.BottomBar) {
                val overflow = splitTabs(state.tabs, config.policy.maxTabs.coerceAtLeast(2)).second
                MoreSheet(navigator, config, overflow, embedFilter) { moreOpen = false }
            }
        }
    }
}

@Composable
private fun badgeCounts(config: ShellConfig): Map<DestinationId, Int> {
    val out = HashMap<DestinationId, Int>()
    for ((id, source) in config.badges) {
        key(id) {
            val flow = remember(source) { source.count() }
            val count by flow.collectAsState(0)
            out[id] = count
        }
    }
    return out
}

@Composable
private fun navigationSpec(
    navigator: StapelNavigator,
    tabs: List<DestinationId>,
    selectedId: String?,
    badges: Map<DestinationId, Int>,
    short: Boolean,
    more: Boolean = false,
    onSelect: (String) -> Unit = { navigator.selectTab(DestinationId(it)) },
): NavigationSpec {
    val label = LocalShellLabel.current
    val items = tabs.mapNotNull { navigator.nav.byId[it] }.map { d ->
        val count = badges[d.id] ?: 0
        NavigationItemSpec(
            id = d.id.value,
            label = label(if (short) d.shortLabelKey ?: d.labelKey else d.labelKey),
            icon = d.icon.toStapelIcon(),
            badge = if (count > 0) BadgeSpec(count, contentDescription = count.toString()) else null,
        )
    } + listOfNotNull(if (more) NavigationItemSpec(MORE_ID, label("shell.nav.more"), StapelIconIds.More) else null)
    return NavigationSpec(items, selectedId, onSelect)
}

/** The neutral nav icon key is a `StapelIcon` id (same vocabulary); an id the icon set lacks draws its fallback. */
internal fun NavIcon.toStapelIcon(): StapelIcon = StapelIcon(key)

/** The top bar shows for a screen or cover on top; a dialog or sheet on top hides it. */
private fun topBarShown(navigator: StapelNavigator, state: NavState): Boolean {
    val top = state.top ?: return false
    val destination = navigator.nav.destinationOf(top.route) ?: return false
    return destination.presentation == Presentation.Screen || destination.presentation == Presentation.Cover
}

@Composable
private fun TopBar(navigator: StapelNavigator, config: ShellConfig, filter: (EmbedSpec) -> Boolean, modifier: Modifier, title: StapelChromeTitle) {
    val state by navigator.state.collectAsState()
    val top = state.top ?: return
    val destination = navigator.nav.destinationOf(top.route) ?: return
    val label = LocalShellLabel.current
    val canGoBack = state.visible.size > 1
    val hasCenter = navigator.nav.embeds.any { it.slot == Slots.TopbarCenter && filter(it) }
    Row(
        Modifier.fillMaxWidth().background(StapelTheme.colors.surfaceRaised).then(modifier).testTag(ShellTags.TopBar).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canGoBack) StapelIconButton(StapelIconIds.ArrowBack, label("shell.nav.back"), { navigator.back() })
        EmbedSlot(Slots.TopbarStart, navigator, config.contributions, filter = filter)
        Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            if (hasCenter) {
                EmbedSlot(Slots.TopbarCenter, navigator, config.contributions, filter = filter)
            } else {
                StapelText(title.current ?: label(destination.labelKey), Modifier.testTag(ShellTags.Title), style = StapelTheme.type.title)
            }
        }
        EmbedSlot(Slots.TopbarEnd, navigator, config.contributions, filter = filter)
    }
}

@Composable
private fun Drawer(
    navigator: StapelNavigator,
    config: ShellConfig,
    tabs: List<DestinationId>,
    selected: DestinationId?,
    filter: (EmbedSpec) -> Boolean,
    insets: Modifier,
) {
    val label = LocalShellLabel.current
    StapelSurface(Modifier.width(280.dp).fillMaxHeight(), elevation = ElevationStep.None, testTag = ShellTags.Drawer) {
        Column(insets) {
            EmbedSlot(Slots.SidenavHead, navigator, config.contributions, filter = filter)
            for (id in tabs) {
                val d = navigator.nav.byId[id] ?: continue
                StapelListItem(
                    headline = label(d.labelKey),
                    leading = { StapelIconView(d.icon.toStapelIcon(), null) },
                    onClick = { navigator.selectTab(id) },
                    selected = id == selected,
                    testTag = "stapel.shell.drawer.${id.value}",
                )
                for (child in d.children.filter { it.menuVisible }) {
                    val route = navigator.nav.defaultRoute(child.id) ?: continue
                    StapelListItem(
                        headline = label(child.labelKey),
                        modifier = Modifier.padding(start = 24.dp),
                        onClick = { navigator.replaceAll(route) },
                        testTag = "stapel.shell.drawer.${child.id.value}",
                    )
                }
            }
            Box(Modifier.weight(1f))
            EmbedSlot(Slots.SidenavFoot, navigator, config.contributions, filter = filter)
        }
    }
}

@Composable
private fun MoreSheet(
    navigator: StapelNavigator,
    config: ShellConfig,
    overflow: List<DestinationId>,
    filter: (EmbedSpec) -> Boolean,
    onClose: () -> Unit,
) {
    val label = LocalShellLabel.current
    ShellOverlay(Presentation.Sheet, onClose) {
        Column(Modifier.testTag(ShellTags.MoreSheet)) {
            for (id in overflow) {
                val d = navigator.nav.byId[id] ?: continue
                StapelListItem(
                    headline = label(d.labelKey),
                    leading = { StapelIconView(d.icon.toStapelIcon(), null) },
                    onClick = {
                        onClose()
                        navigator.selectTab(id)
                    },
                    testTag = "stapel.shell.more.${id.value}",
                )
            }
            EmbedSlot(Slots.SheetFoot, navigator, config.contributions, filter = filter)
        }
    }
}

/** The shell's sheet: a scrim and a raised surface at the bottom, drawn through the skin. */
private val ShellOverlay: OverlayHost = { _, onDismiss, content ->
    Box(
        Modifier.fillMaxSize()
            .background(StapelTheme.colors.text.copy(alpha = 0.32f))
            .pointerInput(onDismiss) { detectTapGestures { onDismiss() } }
            .semantics { onClick(label = "Dismiss") { onDismiss(); true } },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.fillMaxWidth().pointerInput(Unit) { detectTapGestures { } }) {
            StapelSurface(Modifier.fillMaxWidth(), elevation = ElevationStep.Medium) { content() }
        }
    }
}

@Composable
private fun Refusal(navigator: StapelNavigator) {
    val refusal by navigator.lastRefusal.collectAsState()
    val deny = refusal as? GuardDecision.Deny ?: return
    var dismissed by remember(deny) { mutableStateOf(false) }
    if (dismissed) return
    val label = LocalShellLabel.current
    StapelBanner(StapelTone.Warning, label(deny.reasonKey), onDismiss = { dismissed = true }, testTag = ShellTags.Refusal)
}

@Composable
private fun DetailPlaceholder() {
    Box(Modifier.fillMaxSize().background(StapelTheme.colors.surfaceRaised).testTag(ShellTags.DetailPlaceholder), contentAlignment = Alignment.Center) {
        StapelText(LocalShellLabel.current("shell.detail.empty"))
    }
}

@Composable
private fun WaitScreen() {
    Box(Modifier.fillMaxSize().testTag(ShellTags.Waiting), contentAlignment = Alignment.Center) {
        StapelProgress(label = LocalShellLabel.current("shell.waiting"))
    }
}

/** The landing of a container root (`account.root`, `admin.root`): one row per child destination. */
@Composable
public fun SectionScreen(root: DestinationId, modifier: Modifier = Modifier) {
    val navigator = LocalNavigator.current as? StapelNavigator ?: return
    val section = navigator.nav.byId[root] ?: return
    val label = LocalShellLabel.current
    StapelPage(modifier.testTag("stapel.shell.section.${root.value}")) {
        StapelScreenTitle(label(section.labelKey))
        for (child in section.children.filter { it.menuVisible }) {
            val route = navigator.nav.defaultRoute(child.id) ?: continue
            StapelListItem(
                headline = label(child.labelKey),
                leading = { StapelIconView(child.icon.toStapelIcon(), null) },
                trailing = { StapelIconView(StapelIconIds.ChevronRight, null) },
                onClick = { navigator.navigate(route) },
                testTag = "stapel.shell.section.row.${child.id.value}",
            )
        }
    }
}

/** Names a destination that cannot be shown and what it is missing; never a blank screen. */
@Composable
public fun UnavailableScreen(destination: ResolvedDestination, missing: Set<CapabilityKey>) {
    val label = LocalShellLabel.current
    StapelPage(Modifier.testTag(ShellTags.Unavailable)) {
        StapelScreenTitle(label(destination.labelKey))
        StapelText(
            if (missing.isEmpty()) {
                "${label("shell.unavailable.no_screen")}: ${destination.id}"
            } else {
                "${label("shell.unavailable.missing")}: ${missing.map { it.value }.sorted().joinToString(", ")}"
            },
        )
    }
}
