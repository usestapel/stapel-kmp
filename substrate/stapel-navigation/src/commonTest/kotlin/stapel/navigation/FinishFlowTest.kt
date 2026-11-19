@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.navigation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import stapel.navigation.NavAuthState.Anonymous
import stapel.navigation.NavAuthState.Member
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@Serializable data object FlLogin : Route
@Serializable data object FlCode : Route
@Serializable data object FlInbox : Route
@Serializable data object FlFeed : Route
@Serializable data class FlThread(val id: String) : Route
@Serializable data object FlCart : Route
@Serializable data object FlPay : Route
@Serializable data object FlHelp : Route
@Serializable data object FlPick : Route
@Serializable data object FlPickMore : Route
@Serializable data object FlNotice : Route
@Serializable data object FlOnboard : Route
@Serializable data object FlOnboardMore : Route

private val SIGN_IN = FlowId("t.sign_in")
private val CHECKOUT = FlowId("t.checkout")
private val PICK = FlowId("t.pick")
private val ONBOARD = FlowId("t.onboard")
private val INBOX = DestinationId("f.inbox")
private val FEED = DestinationId("f.feed")
private val ONBOARD_TAB = DestinationId("f.onboard")

private inline fun <reified R : Route> flowDest(
    id: String,
    path: String,
    serializer: kotlinx.serialization.KSerializer<R>,
    codec: PathCodec<R>,
    flow: FlowId? = null,
    placement: Placement = Placement.Top,
    menu: Boolean = false,
    requiresAuth: Boolean = true,
    surface: Surface? = null,
    order: Int = 0,
    presentation: Presentation = Presentation.Screen,
): DestinationSpec<R> = DestinationSpec(
    id = DestinationId(id), routeClass = R::class, serializer = serializer, path = PathPattern(path), codec = codec,
    labelKey = id, icon = NavIcons.Apps, placement = placement, menuVisibleDefault = menu, requiresAuth = requiresAuth,
    order = order, surface = surface, presentation = presentation, flow = flow,
)

private val flowManifest = NavManifest(
    "test:flows", "0",
    listOf(
        flowDest("f.login", "/login", FlLogin.serializer(), PathCodec.constant(FlLogin), flow = SIGN_IN, requiresAuth = false),
        flowDest("f.code", "/login-code", FlCode.serializer(), PathCodec.constant(FlCode), flow = SIGN_IN, requiresAuth = false),
        flowDest("f.feed", "/feed", FlFeed.serializer(), PathCodec.constant(FlFeed), menu = true, requiresAuth = false, surface = Surface.Public, order = 1),
        flowDest("f.inbox", "chat", FlInbox.serializer(), PathCodec.constant(FlInbox), menu = true, order = 2),
        flowDest("f.onboard", "onboard", FlOnboard.serializer(), PathCodec.constant(FlOnboard), flow = ONBOARD, menu = true, order = 3),
        flowDest("f.onboard_more", "/onboard-more", FlOnboardMore.serializer(), PathCodec.constant(FlOnboardMore), flow = ONBOARD),
        flowDest(
            "f.thread", "t/:id", FlThread.serializer(),
            PathCodec({ p, _ -> p["id"]?.let(::FlThread) }, { RouteAddress(path = mapOf("id" to it.id)) }),
            placement = Placement.Submenu(DestinationId("f.inbox")),
        ),
        flowDest("f.cart", "/cart", FlCart.serializer(), PathCodec.constant(FlCart), flow = CHECKOUT),
        flowDest("f.pay", "/pay", FlPay.serializer(), PathCodec.constant(FlPay), flow = CHECKOUT),
        flowDest("f.help", "/help", FlHelp.serializer(), PathCodec.constant(FlHelp), requiresAuth = false),
        flowDest("f.pick", "/pick", FlPick.serializer(), PathCodec.constant(FlPick), flow = PICK, presentation = Presentation.Sheet),
        flowDest("f.pick_more", "/pick-more", FlPickMore.serializer(), PathCodec.constant(FlPickMore), flow = PICK),
        flowDest("f.notice", "/notice", FlNotice.serializer(), PathCodec.constant(FlNotice), presentation = Presentation.Dialog),
    ),
)

private val flowNav = resolveNav(listOf(flowManifest))
private val flowPolicy = NavPolicy(signIn = DestinationId("f.login"), home = INBOX)

private fun run(start: NavState, vararg commands: NavCommand, policy: NavPolicy = flowPolicy): NavState =
    commands.fold(start) { s, c -> reduceNav(s, c, flowNav, policy).state }

private fun member(policy: NavPolicy = flowPolicy) = initialNavState(flowNav, policy, Member)

