package stapel.navigation.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import stapel.mvi.Presenter
import stapel.mvi.PresenterContext
import stapel.mvi.compose.PresenterFactory
import stapel.mvi.compose.rememberPresenter
import stapel.navigation.CapabilityKey
import stapel.navigation.DestinationId
import stapel.navigation.FlowId
import stapel.navigation.NavAuthState
import stapel.navigation.NavIcons
import stapel.navigation.NavPolicy
import stapel.navigation.PaneRole
import stapel.navigation.PathCodec
import stapel.navigation.Placement
import stapel.navigation.Presentation
import stapel.navigation.ResultKey
import stapel.navigation.RouteAddress
import stapel.navigation.SlotId
import kotlin.reflect.KClass

@Serializable data object HomeRoute : stapel.navigation.Route
@Serializable data object ProfileRoute : stapel.navigation.Route
@Serializable data class DetailRoute(val id: String) : stapel.navigation.Route
@Serializable data object PickerRoute : stapel.navigation.Route
@Serializable data object ConfirmRoute : stapel.navigation.Route
@Serializable data object FilterRoute : stapel.navigation.Route
@Serializable data object CameraRoute : stapel.navigation.Route
@Serializable data object WizardRoute : stapel.navigation.Route
@Serializable data object PrivacyRoute : stapel.navigation.Route
@Serializable data object OrderCartRoute : stapel.navigation.Route
@Serializable data object OrderPayRoute : stapel.navigation.Route
@Serializable data object MailRoute : stapel.navigation.Route
@Serializable data class MailItemRoute(val id: String) : stapel.navigation.Route

val HOME = DestinationId("t.home")
val PROFILE = DestinationId("t.profile")
val PICKED = ResultKey("t.picked", String.serializer())
val ORDER = FlowId("t.order")
val ORDERED = ResultKey("t.ordered", String.serializer())

/** How often each presenter's scope ended (cleared), by presenter state: "cleared exactly once". */
val presenterClears: MutableMap<String, Int> = HashMap()

/** Presenter of the detail screen: records its lifetime. */
class DetailPresenter(val context: PresenterContext, val id: String) : Presenter<String, Unit, Unit>(id, context) {
    init {
        context.scope.coroutineContext[kotlinx.coroutines.Job]!!.invokeOnCompletion { presenterClears[id] = (presenterClears[id] ?: 0) + 1 }
    }

    override suspend fun handle(intent: Unit) = Unit
}

class RecordingFactory : PresenterFactory {
    val created = mutableListOf<DetailPresenter>()

    override fun <P : Presenter<*, *, *>> create(type: KClass<P>, context: PresenterContext, args: List<Any?>): P {
        @Suppress("UNCHECKED_CAST")
        return DetailPresenter(context, args.single() as String).also { created += it } as P
    }
}

@Composable
private fun Counter(tag: String) {
    var count by rememberSaveable { mutableIntStateOf(0) }
    BasicText("$tag:$count", Modifier.testTag("$tag-count").clickable { count++ })
}

