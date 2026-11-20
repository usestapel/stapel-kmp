package shop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.core.module.Module
import org.koin.dsl.module
import shop.generated.APP_BASE_URLS
import shop.generated.APP_ENVIRONMENT
import shop.generated.APP_POSTURE
import shop.orders.api.OrdersApi
import shop.orders.api.OrdersApiClient
import shop.theme.AppTokens
import stapel.app.di.BrickContext
import stapel.app.di.StapelApp
import stapel.app.di.StapelAppBuilder
import stapel.app.di.StapelAppContent
import stapel.app.di.stapelApp
import stapel.auth.ui.di.AuthBrick
import stapel.cdn.di.cdnModule
import stapel.profiles.ui.AvatarPicker
import stapel.profiles.ui.di.ProfilesBrick
import stapel.config.StapelEnvironment
import stapel.config.platformClientInfo
import stapel.i18n.I18nBundle
import stapel.navigation.DeepLinkConfig
import stapel.navigation.DestinationId
import stapel.navigation.NavIcons
import stapel.navigation.Placement
import stapel.navigation.PathCodec
import stapel.navigation.Route
import stapel.navigation.RouteAddress
import stapel.navigation.compose.NavContribution
import stapel.navigation.compose.navContribution
import stapel.notifications.push.di.notificationsPushModule
import stapel.notifications.push.notificationsPushRouting
import stapel.push.PushController
import stapel.push.PushOptions
import stapel.push.di.pushModule
import stapel.result.StapelResult
import stapel.runtime.PlatformContext
import stapel.ui.StapelButton
import stapel.ui.StapelPage
import stapel.ui.StapelScreenTitle
import stapel.ui.StapelText
import stapel.ui.material3.StapelMaterialTheme
import stapel.wire.ApiTransport

/** The shop's own home (a member screen). */
@Serializable
data object ShopHomeRoute : Route

/** One order, `/app/orders/<id>`. */
@Serializable
data class OrderRoute(val orderId: String) : Route

/** The customer's orders, in the account section (which the account control in the top bar opens). */
@Serializable
data object MyOrdersRoute : Route

/** The shop's own UI strings. */
private val ShopStrings = I18nBundle(
    "shop",
    mapOf(
        "en" to { mapOf("shop.nav.home" to "Home", "shop.nav.order" to "Order", "shop.nav.my_orders" to "My orders") },
        "ru" to { mapOf("shop.nav.home" to "Главная", "shop.nav.order" to "Заказ", "shop.nav.my_orders" to "Мои заказы") },
    ),
)

/**
 * The whole app, assembled once per process: the substrate, the auth and profiles bricks, the app's
 * own screens and its own `orders` backend client, the theme from `stapel.theme.json`.
 *
 * @param target `android` | `ios` | `desktop`: picks the base URL of the environment.
 * @param baseUrl overrides the environment's base URL (tests, a stand on another port).
 * @param avatarPicker the platform's picture picker for the profile avatar (the one wiring point of the
 *   upload); null = the settings screen offers no avatar change.
 * @param pushProvider the platform's push provider module (`firebasePushModule(...)`): push through
 *   stapel-push with the notifications brick's registry; taps open their deep link. Null = no push.
 */