private fun push(vararg routes: Route) = routes.map { NavCommand.Push(it) }.toTypedArray()

/** Every entry on every stack, for "the flow left everywhere" assertions. */
private fun NavState.everyEntry(): List<StackEntry> = tabStacks.values.flatten() + root + overlays

class FinishFlowTest {
    private data class Row(
        val case: String,
        val start: NavState,
        val finish: NavCommand.FinishFlow,
        val visible: List<Route>,
        val selectedTab: DestinationId?,
        val policy: NavPolicy = flowPolicy,
    )

    @Test
    fun finishFlowTable() {
        val rows = listOf(
            Row("flow at the root of a tab", run(member(), *push(FlCart, FlPay)), NavCommand.FinishFlow(CHECKOUT), listOf(FlInbox), INBOX),
            Row("current flow = the top entry's flow", run(member(), *push(FlCart, FlPay)), NavCommand.FinishFlow(), listOf(FlInbox), INBOX),
            Row(
                "screens opened above the flow leave with it",
                run(member(), *push(FlThread("1"), FlCart, FlHelp, FlPay)), NavCommand.FinishFlow(CHECKOUT), listOf(FlInbox, FlThread("1")), INBOX,
            ),
            Row("flow in an overlay: the sheet closes", run(member(), *push(FlPick, FlPickMore)), NavCommand.FinishFlow(PICK), listOf(FlInbox), INBOX),
            Row(
                "flow in an overlay above a dialog: the dialog stays",
                run(member(), *push(FlNotice, FlPick, FlPickMore)), NavCommand.FinishFlow(PICK), listOf(FlInbox, FlNotice), INBOX,
            ),
            Row(
                "flow above the tab host (a cover-like root push)",
                run(member(), NavCommand.ReplaceAll(FlCart), NavCommand.Push(FlPay)), NavCommand.FinishFlow(CHECKOUT), listOf(FlInbox), INBOX,
            ),
            Row(
                "the tab root belongs to the flow: falls back to home",
                run(member(), NavCommand.SelectTab(ONBOARD_TAB), NavCommand.Push(FlOnboardMore)), NavCommand.FinishFlow(ONBOARD), listOf(FlInbox), INBOX,
            ),
            Row(
                "the tab root belongs to the flow, home is that tab: falls back to the first other tab",
                run(member(NavPolicy(signIn = DestinationId("f.login"), home = ONBOARD_TAB)), NavCommand.Push(FlOnboardMore)),
                NavCommand.FinishFlow(ONBOARD), listOf(FlFeed), FEED,
                NavPolicy(signIn = DestinationId("f.login"), home = ONBOARD_TAB),
            ),
            Row(
                "the flow's first entry is the root of a stack without a tab host: start state",
                initialNavState(flowNav, flowPolicy, Anonymous).let { run(it, NavCommand.Push(FlCode)) },
                NavCommand.FinishFlow(SIGN_IN), listOf(FlLogin), null,
            ),
        )
        for (row in rows) {
            val out = reduceNav(row.start, row.finish, flowNav, row.policy)
            assertEquals(row.visible, out.state.visibleRoutes(), row.case)
            assertEquals(row.selectedTab, out.state.selectedTab, row.case)
            assertEquals(null, out.refused, row.case)
            val flow = row.finish.flow ?: CHECKOUT
            val flowUids = row.start.everyEntry().filter { flowNav.destinationOf(it.route)?.spec?.flow == flow }.map { it.uid }.toSet()
            assertTrue(flowUids.isNotEmpty(), row.case)
            assertTrue(out.state.everyEntry().none { it.uid in flowUids }, "${row.case}: no entry of $flow left on any stack")
        }
    }

    @Test
    fun theFallbackEntryIsAFreshInstanceNeverTheFinishedOne() {
        val start = run(initialNavState(flowNav, flowPolicy, Anonymous), NavCommand.Push(FlCode))
        val out = reduceNav(start, NavCommand.FinishFlow(SIGN_IN), flowNav, flowPolicy).state
        assertNotEquals(start.root.first().uid, out.root.single().uid, "new uid: the finished flow's state is not reused")
    }

    @Test
    fun entriesOfTheFlowInATabThatIsNotShownLeaveToo() {
        val start = run(member(), *push(FlCart, FlPay), NavCommand.SelectTab(FEED))
        val out = reduceNav(start, NavCommand.FinishFlow(CHECKOUT), flowNav, flowPolicy).state
        assertEquals(listOf<Route>(FlFeed), out.visibleRoutes(), "what is shown does not move")
        assertEquals(listOf<Route>(FlInbox), out.tabStacks.getValue(INBOX).map { it.route })
    }

