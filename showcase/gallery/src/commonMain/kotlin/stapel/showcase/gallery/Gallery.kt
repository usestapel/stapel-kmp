package stapel.showcase.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import stapel.navigation.DeepLinkResolver
import stapel.navigation.NavAuthSource
import stapel.navigation.NavAuthState
import stapel.navigation.Route
import stapel.navigation.StapelNavigator
import stapel.navigation.Navigator
import stapel.navigation.compose.StapelNavHost
import stapel.navigation.compose.rememberStapelNavigator
import stapel.showcase.KnobValues
import stapel.showcase.Story
import stapel.showcase.StoryCatalog
import stapel.showcase.searchStories
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
import stapel.ui.EmptyState
import stapel.ui.SectionHeader
import stapel.ui.SectionHeaderSize
import stapel.ui.StapelInput
import stapel.ui.StapelListItem
import stapel.ui.StapelText
import stapel.ui.StapelTextField
import stapel.ui.StapelTheme
import stapel.ui.skin.FoundationSkin
import stapel.ui.skin.TextFieldKind
import stapel.ui.stapelLayout

/**
 * What the gallery holds across pages: the catalogues, the global controls, the search query, the
 * live knob values per story and the event log.
 */
class GalleryState(val catalogs: List<StoryCatalog>, initial: GalleryControls = GalleryControls()) {
    /** The global controls applied to every canvas. */
    var controls: GalleryControls by mutableStateOf(initial)

    /** The search field's state. */
    val query: TextFieldState = TextFieldState()

    /** Callbacks reported by stories, newest last. */
    val events: MutableList<String> = mutableStateListOf()

    private val values = HashMap<String, KnobValues>()

    /** The catalogue named [id]. */
    fun catalog(id: String): StoryCatalog? = catalogs.firstOrNull { it.id == id }

    /** The live knob values of [story] (kept while the gallery runs, so a page revisit keeps them). */
    fun values(catalog: StoryCatalog, story: Story): KnobValues = values.getOrPut(catalog.qualifiedId(story)) { KnobValues(story.knobs) }

    /** Every locale any catalogue ships, English first. */
    val locales: List<String> = (listOf("en") + catalogs.flatMap { it.environment.locales }).distinct()

    /** Appends to the event log (keeps the last 50). */
    fun log(event: String) {
        events += event
        while (events.size > 50) events.removeAt(0)
    }
}

/** The gallery's chrome draws itself in this fixed theme; only the story canvases follow the controls. */
@Composable
private fun ChromeTheme(width: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    StapelTheme(
        skin = FoundationSkin, tokens = StapelDefaultTokens, mode = ThemeMode.Light,
        layout = stapelLayout(StapelDefaultTokens, width, input = StapelInput.Pointer), reduceMotion = true, content = content,
    )
}

/**
 * The gallery app: the catalogue tree with search (a side pane on wide windows, the start page on a
 * phone), the global controls, and the pages — catalogue (family) pages and story pages — on
 * stapel-navigation, so each has a route and a link.
 *
 * @param catalogs what to show (the build's [stapel.showcase.gallery.generated.discoveredCatalogs] by default).
 * @param initialLink opened once at start (`/s/ui/button` or `stapel-showcase://…`).
 * @param copyText puts text on the platform clipboard ("copy story id", "copy link").
 */
@Composable
fun Gallery(
    catalogs: List<StoryCatalog> = stapel.showcase.gallery.generated.discoveredCatalogs.map { it.catalog },
    initialLink: String? = null,
    copyText: (String) -> Unit = {},
    state: GalleryState = remember(catalogs) { GalleryState(catalogs) },
) {
    val auth = remember { NavAuthSource { MutableStateFlow(NavAuthState.Member) } }
    var navigatorRef by remember { mutableStateOf<StapelNavigator?>(null) }
    val contribution = remember(state) {
        galleryContribution(
            home = { HomePage(state, navigator) },
            catalog = { route -> CatalogPage(state, route, navigator) },
            story = { route -> StoryPage(state, route, navigator, { r -> linkOf(navigatorRef, r) }, copyText) },
        )
    }
    val contributions = remember(contribution) { listOf(contribution) }
    val navigator = rememberStapelNavigator(contributions, galleryPolicy, auth, galleryDeepLinks)
    navigatorRef = navigator
    LaunchedEffect(navigator, initialLink) { initialLink?.let { navigator.open(galleryLink(it)) } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        ChromeTheme(maxWidth) {
            Column(Modifier.fillMaxSize().background(StapelTheme.colors.surfaceSunken)) {
                ControlsBar(state)
                Row(Modifier.fillMaxSize()) {
                    if (wide) {
                        Column(Modifier.width(300.dp).fillMaxHeight().background(StapelTheme.colors.surface).testTag("gallery.sidebar")) {
                            CatalogTree(state, navigator)
                        }
                    }
                    StapelNavHost(
                        navigator = navigator,
                        contributions = contributions,
                        modifier = Modifier.fillMaxSize(),
                        unavailable = { d, _ -> EmptyState("Unavailable: ${d.id.value}") },
                        waiting = { StapelText("…") },
                    )
                }
            }
        }
    }
}

