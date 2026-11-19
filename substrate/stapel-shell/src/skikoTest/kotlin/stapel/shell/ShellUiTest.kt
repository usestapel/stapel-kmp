package stapel.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import stapel.navigation.CapabilityKey
import stapel.navigation.DestinationId
import stapel.navigation.EmbedSurface
import stapel.navigation.NavAuthSource
import stapel.navigation.NavAuthState
import stapel.navigation.NavIcons
import stapel.navigation.NavPolicy
import stapel.navigation.PathCodec
import stapel.navigation.Placement
import stapel.navigation.Route
import stapel.navigation.StapelNavigator
import stapel.navigation.Surface
import stapel.navigation.WidthClass
import stapel.navigation.compose.NavContribution
import stapel.navigation.compose.navContribution
import stapel.navigation.compose.rememberStapelNavigator
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
import stapel.ui.StapelInput
import stapel.ui.StapelTheme
import stapel.ui.skin.FoundationSkin
import stapel.ui.stapelLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Serializable data object ARoute : Route
@Serializable data object BRoute : Route
@Serializable data object CRoute : Route
@Serializable data object DRoute : Route
@Serializable data object ERoute : Route
@Serializable data object FRoute : Route
@Serializable data object FeedRoute : Route
@Serializable data object PrivacyRoute : Route
@Serializable data object CameraRoute : Route

private fun app(): NavContribution = navContribution("test:app-ui", "0") {
    destination<FeedRoute>(id = "t.feed", path = "/feed", labelKey = "t.feed", icon = NavIcons.Home, codec = PathCodec.constant(FeedRoute), requiresAuth = false, surface = Surface.Public, order = 0) { BasicText("feed", Modifier.testTag("screen-feed")) }
    destination<ARoute>(id = "t.a", path = "a", labelKey = "t.a", icon = NavIcons.Message, codec = PathCodec.constant(ARoute), order = 1) { BasicText("a", Modifier.testTag("screen-a")) }
    destination<BRoute>(id = "t.b", path = "b", labelKey = "t.b", icon = NavIcons.Bell, codec = PathCodec.constant(BRoute), order = 2) { BasicText("b", Modifier.testTag("screen-b")) }
    destination<CRoute>(id = "t.c", path = "c", labelKey = "t.c", icon = NavIcons.Search, codec = PathCodec.constant(CRoute), order = 3) { BasicText("c", Modifier.testTag("screen-c")) }
    destination<DRoute>(id = "t.d", path = "d", labelKey = "t.d", icon = NavIcons.Tag, codec = PathCodec.constant(DRoute), order = 4) { BasicText("d", Modifier.testTag("screen-d")) }
    destination<ERoute>(id = "t.e", path = "e", labelKey = "t.e", icon = NavIcons.Clock, codec = PathCodec.constant(ERoute), order = 5) { BasicText("e", Modifier.testTag("screen-e")) }
    destination<FRoute>(id = "t.f", path = "f", labelKey = "t.f", icon = NavIcons.Heart, codec = PathCodec.constant(FRoute), order = 6) { BasicText("f", Modifier.testTag("screen-f")) }
    destination<PrivacyRoute>(
        id = "t.privacy", path = "privacy", labelKey = "t.privacy", icon = NavIcons.ShieldCheck, codec = PathCodec.constant(PrivacyRoute),
        placement = Placement.Submenu(DestinationId("account.root")),
    ) { BasicText("privacy", Modifier.testTag("screen-privacy")) }
    destination<CameraRoute>(
        id = "t.camera", path = "/camera", labelKey = "t.camera", icon = NavIcons.Apps, codec = PathCodec.constant(CameraRoute),
        menuVisibleDefault = false, requiresAuth = false, requires = setOf(CapabilityKey("camera")),
    ) { BasicText("camera") }
    embed("e.second", Slots.TopbarEnd, order = 2) { BasicText("second", Modifier.testTag("e.second")) }
    embed("e.first", Slots.TopbarEnd, order = 1) { BasicText("first", Modifier.testTag("e.first")) }
    embed("e.member", Slots.TopbarStart, order = 1, surface = EmbedSurface.Member) { BasicText("member menu", Modifier.testTag("e.member")) }
    embed("e.desktop", Slots.SidenavHead, order = 1, widths = setOf(WidthClass.Desktop)) { BasicText("brand", Modifier.testTag("e.desktop")) }
    embed("e.unplanned", Slots.TopbarEnd, order = 9) { BasicText("unplanned", Modifier.testTag("e.unplanned")) }
    embed("e.sheetfoot", Slots.SheetFoot, order = 1) { BasicText("legal", Modifier.testTag("e.sheetfoot")) }
}

