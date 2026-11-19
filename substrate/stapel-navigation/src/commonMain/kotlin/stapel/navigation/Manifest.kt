package stapel.navigation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.reflect.KClass

/**
 * One navigable destination a UI library declares. Field for field the web nav-manifest entry
 * (minus the React `component`), plus the mobile fields ([presentation], [pane], [flow],
 * [pageSlots], [requires]).
 *
 * @property id stable id, shared with the web manifest.
 * @property routeClass the route type; one destination per route type.
 * @property serializer serializer of the route, registered for state saving.
 * @property path the web path (absolute `"/login"` or relative `"security"`).
 * @property codec route ⇄ path/query parameters.
 * @property labelKey i18n key of the menu label.
 * @property icon neutral icon key.
 * @property placement top level or child of another destination.
 * @property menuVisibleDefault shown in menus unless overridden.
 * @property requiresAuth needs a session (any, guest included).
 * @property order sort key among siblings; ties sort by id.
 * @property surface declared audience; null = derived from [requiresAuth].
 * @property shortLabelKey label for narrow chrome (bottom bar).
 * @property presentation how it is shown.
 * @property pane list-detail role.
 * @property flow presenter scope shared with other destinations of the same flow.
 * @property pageSlots page slots this screen renders.
 * @property requires capabilities without which the screen is replaced by a named placeholder.
 * @property aliases additional absolute path patterns that open this destination from a link (a
 *   short or older form, a push payload path); parsed with [codec] like the canonical path. Mobile
 *   only: the canonical address ([path], `ResolvedNav.addressOf`, `DeepLinkResolver.uriFor`) never
 *   changes, and the web route plan has no aliases.
 */
public class DestinationSpec<R : Route>(
    public val id: DestinationId,
    public val routeClass: KClass<R>,
    public val serializer: KSerializer<R>,
    public val path: PathPattern,
    public val codec: PathCodec<R>,
    public val labelKey: String,
    public val icon: NavIcon,
    public val placement: Placement,
    public val menuVisibleDefault: Boolean,
    public val requiresAuth: Boolean,
    public val order: Int,
    public val surface: Surface? = null,
    public val shortLabelKey: String? = null,
    public val presentation: Presentation = Presentation.Screen,
    public val pane: PaneRole = PaneRole.Single,
    public val flow: FlowId? = null,
    public val pageSlots: Set<SlotId> = emptySet(),
    public val requires: Set<CapabilityKey> = emptySet(),
    public val aliases: List<PathPattern> = emptyList(),
) {
    /** The declared surface, or the one derived from [requiresAuth]. */
    public val resolvedSurface: Surface get() = surface.orDerivedFrom(requiresAuth)
}

/**
 * A piece of UI a library places into a chrome or page slot.
 *
 * @property id stable id.
 * @property slot the slot it fills.
 * @property order sort key inside the slot; ties sort by id.
 * @property surface who sees it.
 * @property scope the destination whose page renders this embed; required for page slots, refused
 *   for chrome slots.
 * @property opens destination the embed opens when activated.
 * @property routeParams page route parameters the embed reads (`"listingId" to "id"`).
 * @property widths width classes it is shown at; null = every width.
 */
public class EmbedSpec(
    public val id: EmbedId,
    public val slot: SlotId,
    public val order: Int,
    public val surface: EmbedSurface = EmbedSurface.Any,
    public val widths: Set<WidthClass>? = null,
    public val opens: DestinationId? = null,
    public val scope: DestinationId? = null,
    public val routeParams: Map<String, String> = emptyMap(),
)

/**
 * Everything one UI library contributes to navigation.
 *
 * @property artifact Maven coordinates without version (`"io.github.usestapel:stapel-auth-ui"`).
 * @property version the library version.
 * @property destinations its destinations.
 * @property embeds its embeds.
 */
public class NavManifest(
    public val artifact: String,
    public val version: String,
    public val destinations: List<DestinationSpec<*>>,
    public val embeds: List<EmbedSpec> = emptyList(),
)

/** The surface itself, or for null the one derived from [requiresAuth] (the web `navEntrySurface` rule). */
public fun Surface?.orDerivedFrom(requiresAuth: Boolean): Surface =
    this ?: if (requiresAuth) Surface.Member else Surface.Public

private val manifestJson = Json { prettyPrint = true }

/**
 * Entry fields [toJson] writes that the web `nav-manifest.json` does not have. The shared route-plan
 * fixtures (web parity) carry none of them, so they can never change a routed path; a reader of the
 * web shape ignores them.
 */
public val MOBILE_ONLY_ENTRY_FIELDS: Set<String> = setOf("presentation", "pane", "flow", "pageSlots", "requires", "aliases")

/**
 * Wire form for the scaffold and Studio: the web `nav-manifest.json` shape (`package`, `version`,
 * `entries` with `route.path` and `placement.level`), minus `component`, plus the mobile fields and
 * `embeds`. Deterministic: same manifest, same text.
 */
public fun NavManifest.toJson(): String {
    val root = buildJsonObject {
        put("package", artifact)
        put("version", version)
        putJsonArray("entries") {
            for (d in destinations) add(buildJsonObject { destinationFields(d) })
        }
        putJsonArray("embeds") {
            for (e in embeds) add(buildJsonObject { embedFields(e) })
        }
    }
    return manifestJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root)
}

private fun JsonObjectBuilder.destinationFields(d: DestinationSpec<*>) {
    put("id", d.id.value)
    put("labelKey", d.labelKey)
    d.shortLabelKey?.let { put("shortLabelKey", it) }
    put("icon", d.icon.key)
    putJsonObject("route") { put("path", d.path.raw) }
    putJsonObject("placement") {
        when (val p = d.placement) {
            Placement.Top -> put("level", "top")
            is Placement.Submenu -> {
                put("level", "submenu")
                put("parentId", p.parentId.value)
            }
        }
    }
    put("menuVisibleDefault", d.menuVisibleDefault)
    put("requiresAuth", d.requiresAuth)
    d.surface?.let { put("surface", it.name.lowercase()) }
    put("order", d.order)
    put("presentation", d.presentation.name.lowercase())
    put("pane", d.pane.name.lowercase())
    d.flow?.let { put("flow", it.value) }
    if (d.pageSlots.isNotEmpty()) put("pageSlots", buildJsonArray { d.pageSlots.map { it.value }.sorted().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
    if (d.aliases.isNotEmpty()) put("aliases", buildJsonArray { d.aliases.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.raw)) } })
    if (d.requires.isNotEmpty()) put("requires", buildJsonArray { d.requires.map { it.value }.sorted().forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
}

private fun JsonObjectBuilder.embedFields(e: EmbedSpec) {
    put("id", e.id.value)
    put("slot", e.slot.value)
    put("order", e.order)
    put("surface", e.surface.name.lowercase())
    e.widths?.let { w -> put("breakpoints", buildJsonArray { w.sortedBy { it.ordinal }.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.name.lowercase())) } }) }
    e.opens?.let { put("opens", it.value) }
    e.scope?.let { put("scopeNavEntry", it.value) }
    if (e.routeParams.isNotEmpty()) {
        putJsonObject("routeProps") { e.routeParams.entries.sortedBy { it.key }.forEach { (k, v) -> put(k, v) } }
    }
}