/** The link of [route] under the gallery's scheme, or null before the navigator exists. */
internal fun linkOf(navigator: StapelNavigator?, route: Route): String? =
    navigator?.let { DeepLinkResolver(it.nav, galleryDeepLinks).uriFor(route, GALLERY_SCHEME) }

/** Search field and either the hits or the catalogue → family → story tree. */
@Composable
internal fun CatalogTree(state: GalleryState, navigator: Navigator) {
    var query by remember { mutableStateOf("") }
    LaunchedEffect(state.query) { snapshotFlow { state.query.text.toString() }.collect { query = it } }
    Column(Modifier.fillMaxSize()) {
        StapelTextField(state.query, "Search stories", Modifier.fillMaxWidth().padding(8.dp), kind = TextFieldKind.Search, testTag = "gallery.search")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (query.isNotBlank()) {
                val hits = searchStories(state.catalogs, query)
                if (hits.isEmpty()) StapelText("No stories match “$query”", Modifier.padding(16.dp).testTag("gallery.no-hits"))
                hits.forEach { hit ->
                    val qid = hit.catalog.qualifiedId(hit.story)
                    StapelListItem(
                        hit.story.title, supporting = "$qid · ${hit.story.group}", testTag = "gallery.hit.$qid",
                        onClick = { navigator.navigate(StoryRoute(hit.catalog.id, hit.story.id)) },
                    )
                }
            } else {
                state.catalogs.forEach { catalog ->
                    StapelListItem(
                        catalog.title, supporting = "${catalog.stories.size} stories", testTag = "gallery.tree.${catalog.id}",
                        onClick = { navigator.navigate(CatalogRoute(catalog.id)) },
                    )
                    catalog.groups.forEach { group ->
                        SectionHeader(group, Modifier.padding(start = 16.dp), size = SectionHeaderSize.XSmall)
                        catalog.stories.filter { it.group == group }.forEach { story ->
                            StapelListItem(
                                story.title, Modifier.padding(start = 16.dp), testTag = "gallery.tree.${catalog.qualifiedId(story)}",
                                onClick = { navigator.navigate(StoryRoute(catalog.id, story.id)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePage(state: GalleryState, navigator: Navigator) {
    Column(Modifier.fillMaxSize().testTag("gallery.page.home")) {
        BoxWithConstraints {
            if (maxWidth < 600.dp) {
                CatalogTree(state, navigator)
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Stapel showcase", caption = "${state.catalogs.sumOf { it.stories.size }} stories in ${state.catalogs.size} catalogues")
                    state.catalogs.forEach { c ->
                        StapelListItem(c.title, supporting = "${c.id} · ${c.groups.size} families · ${c.stories.size} stories", onClick = { navigator.navigate(CatalogRoute(c.id)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogPage(state: GalleryState, route: CatalogRoute, navigator: Navigator) {
    val catalog = state.catalog(route.catalog)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("gallery.page.catalog.${route.catalog}")) {
        if (catalog == null) {
            EmptyState("No catalogue “${route.catalog}”", description = "Known: ${state.catalogs.joinToString { it.id }}")
            return@Column
        }
        SectionHeader(catalog.title, caption = "${catalog.stories.size} stories")
        catalog.groups.forEach { group ->
            SectionHeader(group, size = SectionHeaderSize.Small)
            catalog.stories.filter { it.group == group }.forEach { story ->
                StapelListItem(
                    story.title, supporting = "${story.id} · ${story.kind} · ${story.states.size} state(s)",
                    testTag = "gallery.family.${catalog.qualifiedId(story)}",
                    onClick = { navigator.navigate(StoryRoute(catalog.id, story.id)) },
                )
            }
        }
    }
}

/** The modes a canvas is drawn in for [choice]. */
internal fun modesOf(choice: ModeChoice): List<ThemeMode> = when (choice) {
    ModeChoice.Light -> listOf(ThemeMode.Light)
    ModeChoice.Dark -> listOf(ThemeMode.Dark)
    ModeChoice.Both -> listOf(ThemeMode.Light, ThemeMode.Dark)
}

/** A horizontally scrollable row of the story in every mode of the controls. */
@Composable
internal fun Frames(
    state: GalleryState,
    catalog: StoryCatalog,
    story: Story,
    storyState: stapel.showcase.StoryState = story.states.first(),
    values: KnobValues = state.values(catalog, story),
    overrides: Map<String, Any?> = emptyMap(),
    showAnatomy: Boolean = false,
    tag: String = "gallery.frame",
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        modesOf(state.controls.mode).forEach { mode ->
            StoryFrame(
                story, catalog.environment, state.controls, mode, Modifier.testTag("$tag.${mode.name.lowercase()}"),
                storyState, values, overrides, { state.log("${catalog.qualifiedId(story)}: $it") }, showAnatomy,
            )
        }
    }
}
