package stapel.navigation.compose

import androidx.compose.runtime.Composable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import stapel.navigation.CapabilityKey
import stapel.navigation.DestinationId
import stapel.navigation.DestinationSpec
import stapel.navigation.EmbedId
import stapel.navigation.EmbedSpec
import stapel.navigation.EmbedSurface
import stapel.navigation.FlowId
import stapel.navigation.NavIcon
import stapel.navigation.NavManifest
import stapel.navigation.PaneRole
import stapel.navigation.PathCodec
import stapel.navigation.PathPattern
import stapel.navigation.Placement
import stapel.navigation.Presentation
import stapel.navigation.Route
import stapel.navigation.SlotId
import stapel.navigation.Surface
import stapel.navigation.WidthClass
import kotlin.reflect.KClass

/** Content of one destination: drawn with the typed route. */
public typealias ScreenContent = @Composable ScreenScope.(route: Route) -> Unit

/** Content of one embed. */
public typealias EmbedContent = @Composable EmbedScope.() -> Unit

/**
 * What one UI library contributes: its [manifest] (pure data, exported as `nav-manifest.json`) and
 * the composables bound to it. A library depends on `stapel-navigation(-compose)` only, never on
 * another brick; foreign parents are named by id.
 *
 * @property manifest the library's navigation manifest.
 */
public class NavContribution internal constructor(
    public val manifest: NavManifest,
    internal val screens: Map<DestinationId, ScreenContent>,
    internal val embedContents: Map<EmbedId, EmbedContent>,
)

/** Builds a [NavContribution] for the library [artifact] at [version]. */
public fun navContribution(artifact: String, version: String, block: NavContributionBuilder.() -> Unit): NavContribution {
    val b = NavContributionBuilder().apply(block)
    return NavContribution(NavManifest(artifact, version, b.destinations.toList(), b.embeds.toList()), b.screens.toMap(), b.embedContents.toMap())
}

/**
 * A project replaces the screen of [id] from an installed library without forking it: the manifest
 * entry keeps its id, path and placement; only the content changes.
 */
public fun NavContribution.overriding(id: DestinationId, content: ScreenContent): NavContribution {
    require(id in screens) { "${manifest.artifact} declares no destination '$id' to override" }
    return NavContribution(manifest, screens + (id to content), embedContents)
}

/** Receiver of [navContribution]. */
public class NavContributionBuilder internal constructor() {
    internal val destinations = mutableListOf<DestinationSpec<*>>()
    internal val screens = LinkedHashMap<DestinationId, ScreenContent>()
    internal val embeds = mutableListOf<EmbedSpec>()
    internal val embedContents = LinkedHashMap<EmbedId, EmbedContent>()

    /**
     * Declares a destination for route type [R] (a `@Serializable` class) and the composable that
     * draws it. Parameters mirror the web manifest entry; see `DestinationSpec` for each field.
     */
    public inline fun <reified R : Route> destination(
        id: String,
        path: String,
        labelKey: String,
        icon: NavIcon,
        codec: PathCodec<R>,
        placement: Placement = Placement.Top,
        menuVisibleDefault: Boolean = true,
        requiresAuth: Boolean = true,
        surface: Surface? = null,
        order: Int = 0,
        shortLabelKey: String? = null,
        presentation: Presentation = Presentation.Screen,
        pane: PaneRole = PaneRole.Single,
        flow: FlowId? = null,
        pageSlots: Set<SlotId> = emptySet(),
        requires: Set<CapabilityKey> = emptySet(),
        noinline content: @Composable ScreenScope.(route: R) -> Unit,
    ) {
        destination(
            R::class, serializer<R>(), id, path, labelKey, icon, codec, placement, menuVisibleDefault, requiresAuth,
            surface, order, shortLabelKey, presentation, pane, flow, pageSlots, requires, content,
        )
    }

    /** Non-reified form of the reified [destination]. */
    public fun <R : Route> destination(
        routeClass: KClass<R>,
        serializer: KSerializer<R>,
        id: String,
        path: String,
        labelKey: String,
        icon: NavIcon,
        codec: PathCodec<R>,
        placement: Placement,
        menuVisibleDefault: Boolean,
        requiresAuth: Boolean,
        surface: Surface?,
        order: Int,
        shortLabelKey: String?,
        presentation: Presentation,
        pane: PaneRole,
        flow: FlowId?,
        pageSlots: Set<SlotId>,
        requires: Set<CapabilityKey>,
        content: @Composable ScreenScope.(route: R) -> Unit,
    ) {
        val destinationId = DestinationId(id)
        require(destinationId !in screens) { "destination '$id' declared twice in one contribution" }
        destinations += DestinationSpec(
            id = destinationId, routeClass = routeClass, serializer = serializer, path = PathPattern(path), codec = codec,
            labelKey = labelKey, icon = icon, placement = placement, menuVisibleDefault = menuVisibleDefault,
            requiresAuth = requiresAuth, order = order, surface = surface, shortLabelKey = shortLabelKey,
            presentation = presentation, pane = pane, flow = flow, pageSlots = pageSlots, requires = requires,
        )
        @Suppress("UNCHECKED_CAST")
        screens[destinationId] = { route -> content(route as R) }
    }

    /** Declares an embed for a chrome slot (rendered by the shell) or a page slot (rendered by [scope]'s page). */
    public fun embed(
        id: String,
        slot: SlotId,
        order: Int,
        surface: EmbedSurface = EmbedSurface.Any,
        widths: Set<WidthClass>? = null,
        opens: DestinationId? = null,
        scope: DestinationId? = null,
        routeParams: Map<String, String> = emptyMap(),
        content: @Composable EmbedScope.() -> Unit,
    ) {
        val embedId = EmbedId(id)
        require(embedId !in embedContents) { "embed '$id' declared twice in one contribution" }
        embeds += EmbedSpec(embedId, slot, order, surface, widths, opens, scope, routeParams)
        embedContents[embedId] = content
    }
}