val testContribution: NavContribution = navContribution("test:app-ui", "0") {
    destination<HomeRoute>(
        id = "t.home", path = "home", labelKey = "home", icon = NavIcons.Home, codec = PathCodec.constant(HomeRoute), order = 1,
    ) {
        Column {
            BasicText("home", Modifier.testTag("screen-home"))
            BasicText("open 7", Modifier.testTag("open-7").clickable { navigator.navigate(DetailRoute("7")) })
            Counter("home")
        }
    }
    destination<ProfileRoute>(
        id = "t.profile", path = "profile", labelKey = "profile", icon = NavIcons.User, codec = PathCodec.constant(ProfileRoute), order = 2,
    ) {
        Column {
            BasicText("profile", Modifier.testTag("screen-profile"))
            PageSlot(SlotId("page.actions"), Modifier)
        }
    }
    destination<DetailRoute>(
        id = "t.detail", path = "d/:id", labelKey = "detail", icon = NavIcons.Profile,
        codec = PathCodec({ p, _ -> p["id"]?.let(::DetailRoute) }, { RouteAddress(path = mapOf("id" to it.id)) }),
        placement = Placement.Submenu(HOME), menuVisibleDefault = false,
    ) { route ->
        val presenter = rememberPresenter<DetailPresenter>(route.id)
        var picked by rememberSaveable { androidx.compose.runtime.mutableStateOf("none") }
        ResultEffect(PICKED) { picked = it }
        ResultEffect(ORDERED) { picked = "order $it" }
        Column {
            BasicText("detail ${presenter.state.value}", Modifier.testTag("screen-detail"))
            BasicText("picked $picked", Modifier.testTag("picked"))
            Counter("detail")
        }
    }
    destination<PickerRoute>(
        id = "t.picker", path = "/pick", labelKey = "pick", icon = NavIcons.Search, codec = PathCodec.constant(PickerRoute),
        menuVisibleDefault = false,
    ) {
        BasicText("blue", Modifier.testTag("pick-blue").clickable { navigator.backWithResult(PICKED, "blue") })
    }
    destination<ConfirmRoute>(
        id = "t.confirm", path = "/confirm", labelKey = "confirm", icon = NavIcons.Apps, codec = PathCodec.constant(ConfirmRoute),
        menuVisibleDefault = false, presentation = Presentation.Dialog,
    ) { BasicText("confirm?", Modifier.testTag("dialog")) }
    destination<FilterRoute>(
        id = "t.filter", path = "/filter", labelKey = "filter", icon = NavIcons.Apps, codec = PathCodec.constant(FilterRoute),
        menuVisibleDefault = false, presentation = Presentation.Sheet,
    ) { BasicText("filters", Modifier.testTag("sheet")) }
    destination<CameraRoute>(
        id = "t.camera", path = "/camera", labelKey = "camera", icon = NavIcons.Apps, codec = PathCodec.constant(CameraRoute),
        menuVisibleDefault = false, requires = setOf(CapabilityKey("camera")),
    ) { BasicText("camera", Modifier.testTag("camera")) }
    destination<WizardRoute>(
        id = "t.wizard", path = "/wizard", labelKey = "wizard", icon = NavIcons.Apps, codec = PathCodec.constant(WizardRoute),
        menuVisibleDefault = false, flow = FlowId("t.wizard"),
    ) {
        var step by rememberSaveable { mutableIntStateOf(1) }
        NavBackInterceptor(enabled = step > 1) { step-- }
        BasicText("step $step", Modifier.testTag("wizard").clickable { step++ })
    }
    destination<PrivacyRoute>(
        id = "t.privacy", path = "privacy", labelKey = "t.privacy.label", icon = NavIcons.ShieldCheck,
        codec = PathCodec.constant(PrivacyRoute), placement = Placement.Submenu(DestinationId("account.root")),
    ) { BasicText("privacy", Modifier.testTag("screen-privacy")) }
    destination<OrderCartRoute>(
        id = "t.order_cart", path = "/order/cart", labelKey = "cart", icon = NavIcons.Cart, codec = PathCodec.constant(OrderCartRoute),
        menuVisibleDefault = false, flow = ORDER,
    ) {
        val presenter = rememberPresenter<DetailPresenter>("order")
        Column {
            BasicText("cart ${presenter.state.value}", Modifier.testTag("order-cart"))
            Counter("cart")
        }
    }
    destination<OrderPayRoute>(
        id = "t.order_pay", path = "/order/pay", labelKey = "pay", icon = NavIcons.Cart, codec = PathCodec.constant(OrderPayRoute),
        menuVisibleDefault = false, flow = ORDER,
    ) {
        val presenter = rememberPresenter<DetailPresenter>("order")
        BasicText("pay ${presenter.state.value}", Modifier.testTag("order-pay").clickable { navigator.finishFlowWithResult(ORDERED, "42") })
    }
    destination<MailRoute>(
        id = "t.mail", path = "mail", labelKey = "mail", icon = NavIcons.Message, codec = PathCodec.constant(MailRoute),
        menuVisibleDefault = false, pane = PaneRole.List, order = 5,
    ) {
        Column {
            BasicText("mail list", Modifier.testTag("mail-list"))
            BasicText("open m1", Modifier.testTag("open-m1").clickable { navigator.navigate(MailItemRoute("m1")) })
            Counter("list")
        }
    }
    destination<MailItemRoute>(
        id = "t.mail_item", path = "m/:id", labelKey = "mail item", icon = NavIcons.Message,
        codec = PathCodec({ p, _ -> p["id"]?.let(::MailItemRoute) }, { RouteAddress(path = mapOf("id" to it.id)) }),
        placement = Placement.Submenu(DestinationId("t.mail")), menuVisibleDefault = false, pane = PaneRole.Detail,
    ) { route ->
        Column {
            BasicText("mail ${route.id}", Modifier.testTag("mail-item"))
            Counter("item")
        }
    }
    embed(id = "t.profile_action", slot = SlotId("page.actions"), order = 1, scope = PROFILE) {
        BasicText("edit profile", Modifier.testTag("embed-action"))
    }
}

val memberAuth: MutableStateFlow<NavAuthState> = MutableStateFlow(NavAuthState.Member)
val testPolicy = NavPolicy(signIn = null, home = HOME)
