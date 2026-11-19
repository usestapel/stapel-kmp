package stapel.showcase.gallery

import kotlinx.serialization.Serializable
import stapel.navigation.DeepLinkConfig
import stapel.navigation.DestinationId
import stapel.navigation.NavIcon
import stapel.navigation.NavPolicy
import stapel.navigation.PathCodec
import stapel.navigation.Placement
import stapel.navigation.Route
import stapel.navigation.RouteAddress
import stapel.navigation.compose.NavContribution
import stapel.navigation.compose.ScreenScope
import stapel.navigation.compose.navContribution
import androidx.compose.runtime.Composable

/** The gallery's start page: the catalogues and their families. */
@Serializable
data object HomeRoute : Route

/** A catalogue page: its families (groups) and their stories. */
@Serializable
data class CatalogRoute(val catalog: String) : Route

/**
 * A story page.
 *
 * @property tab the page tab (`playground`, `states`, `matrix`, `anatomy`, `usage`), null = playground.
 * @property state the state selected on the states tab.
 * @property controls global controls carried by the link (see [GalleryControls.toQuery]).
 */
@Serializable
data class StoryRoute(
    val catalog: String,
    val story: String,
    val tab: String? = null,
    val state: String? = null,
    val controls: Map<String, String> = emptyMap(),
) : Route

/** The custom scheme of gallery links: `stapel-showcase://s/ui/button?skin=Foundation`. */
const val GALLERY_SCHEME: String = "stapel-showcase"

/** Deep-link configuration of the gallery (custom scheme only). */
val galleryDeepLinks: DeepLinkConfig = DeepLinkConfig(schemes = setOf(GALLERY_SCHEME), hosts = emptySet())

internal val HOME = DestinationId("gallery.home")

/** Navigation policy: no sign-in, home is the start page. */
val galleryPolicy: NavPolicy = NavPolicy(signIn = null, home = HOME)

/**
 * Turns a page path into a gallery link: `/s/ui/button?skin=Foundation` (story), `/c/auth`
 * (catalogue), `/` (start); a full `stapel-showcase://` link is accepted too.
 */
fun galleryLink(pathOrUri: String): String {
    if (pathOrUri.startsWith("$GALLERY_SCHEME:")) return pathOrUri
    return "$GALLERY_SCHEME://" + pathOrUri.removePrefix("/")
}

private fun first(q: Map<String, List<String>>, key: String): String? = q[key]?.firstOrNull()?.takeIf { it.isNotEmpty() }

/** The gallery's destinations; [home], [catalog] and [story] draw the pages. */
internal fun galleryContribution(
    home: @Composable ScreenScope.() -> Unit,
    catalog: @Composable ScreenScope.(CatalogRoute) -> Unit,
    story: @Composable ScreenScope.(StoryRoute) -> Unit,
): NavContribution = navContribution("stapel-showcase-gallery", "0") {
    destination<HomeRoute>(
        id = HOME.value, path = "", labelKey = "Gallery", icon = NavIcon("home"),
        codec = PathCodec.constant(HomeRoute), requiresAuth = false,
    ) { home() }
    destination<CatalogRoute>(
        id = "gallery.catalog", path = "c/:catalog", labelKey = "Catalogue", icon = NavIcon("folder"),
        codec = PathCodec({ p, _ -> p["catalog"]?.let(::CatalogRoute) }, { RouteAddress(path = mapOf("catalog" to it.catalog)) }),
        placement = Placement.Submenu(HOME), menuVisibleDefault = false, requiresAuth = false,
    ) { route -> catalog(route) }
    destination<StoryRoute>(
        id = "gallery.story", path = "s/:catalog/:story", labelKey = "Story", icon = NavIcon("apps"),
        codec = PathCodec(
            parse = { p, q ->
                val c = p["catalog"]
                val s = p["story"]
                if (c == null || s == null) null
                else StoryRoute(
                    c, s, first(q, "tab"), first(q, "state"),
                    q.filterKeys { it in GalleryControls.KEYS }.mapNotNull { (k, v) -> v.firstOrNull()?.let { k to it } }.toMap(),
                )
            },
            format = { r ->
                RouteAddress(
                    path = mapOf("catalog" to r.catalog, "story" to r.story),
                    query = buildMap {
                        r.tab?.let { put("tab", listOf(it)) }
                        r.state?.let { put("state", listOf(it)) }
                        r.controls.forEach { (k, v) -> put(k, listOf(v)) }
                    },
                )
            },
        ),
        placement = Placement.Submenu(HOME), menuVisibleDefault = false, requiresAuth = false,
    ) { route -> story(route) }
}
