package stapel.navigation

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

@Serializable data object LoginRoute : Route
@Serializable data object FeedRoute : Route
@Serializable data object InboxRoute : Route
@Serializable data class ThreadRoute(val threadId: String) : Route
@Serializable data object SettingsRoute : Route
@Serializable data object SecurityRoute : Route
@Serializable data class ItemRoute(val id: String, val tab: String? = null) : Route
@Serializable data class QrRoute(val key: String) : Route
@Serializable data object ConfirmRoute : Route
@Serializable data object PrivacyRoute : Route
@Serializable data object HelpRoute : Route

/** Route type entry of the generated fixture table. */
internal class FixtureRouteType<R : Route>(val type: KClass<R>, val serializer: KSerializer<R>, val instance: R)

internal inline fun <reified R : Route> dest(
    id: String,
    path: String,
    serializer: KSerializer<R>,
    codec: PathCodec<R>,
    placement: Placement = Placement.Top,
    menu: Boolean = true,
    requiresAuth: Boolean = true,
    surface: Surface? = null,
    order: Int = 0,
    presentation: Presentation = Presentation.Screen,
    icon: NavIcon = NavIcons.Apps,
): DestinationSpec<R> = DestinationSpec(
    id = DestinationId(id), routeClass = R::class, serializer = serializer, path = PathPattern(path), codec = codec,
    labelKey = "$id.label", icon = icon, placement = placement, menuVisibleDefault = menu,
    requiresAuth = requiresAuth, order = order, surface = surface, presentation = presentation,
)

internal val authManifest = NavManifest(
    "test:auth-ui", "0",
    listOf(
        dest("auth.login", "/login", LoginRoute.serializer(), PathCodec.constant(LoginRoute), menu = false, requiresAuth = false),
        dest(
            "auth.qr_confirm", "/qr-confirm", QrRoute.serializer(),
            PathCodec({ _, q -> q["key"]?.firstOrNull()?.let(::QrRoute) }, { RouteAddress(query = mapOf("key" to listOf(it.key))) }),
            menu = false, requiresAuth = true, surface = Surface.Public, order = 1,
        ),
        dest(
            "auth.security", "security", SecurityRoute.serializer(), PathCodec.constant(SecurityRoute),
            placement = Placement.Submenu(DestinationId("profiles.settings")), order = 10,
        ),
    ),
)

internal val appManifest = NavManifest(
    "test:app-ui", "0",
    listOf(
        dest("feed.home", "/feed", FeedRoute.serializer(), PathCodec.constant(FeedRoute), requiresAuth = false, surface = Surface.Public, order = 1),
        dest("chat.inbox", "chat", InboxRoute.serializer(), PathCodec.constant(InboxRoute), order = 10),
        dest(
            "chat.thread", "t/:threadId", ThreadRoute.serializer(),
            PathCodec({ p, _ -> p["threadId"]?.let(::ThreadRoute) }, { RouteAddress(path = mapOf("threadId" to it.threadId)) }),
            placement = Placement.Submenu(DestinationId("chat.inbox")), menu = false, order = 1,
        ),
        dest("profiles.settings", "settings", SettingsRoute.serializer(), PathCodec.constant(SettingsRoute), order = 90),
        dest(
            "listings.detail", "/l/:id", ItemRoute.serializer(),
            PathCodec(
                { p, q -> p["id"]?.let { ItemRoute(it, q["tab"]?.firstOrNull()) } },
                { RouteAddress(path = mapOf("id" to it.id), query = it.tab?.let { t -> mapOf("tab" to listOf(t)) } ?: emptyMap()) },
            ),
            menu = false, requiresAuth = false, surface = Surface.Public, order = 5,
        ),
        dest("x.confirm", "confirm", ConfirmRoute.serializer(), PathCodec.constant(ConfirmRoute), menu = false, order = 200, presentation = Presentation.Dialog),
        dest(
            "account.privacy", "privacy", PrivacyRoute.serializer(), PathCodec.constant(PrivacyRoute),
            placement = Placement.Submenu(DestinationId("account.root")), order = 5,
        ),
    ),
)

internal val testNav: ResolvedNav = resolveNav(listOf(authManifest, appManifest))

internal val redirectPolicy = NavPolicy(signIn = DestinationId("auth.login"), home = DestinationId("chat.inbox"))
internal val wallPolicy = NavPolicy(signIn = DestinationId("auth.login"), unauthenticated = UnauthenticatedMode.Wall)

internal fun NavState.visibleRoutes(): List<Route> = visible.map { it.route }

internal fun reduceAll(start: NavState, policy: NavPolicy, vararg commands: NavCommand): NavReduction =
    commands.fold(NavReduction(start)) { acc, c -> reduceNav(acc.state, c, testNav, policy) }
