package stapel.navigation

import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Per-destination override from the project's nav override file. Null fields keep the declaration.
 *
 * @property menuVisible shown in menus.
 * @property order sort key.
 * @property surface audience.
 * @property requiresAuth needs a session.
 */
public class NavOverride(
    public val menuVisible: Boolean? = null,
    public val order: Int? = null,
    public val surface: Surface? = null,
    public val requiresAuth: Boolean? = null,
)

/**
 * The project's overrides.
 *
 * @property byId overrides by destination id.
 */
public class NavOverrides(public val byId: Map<DestinationId, NavOverride> = emptyMap())

/**
 * A section no library owns (`account.root`, `admin.root`): the container declares it whenever an
 * installed destination hangs from it.
 *
 * @property id the root's id.
 * @property labelKey i18n key of its label.
 * @property icon neutral icon key.
 * @property path its path relative to the member area.
 * @property order sort key among tops.
 */
public class ContainerRoot(
    public val id: DestinationId,
    public val labelKey: String,
    public val icon: NavIcon,
    public val path: String,
    public val order: Int,
)

/** The container roots of the web monolith, with the same ids, paths and order. */
public object ContainerRoots {
    /** The account section, `/app/account`. */
    public val Account: ContainerRoot = ContainerRoot(DestinationId("account.root"), "app.nav.account", NavIcons.User, "account", 100)

    /** The admin section, `/app/admin`. */
    public val Admin: ContainerRoot = ContainerRoot(DestinationId("admin.root"), "shell.nav.admin", NavIcons.Audit, "admin", 110)

    /** Both roots. */
    public val all: List<ContainerRoot> = listOf(Account, Admin)
}

/**
 * Route of a container root's section screen (the generic list of its children).
 *
 * @property id the container root.
 */
@Serializable
public data class SectionRoute(public val id: DestinationId) : Route

/**
 * A destination after resolution: overrides applied, absolute path computed, parent and children
 * linked.
 *
 * @property spec the library's declaration; null for a container root.
 * @property id the destination id.
 * @property labelKey i18n key of the label.
 * @property shortLabelKey i18n key of the short label, if any.
 * @property icon neutral icon key.
 * @property absolutePath the web path (`"/login"`, `"/app/settings/security"`), with `:params`.
 * @property surface the audience after overrides (declared or derived).
 * @property requiresAuth needs a session, after overrides.
 * @property order sort key after overrides.
 * @property menuVisible shown in menus, after overrides.
 * @property parent the parent of a child entry; null for a top.
 * @property children routed children, sorted (order, id).
 */
public class ResolvedDestination(
    public val spec: DestinationSpec<*>?,
    public val id: DestinationId,
    public val labelKey: String,
    public val shortLabelKey: String?,
    public val icon: NavIcon,
    public val absolutePath: String,
    public val surface: Surface,
    public val requiresAuth: Boolean,
    public val order: Int,
    public val menuVisible: Boolean,
    public val parent: DestinationId?,
    public val children: List<ResolvedDestination>,
) {
    /** How it is presented; a container root is a screen. */
    public val presentation: Presentation get() = spec?.presentation ?: Presentation.Screen

    /** Id and path, for messages. */
    override fun toString(): String = "ResolvedDestination($id, $absolutePath)"
}

/**
 * An installed declaration that did not make it into the navigation, with the reason. Never silent.
 *
 * @property id id of the destination or embed.
 * @property reason why it was dropped, in words.
 */
public data class DroppedEntry(public val id: String, public val reason: String)

/**
 * Thrown for declarations stapel-tools refuses at generation time.
 *
 * @property problems one line per problem, each starting with the offending id and a colon.
 */
public class NavContractException(public val problems: List<String>) :
    IllegalStateException("navigation contract is not mountable:\n  - " + problems.joinToString("\n  - "))