@OptIn(ExperimentalTestApi::class)
private class ShellHarness(val test: ComposeUiTest, auth: NavAuthState = NavAuthState.Member, maxTabs: Int = 5) {
    val authFlow = MutableStateFlow(auth)
    var width by mutableStateOf(390.dp)
    val badge = MutableStateFlow(0)
    lateinit var navigator: StapelNavigator
    private val contribution = app()
    private val plan = SlotPlan.offered(ShellTemplate.AdaptiveTabs, listOf(contribution)).let { offered ->
        SlotPlan(offered.filled.mapValues { (_, ids) -> ids.filter { it.value != "e.unplanned" } })
    }
    val config = ShellConfig(
        contributions = listOf(contribution),
        slots = plan,
        policy = NavPolicy(signIn = null, maxTabs = maxTabs),
        auth = NavAuthSource { authFlow },
        badges = mapOf(DestinationId("t.b") to BadgeSource { badge }),
        label = { it },
    )

    fun start() {
        test.setContent {
            val layout = stapelLayout(StapelDefaultTokens, width, StapelInput.Touch, 44.dp)
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, layout = layout) {
                Box(Modifier.size(width, 800.dp)) {
                    val n = rememberStapelNavigator(config.contributions, config.policy, config.auth)
                    navigator = n
                    StapelShell(n, config)
                }
            }
        }
        test.waitForIdle()
    }

    fun at(w: Dp) {
        width = w
        test.waitForIdle()
    }

    fun exists(tag: String): Boolean = test.onAllNodesWithTagCompat(tag) > 0
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.onAllNodesWithTagCompat(tag: String): Int =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

@OptIn(ExperimentalTestApi::class)
class ShellUiTest {
    @Test
    fun theChromeFollowsTheTokenBreakpoints() = runComposeUiTest {
        val h = ShellHarness(this)
        h.start()
        assertTrue(h.exists(ShellTags.BottomBar) && !h.exists(ShellTags.Rail) && !h.exists(ShellTags.Drawer), "phone: bar")
        h.at(900.dp)
        assertTrue(!h.exists(ShellTags.BottomBar) && h.exists(ShellTags.Rail) && !h.exists(ShellTags.Drawer), "tablet: rail")
        h.at(1400.dp)
        assertTrue(!h.exists(ShellTags.BottomBar) && !h.exists(ShellTags.Rail) && h.exists(ShellTags.Drawer), "desktop: drawer")
        h.at(767.dp)
        assertTrue(h.exists(ShellTags.BottomBar), "767 dp is still a phone (tablet starts at 768)")
    }

