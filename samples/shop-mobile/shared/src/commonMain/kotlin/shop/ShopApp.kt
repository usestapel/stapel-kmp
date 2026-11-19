package shop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
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
import stapel.config.StapelEnvironment
import stapel.config.platformClientInfo
import stapel.navigation.DeepLinkConfig
import stapel.navigation.DestinationId
import stapel.navigation.NavIcons
import stapel.navigation.PathCodec
import stapel.navigation.Route
import stapel.navigation.RouteAddress
import stapel.navigation.compose.NavContribution
import stapel.navigation.compose.navContribution
import stapel.result.StapelResult
import stapel.runtime.PlatformContext
import stapel.ui.StapelButton
import stapel.ui.StapelPage
import stapel.ui.StapelText
import stapel.ui.material3.StapelMaterialTheme
import stapel.wire.ApiTransport

/** The shop's own home (a member screen). */
@Serializable
data object ShopHomeRoute : Route

/** One order, `/app/orders/<id>`. */
@Serializable
data class OrderRoute(val orderId: String) : Route

/**
 * The whole app, assembled once per process: the substrate, the auth brick, the app's own screens and
 * its own `orders` backend client, the theme from `stapel.theme.json`.
 *
 * @param target `android` | `ios` | `desktop`: picks the base URL of the environment.
 * @param baseUrl overrides the environment's base URL (tests, a stand on another port).
 */
fun createShopApp(
    platform: PlatformContext,
    target: String,
    baseUrl: String? = null,
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
    bricks(ShopAuthBrick())
    screens(::shopScreens)
    modules(module { single<OrdersApi> { OrdersApiClient(get<ApiTransport>()) } })
    navigation(
        home = DestinationId("shop.home"),
        deepLinks = DeepLinkConfig(schemes = setOf("shopmobile"), hosts = setOf("shop.example.com")),
    )
    theme { content -> StapelMaterialTheme(tokens = AppTokens, content = content) }
    configure()
}

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
    ) { route -> StapelPage(Modifier.testTag("shop.order")) { StapelText("Order ${route.orderId}") } }
}

@Composable
private fun HomeScreen(context: BrickContext) {
    val scope = rememberCoroutineScope()
    var orders by remember { mutableStateOf<String?>(null) }
    StapelPage(Modifier.testTag("shop.home")) {
        StapelText("Shop")
        orders?.let { StapelText(it, Modifier.testTag("shop.home.orders")) }
        StapelButton("Load orders", {
            scope.launch {
                orders = when (val r = context.koin.get<OrdersApi>().listOrders()) {
                    is StapelResult.Ok -> "${r.value.size} orders"
                    is StapelResult.Err -> "orders unavailable: ${r.error.code.value}"
                }
            }
        }, testTag = "shop.home.load")
    }
}