fun createShopApp(
    platform: PlatformContext,
    target: String,
    baseUrl: String? = null,
    avatarPicker: AvatarPicker? = null,
    pushProvider: Module? = null,
    configure: StapelAppBuilder.() -> Unit = {},
): StapelApp = stapelApp(platform) {
    environment(
        StapelEnvironment.single(
            name = APP_ENVIRONMENT,
            baseUrl = baseUrl ?: APP_BASE_URLS.getValue(target),
            posture = APP_POSTURE,
            client = platformClientInfo(appId = "io.github.usestapel.sample.shop", appVersion = "0.1.0", build = "1"),
        ),
    )
    bricks(AuthBrick(), ProfilesBrick(picker = avatarPicker))
    screens(::shopScreens)
    // stapel-cdn as the app's media uploader: where the profiles avatar goes.
    modules(cdnModule(), module { single<OrdersApi> { OrdersApiClient(get<ApiTransport>()) } })
    if (pushProvider != null) {
        modules(pushModule(PushOptions(routing = notificationsPushRouting())), notificationsPushModule(), pushProvider)
    }
    navigation(
        home = DestinationId("shop.home"),
        deepLinks = DeepLinkConfig(schemes = setOf("shopmobile"), hosts = setOf("shop.example.com")),
    )
    theme { content -> StapelMaterialTheme(tokens = AppTokens, content = content) }
    strings { register(ShopStrings) }
    configure()
}.also { app -> app.koin.getOrNull<PushController>()?.attach { app.openUrl(it) } }

/** The home screen's title. */
const val SHOP_HOME_TITLE: String = "Shop"

/** The app UI. */
@Composable
fun ShopApp(app: StapelApp) {
    StapelAppContent(app)
}

/** The shop's own destinations, with the web ids and paths the composition plans. */
fun shopScreens(context: BrickContext): NavContribution = navContribution("app:shop-mobile", "0.1.0") {
    destination<ShopHomeRoute>(
        id = "shop.home", path = "home", labelKey = "shop.nav.home", icon = NavIcons.Home,
        codec = PathCodec.constant(ShopHomeRoute), requiresAuth = true, order = 0,
    ) { HomeScreen(context) }
    destination<OrderRoute>(
        id = "shop.order", path = "orders/:orderId", labelKey = "shop.nav.order", icon = NavIcons.Cart,
        codec = PathCodec(parse = { params, _ -> OrderRoute(params.getValue("orderId")) }, format = { r -> RouteAddress(path = mapOf("orderId" to r.orderId)) }),
        requiresAuth = true, menuVisibleDefault = false, order = 1,
    ) { route -> StapelPage(Modifier.testTag("shop.order")) { StapelScreenTitle("Order ${route.orderId}") } }
    // A child of the container's account section: with it the section exists, and the top bar's account
    // control offers "Account" (without the section it offers sign-out only).
    destination<MyOrdersRoute>(
        id = "shop.my_orders", path = "orders", labelKey = "shop.nav.my_orders", icon = NavIcons.Cart,
        codec = PathCodec.constant(MyOrdersRoute), placement = Placement.Submenu(DestinationId("account.root")),
        requiresAuth = true, order = 2,
    ) { StapelPage(Modifier.testTag("shop.my_orders")) { StapelScreenTitle("My orders") } }
}

@Composable
private fun HomeScreen(context: BrickContext) {
    val scope = rememberCoroutineScope()
    var orders by remember { mutableStateOf<String?>(null) }
    // Every screen titles itself once: in the shell the top bar shows it, hosted bare it is the heading.
    StapelPage(Modifier.testTag("shop.home")) {
        StapelScreenTitle(SHOP_HOME_TITLE)
        orders?.let { StapelText(it, Modifier.testTag("shop.home.orders")) }
        StapelButton("Load orders", {
            scope.launch {
                orders = when (val r = context.koin.get<OrdersApi>().listOrders()) {
                    is StapelResult.Ok -> "${r.value.size} orders"
                    is StapelResult.Err -> "orders unavailable: ${r.error.code.value}"
                }
            }
        }, testTag = "shop.home.load")
        // Push is switched on by a deliberate act, never at launch (the button is its own rationale).
        context.koin.getOrNull<PushController>()?.let { push ->
            val state by push.state.collectAsState()
            StapelText("Notifications: ${state.permission} / ${state.registration}", Modifier.testTag("shop.home.push.state"))
            if (!state.enabled) StapelButton("Turn on notifications", { scope.launch { push.enable() } }, testTag = "shop.home.push")
        }
    }
}