    @Test
    fun overflowTabsMoveBehindMoreOnAPhone() = runComposeUiTest {
        val h = ShellHarness(this)
        h.start()
        // feed, a, b, c, d, e, f, account.root = 8 tabs; 4 in the bar + More.
        val inBar = { label: String -> hasText(label) and hasAnyAncestor(hasTestTag(ShellTags.BottomBar)) }
        for (label in listOf("t.feed", "t.a", "t.b", "t.c", "shell.nav.more")) onNode(inBar(label)).assertIsDisplayed()
        assertEquals(0, onAllNodes(inBar("t.d")).fetchSemanticsNodes().size)
        onNodeWithText("shell.nav.more").performClick()
        waitForIdle()
        onNodeWithTag(ShellTags.MoreSheet).assertIsDisplayed()
        onNodeWithTag("e.sheetfoot").assertIsDisplayed()
        onNodeWithTag("stapel.shell.more.t.e").performClick()
        waitForIdle()
        onNodeWithTag("screen-e").assertIsDisplayed()
        assertEquals(DestinationId("t.e"), h.navigator.state.value.selectedTab)
        assertTrue(!h.exists(ShellTags.MoreSheet))
        h.at(900.dp)
        onNode(hasText("t.f") and hasAnyAncestor(hasTestTag(ShellTags.Rail))).assertIsDisplayed() // a rail shows every tab, no More
    }

    @Test
    fun slotEmbedsRenderInOrderOnlyWhenPlannedVisibleAndAtTheirWidth() = runComposeUiTest {
        val h = ShellHarness(this)
        h.start()
        val first = onNodeWithTag("e.first").getUnclippedBoundsInRoot().left
        val second = onNodeWithTag("e.second").getUnclippedBoundsInRoot().left
        assertTrue(first < second, "order 1 before order 2")
        assertTrue(!h.exists("e.unplanned"), "an embed the plan does not list is not rendered")
        assertTrue(!h.exists("e.desktop"), "a desktop-only embed is not on a phone")
        h.at(1400.dp)
        assertTrue(h.exists("e.desktop"))
    }

    @Test
    fun aMemberOnlyEmbedIsHiddenWhenSignedOut() = runComposeUiTest {
        val h = ShellHarness(this, auth = NavAuthState.Anonymous)
        h.start()
        onNodeWithTag("screen-feed").assertIsDisplayed()
        assertTrue(!h.exists("e.member"))
        h.authFlow.value = NavAuthState.Member
        waitForIdle()
        assertTrue(h.exists("e.member"))
    }

    @Test
    fun badgesShowTheLiveCount() = runComposeUiTest {
        val h = ShellHarness(this)
        h.start()
        assertEquals(0, onAllNodesWithText("3").fetchSemanticsNodes().size)
        h.badge.value = 3
        waitForIdle()
        onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun aContainerRootLandsOnItsSectionScreen() = runComposeUiTest {
        val h = ShellHarness(this)
        h.start()
        h.at(900.dp)
        onNodeWithText("app.nav.account").performClick()
        waitForIdle()
        onNodeWithTag("stapel.shell.section.account.root").assertIsDisplayed()
        onNodeWithTag("stapel.shell.section.row.t.privacy").performClick()
        waitForIdle()
        onNodeWithTag("screen-privacy").assertIsDisplayed()
    }

    @Test
    fun theShellNamesWhatItCannotShow() = runComposeUiTest {
        val h = ShellHarness(this, auth = NavAuthState.Guest)
        h.start()
        runOnIdle { h.navigator.navigate(CameraRoute) }
        waitForIdle()
        onNodeWithTag(ShellTags.Unavailable).assertIsDisplayed()
        onNodeWithText("shell.unavailable.missing: camera").assertIsDisplayed()
        runOnIdle { h.navigator.back() }
        runOnIdle { h.navigator.navigate(ARoute) } // member destination, guest session
        waitForIdle()
        onNodeWithTag(ShellTags.Refusal).assertIsDisplayed()
        onNodeWithText("nav.no_mandate").assertIsDisplayed()
    }

    @Test
    fun theWaitScreenShowsWhileAuthIsUnresolved() = runComposeUiTest {
        val h = ShellHarness(this, auth = NavAuthState.Unresolved)
        h.start()
        onNodeWithTag(ShellTags.Waiting).assertIsDisplayed()
        h.authFlow.value = NavAuthState.Member
        waitForIdle()
        assertTrue(!h.exists(ShellTags.Waiting))
        assertTrue(h.exists(ShellTags.BottomBar))
    }
}