/** Result of [resolveNav]. */
public class ResolvedNav internal constructor(
    /** Top-level destinations sorted by (order, id), not filtered by audience. */
    public val tops: List<ResolvedDestination>,
    /** Every routed destination, tops and children. */
    public val byId: Map<DestinationId, ResolvedDestination>,
    /** Container roots injected because something hangs from them, sorted by id. */
    public val containerRoots: List<DestinationId>,
    /** Orphans and unmountable entries, with the reason. */
    public val dropped: List<DroppedEntry>,
    /** Embeds whose targets are installed, sorted by (slot, order, id). */
    public val embeds: List<EmbedSpec>,
    /** The manifests resolved, in the order given. */
    public val manifests: List<NavManifest>,
    private val byRouteClass: Map<KClass<*>, DestinationId>,
) {
    /** The destination [route] belongs to, or null when no installed library declares its type. */
    public fun destinationOf(route: Route): ResolvedDestination? = when (route) {
        is SectionRoute -> byId[route.id]?.takeIf { it.spec == null }
        else -> byRouteClass[route::class]?.let { byId[it] }
    }

    /** Route of [id] without parameters (a tab root, a section), or null when it needs parameters. */
    public fun defaultRoute(id: DestinationId): Route? {
        val d = byId[id] ?: return null
        val spec = d.spec ?: return SectionRoute(id)
        return spec.codec.parse(emptyMap(), emptyMap())
    }

    /** Path and query parameters of [route]. */
    public fun addressOf(route: Route): RouteAddress? {
        if (route is SectionRoute) return RouteAddress()
        val spec = destinationOf(route)?.spec ?: return null
        @Suppress("UNCHECKED_CAST")
        return (spec.codec as PathCodec<Route>).format(route)
    }

    /**
     * The stack a user would have walked to reach [route]: the declared ancestors (built from the
     * route's own path parameters) followed by [route]. An ancestor that needs parameters the route
     * does not carry is left out.
     */
    public fun syntheticStack(route: Route): List<Route> {
        val target = destinationOf(route) ?: return listOf(route)
        val params = addressOf(route)?.path.orEmpty()
        val out = ArrayDeque<Route>()
        out.addFirst(route)
        var parentId = target.parent
        while (parentId != null) {
            val parent = byId[parentId] ?: break
            val parentRoute = parent.spec?.let { spec ->
                val names = PathPattern(parent.absolutePath).params.toSet()
                spec.codec.parse(params.filterKeys { it in names }, emptyMap())
            } ?: if (parent.spec == null) SectionRoute(parent.id) else null
            if (parentRoute != null) out.addFirst(parentRoute)
            parentId = parent.parent
        }
        return out.toList()
    }

    /** Menu entries for [auth]: menu-visible tops the audience may open, sorted (order, id). */
    public fun menuFor(auth: NavAuthState): List<ResolvedDestination> =
        tops.filter { it.menuVisible && isOpenTo(it, auth) }

    /** Tabs for [auth]: [menuFor] entries that are screens and can be opened without parameters. */
    public fun tabsFor(auth: NavAuthState): List<DestinationId> =
        menuFor(auth).filter { it.presentation == Presentation.Screen && defaultRoute(it.id) != null }.map { it.id }
}

/** Whether [auth] may open [d] without a sign-in: the surface × requiresAuth matrix, axes independent. */
internal fun isOpenTo(d: ResolvedDestination, auth: NavAuthState): Boolean = when {
    d.surface == Surface.Public && !d.requiresAuth -> true
    d.surface == Surface.Public -> auth == NavAuthState.Guest || auth == NavAuthState.Member
    else -> auth == NavAuthState.Member
}

private class Working(
    val spec: DestinationSpec<*>?,
    val id: DestinationId,
    val labelKey: String,
    val shortLabelKey: String?,
    val icon: NavIcon,
    val path: PathPattern,
    val placement: Placement,
    val surface: Surface,
    val requiresAuth: Boolean,
    val order: Int,
    val menuVisible: Boolean,
)

private val WEB_ONLY_SLOTS = setOf("browsebar", "footer")