    @Test
    fun aFlowOnNoStackIsANoOp() {
        val start = run(member(), NavCommand.Push(FlThread("1")))
        assertSame(start, reduceNav(start, NavCommand.FinishFlow(CHECKOUT), flowNav, flowPolicy).state)
        assertSame(start, reduceNav(start, NavCommand.FinishFlow(), flowNav, flowPolicy).state, "top entry has no flow")
    }

    // ── redirect and return: a guard opened the sign-in flow ────────────────────────────────

    private fun refusedThenSignInFlow(): NavState {
        val anon = initialNavState(flowNav, flowPolicy, Anonymous)
        val refused = reduceNav(anon, NavCommand.Push(FlThread("7")), flowNav, flowPolicy)
        assertEquals(GuardDecision.SignIn(UnauthenticatedMode.Redirect, listOf(FlInbox, FlThread("7"))), refused.refused)
        return run(refused.state, NavCommand.Push(FlCode))
    }

    @Test
    fun finishingTheSignInFlowBeforeTheSessionArrivesStillReturnsToThePendingTarget() {
        val finished = run(refusedThenSignInFlow(), NavCommand.FinishFlow(SIGN_IN))
        assertEquals(listOf(FlInbox, FlThread("7")), finished.pendingReturn, "completion keeps the target")
        val signedIn = run(finished, NavCommand.AuthChanged(Member))
        assertEquals(listOf(FlInbox, FlThread("7")), signedIn.visibleRoutes())
        assertEquals(null, signedIn.pendingReturn)
    }

    @Test
    fun theSessionArrivingFirstRemovesTheWholeSignInFlowAndFinishingIsThenANoOp() {
        val signedIn = run(refusedThenSignInFlow(), NavCommand.AuthChanged(Member))
        assertEquals(listOf(FlInbox, FlThread("7")), signedIn.visibleRoutes())
        assertSame(signedIn, reduceNav(signedIn, NavCommand.FinishFlow(SIGN_IN), flowNav, flowPolicy).state)
    }

    @Test
    fun aSessionWithoutPendingTargetRemovesEveryStepOfTheSignInFlow() {
        val anon = run(initialNavState(flowNav, flowPolicy, Anonymous), NavCommand.Push(FlCode))
        val signedIn = run(anon, NavCommand.AuthChanged(Member))
        assertEquals(listOf<Route>(FlInbox), signedIn.visibleRoutes(), "the code step does not outlive the sign-in")
    }

    @Test
    fun backInsideTheSignInFlowKeepsThePendingTargetLeavingItForgetsIt() {
        val inFlow = refusedThenSignInFlow()
        val stepBack = run(inFlow, NavCommand.Back)
        assertEquals(listOf(FlInbox, FlThread("7")), stepBack.pendingReturn, "back from the code step to the login step")
        val pushedPublic = run(stepBack, NavCommand.Push(FlHelp))
        assertEquals(listOf(FlInbox, FlThread("7")), pushedPublic.pendingReturn)
        val target = listOf<Route>(FlInbox, FlThread("7"))
        val aboveHelp = NavState(
            auth = Anonymous, root = listOf(StackEntry("a", FlHelp), StackEntry("b", FlLogin), StackEntry("c", FlCode)),
            pendingReturn = target, nextUid = 3,
        )
        val oneStepBack = run(aboveHelp, NavCommand.Back)
        assertEquals(target, oneStepBack.pendingReturn, "the login step is still on screen")
        assertEquals(null, run(oneStepBack, NavCommand.Back).pendingReturn, "the last sign-in step left: abandoned")
    }

    // ── the navigator: typed result to the screen that started the flow ─────────────────────

    private fun TestScope.owned(): CoroutineScope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))

    @Test
    fun finishFlowWithResultDeliversOnceAndOnlyWhenTheFlowWasThere() = runTest {
        val scope = owned()
        val n = StapelNavigator(flowNav, flowPolicy, { MutableStateFlow(Member) }, scope)
        val key = ResultKey("t.order", String.serializer())
        val received = mutableListOf<String>()
        val collector = launch { n.results(key).collect { received += it } }
        assertFalse(n.finishFlowWithResult(key, "stale", CHECKOUT), "no flow on the stack")
        n.navigate(FlCart)
        n.navigate(FlPay)
        assertTrue(n.finishFlowWithResult(key, "order-1"))
        assertEquals(listOf<Route>(FlInbox), n.state.value.visibleRoutes())
        assertFalse(n.finishFlow(CHECKOUT), "finishing twice changes nothing")
        advanceUntilIdle()
        assertEquals(listOf("order-1"), received)
        collector.cancel()
        scope.cancel()
    }
}