/**
 * Resolves installed manifests into the navigation tree. A port of stapel-tools'
 * `build_nav_route_plan` (paths, container roots, orphan rule) held to it by shared fixtures, plus
 * the overrides of `@stapel/shell-react`'s `resolveNav`.
 *
 * Path rule: an absolute path stays; a public top with a relative path is hoisted to the root
 * (`/path`) with its children under it; a member top sits under [memberAreaPath]; a child appends
 * its path to its parent's. Children of an absolute-path top get no route (as in the planner).
 *
 * Throws [NavContractException] for a duplicate id, a duplicate route type, an icon outside [icons],
 * a page-slot embed without `scope` or a chrome-slot embed with one, and — when [knownIds] is given —
 * a parent that neither an installed library, a container root nor [knownIds] declares. A parent in
 * [knownIds] that is not installed is an orphan: dropped with the reason.
 *
 * @param knownIds every destination id any library of the platform declares (the registry); null
 *   when unknown, then every missing parent is an orphan.
 */
public fun resolveNav(
    manifests: List<NavManifest>,
    overrides: NavOverrides = NavOverrides(),
    memberAreaPath: String = "/app",
    icons: Set<NavIcon> = NavIcons.all,
    knownIds: Set<DestinationId>? = null,
): ResolvedNav {
    val problems = mutableListOf<String>()
    val dropped = mutableListOf<DroppedEntry>()
    val declaredBy = LinkedHashMap<DestinationId, String>()
    val byRouteClass = HashMap<KClass<*>, DestinationId>()
    val specs = mutableListOf<DestinationSpec<*>>()
    val containerIds = ContainerRoots.all.map { it.id }.toSet()

    for (m in manifests) for (d in m.destinations) {
        val first = declaredBy[d.id]
        when {
            first != null -> problems += "${d.id}: declared twice (by $first and ${m.artifact})"
            d.id in containerIds -> problems += "${d.id}: is a container root; a library may hang entries from it, not declare it"
            else -> {
                declaredBy[d.id] = m.artifact
                specs += d
            }
        }
        val otherRoute = byRouteClass[d.routeClass]
        if (otherRoute != null && otherRoute != d.id) {
            problems += "${d.id}: route type ${d.routeClass.simpleName} is already bound to $otherRoute"
        } else {
            byRouteClass[d.routeClass] = d.id
        }
        if (d.icon !in icons) {
            problems += "${d.id}: icon '${d.icon.key}' is not in the icon vocabulary (${icons.map { it.key }.sorted().joinToString(", ")})"
        }
        val p = d.placement
        if (p is Placement.Submenu && knownIds != null) {
            val parent = p.parentId
            val installedOrKnown = parent in knownIds || parent in containerIds ||
                manifests.any { mm -> mm.destinations.any { it.id == parent } }
            if (!installedOrKnown) {
                problems += "${d.id}: placement.parentId '$parent' is declared by no library and by no container root " +
                    "(${containerIds.map { it.value }.sorted().joinToString(", ")})"
            }
        }
    }
    for (m in manifests) for (e in m.embeds) {
        if (e.slot.isPageSlot && e.scope == null) problems += "${e.id}: page slot '${e.slot}' needs a scope destination"
        if (!e.slot.isPageSlot && e.scope != null) problems += "${e.id}: chrome slot '${e.slot}' takes no scope"
    }
    if (problems.isNotEmpty()) throw NavContractException(problems)

    val neededRoots = specs.mapNotNull { (it.placement as? Placement.Submenu)?.parentId }
        .filter { it in containerIds }.toSet()
    val roots = ContainerRoots.all.filter { it.id in neededRoots }.sortedBy { it.id.value }

    fun ov(id: DestinationId) = overrides.byId[id]
    val working = mutableListOf<Working>()
    for (s in specs) {
        val o = ov(s.id)
        val requiresAuth = o?.requiresAuth ?: s.requiresAuth
        working += Working(
            spec = s, id = s.id, labelKey = s.labelKey, shortLabelKey = s.shortLabelKey, icon = s.icon,
            path = s.path, placement = s.placement,
            surface = o?.surface ?: s.surface.orDerivedFrom(requiresAuth), requiresAuth = requiresAuth,
            order = o?.order ?: s.order, menuVisible = o?.menuVisible ?: s.menuVisibleDefault,
        )
    }
    for (r in roots) {
        val o = ov(r.id)
        working += Working(
            spec = null, id = r.id, labelKey = r.labelKey, shortLabelKey = null, icon = r.icon,
            path = PathPattern(r.path), placement = Placement.Top,
            surface = o?.surface ?: Surface.Member, requiresAuth = o?.requiresAuth ?: true,
            order = o?.order ?: r.order, menuVisible = o?.menuVisible ?: true,
        )
    }

    val sortKey = compareBy<Working>({ it.order }, { it.id.value })
    val topWorking = working.filter { it.placement == Placement.Top }.sortedWith(sortKey)
    val topIds = topWorking.map { it.id }.toSet()
    val childrenOf = HashMap<DestinationId, MutableList<Working>>()
    for (w in working) {
        val p = w.placement as? Placement.Submenu ?: continue
        when {
            p.parentId in topIds -> childrenOf.getOrPut(p.parentId) { mutableListOf() } += w
            working.any { it.id == p.parentId } ->
                dropped += DroppedEntry(w.id.value, "parent '${p.parentId}' is itself a submenu entry; only top-level entries take children")
            else -> dropped += DroppedEntry(w.id.value, "parent '${p.parentId}' is not installed")
        }
    }

    val byId = LinkedHashMap<DestinationId, ResolvedDestination>()
    val member = memberAreaPath.trimEnd('/')
    val tops = mutableListOf<ResolvedDestination>()
    for (top in topWorking) {
        val kids = childrenOf[top.id].orEmpty().sortedWith(sortKey)
        val base: String = when {
            top.path.isAbsolute -> top.path.raw
            top.surface == Surface.Public -> "/" + top.path.raw
            else -> member + "/" + top.path.raw
        }
        val resolvedKids = if (top.path.isAbsolute) {
            kids.forEach {
                dropped += DroppedEntry(it.id.value, "parent '${top.id}' has an absolute path; children of an absolute-path entry get no route")
            }
            emptyList()
        } else {
            kids.map { k -> k.resolve("$base/${k.path.raw}", top.id, emptyList()) }
        }
        val resolvedTop = top.resolve(base, null, resolvedKids)
        tops += resolvedTop
        byId[top.id] = resolvedTop
        resolvedKids.forEach { byId[it.id] = it }
    }

    val embeds = mutableListOf<EmbedSpec>()
    for (m in manifests) for (e in m.embeds) {
        when {
            e.slot.value in WEB_ONLY_SLOTS -> dropped += DroppedEntry(e.id.value, "slot '${e.slot}' is web-only")
            e.opens != null && e.opens !in byId -> dropped += DroppedEntry(e.id.value, "opens '${e.opens}', which is not installed")
            e.scope != null && e.scope !in byId -> dropped += DroppedEntry(e.id.value, "scope '${e.scope}' is not installed")
            else -> embeds += e
        }
    }
    embeds.sortWith(compareBy<EmbedSpec>({ it.slot.value }, { it.order }, { it.id.value }))

    return ResolvedNav(
        tops = tops,
        byId = byId,
        containerRoots = roots.map { it.id },
        dropped = dropped.sortedBy { it.id },
        embeds = embeds,
        manifests = manifests,
        byRouteClass = byRouteClass.filterValues { it in byId },
    )
}

private fun Working.resolve(path: String, parent: DestinationId?, children: List<ResolvedDestination>) = ResolvedDestination(
    spec = spec, id = id, labelKey = labelKey, shortLabelKey = shortLabelKey, icon = icon,
    absolutePath = path, surface = surface, requiresAuth = requiresAuth, order = order,
    menuVisible = menuVisible, parent = parent, children = children,
)
