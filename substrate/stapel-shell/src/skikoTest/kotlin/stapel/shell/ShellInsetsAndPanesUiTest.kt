package stapel.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import stapel.navigation.DestinationId
import stapel.navigation.NavAuthSource
import stapel.navigation.NavAuthState
import stapel.navigation.NavIcons
import stapel.navigation.NavPolicy
import stapel.navigation.PaneRole
import stapel.navigation.PathCodec
import stapel.navigation.Placement
import stapel.navigation.Route
import stapel.navigation.RouteAddress
import stapel.navigation.StapelNavigator
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

@Serializable data object InsetHomeRoute : Route
@Serializable data object InsetOtherRoute : Route
@Serializable data object InboxListRoute : Route
@Serializable data class InboxItemRoute(val id: String) : Route

/** Fake system insets: distinct per side, so a side applied twice or not at all shows in the bounds. */
private val FAKE = WindowInsets(left = 10.dp, top = 24.dp, right = 12.dp, bottom = 16.dp)
// Below the test window's height, so the shell's box is exactly this tall.
private val HEIGHT = 700.dp

private val contribution = navContribution("test:insets-ui", "0") {
    // A screen that applies the same insets itself, the way StapelPage applies the system ones.
    val page: @androidx.compose.runtime.Composable () -> Unit = {
        Box(Modifier.fillMaxSize().windowInsetsPadding(FAKE)) { Box(Modifier.fillMaxSize().testTag("inner")) }
    }
    destination<InsetHomeRoute>(id = "i.home", path = "home", labelKey = "i.home", icon = NavIcons.Home, codec = PathCodec.constant(InsetHomeRoute), order = 1) { page() }
    destination<InsetOtherRoute>(id = "i.other", path = "other", labelKey = "i.other", icon = NavIcons.Bell, codec = PathCodec.constant(InsetOtherRoute), order = 2) { page() }
    destination<InboxListRoute>(
        id = "i.inbox", path = "inbox", labelKey = "i.inbox", icon = NavIcons.Message, codec = PathCodec.constant(InboxListRoute),
        menuVisibleDefault = false, pane = PaneRole.List,
    ) { BasicText("inbox", Modifier.testTag("inbox-list")) }
    destination<InboxItemRoute>(
        id = "i.item", path = "m/:id", labelKey = "i.item", icon = NavIcons.Message,
        codec = PathCodec({ p, _ -> p["id"]?.let(::InboxItemRoute) }, { RouteAddress(path = mapOf("id" to it.id)) }),
        placement = Placement.Submenu(DestinationId("i.inbox")), menuVisibleDefault = false, pane = PaneRole.Detail,
    ) { BasicText("item ${it.id}", Modifier.testTag("inbox-item")) }
}

@OptIn(ExperimentalTestApi::class)
private class InsetsHarness(val test: ComposeUiTest, template: ShellTemplate = ShellTemplate.AdaptiveTabs) {
    var width by mutableStateOf(390.dp)
    lateinit var navigator: StapelNavigator
    val config = ShellConfig(
        contributions = listOf(contribution),
        template = template,
        slots = SlotPlan(emptyMap()),
        policy = NavPolicy(signIn = null),
        auth = NavAuthSource { MutableStateFlow(NavAuthState.Member) },
        label = { it },
    )

    fun start() {
        test.setContent {
            val layout = stapelLayout(StapelDefaultTokens, width, StapelInput.Touch, 44.dp)
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, layout = layout) {
                Box(Modifier.size(width, HEIGHT)) {
                    val n = rememberStapelNavigator(config.contributions, config.policy, config.auth)
                    navigator = n
                    StapelShell(n, config, windowInsets = FAKE)
                }
            }
        }
        test.waitForIdle()
    }

    fun at(w: Dp) {
        width = w
        test.waitForIdle()
    }

    fun bounds(tag: String): DpRect = test.onNodeWithTag(tag).getUnclippedBoundsInRoot()
}

private fun assertNear(expected: Dp, actual: Dp, what: String) =
    assertTrue((expected - actual).value.let { it > -0.5f && it < 0.5f }, "$what: expected $expected, was $actual")

@OptIn(ExperimentalTestApi::class)
class ShellInsetsAndPanesUiTest {
    @Test
    fun phoneBarAndTopBarKeepTheInsetsClearAndConsumeThemForTheScreen() = runComposeUiTest {
        val h = InsetsHarness(this)
        h.start()
        val top = h.bounds(ShellTags.TopBar)
        assertNear(24.dp, top.top, "top bar below the status bar")
        assertNear(10.dp, top.left, "top bar clear of the left inset")
        assertNear(390.dp - 12.dp, top.right, "top bar clear of the right inset")
        val bar = h.bounds(ShellTags.BottomBar)
        assertNear(HEIGHT - 16.dp, bar.bottom, "bottom bar above the navigation bar")
        assertNear(10.dp, bar.left, "bottom bar clear of the left inset")
        val content = h.bounds(ShellTags.Content)
        val inner = h.bounds("inner")
        assertEquals(content, inner, "the screen's own inset padding adds nothing: the chrome consumed every side")
    }

    @Test
    fun railAndDrawerTakeTheStartSideAndTheTopBarTheRest() = runComposeUiTest {
        val h = InsetsHarness(this)
        h.start()
        for ((w, tag) in listOf(900.dp to ShellTags.Rail, 1400.dp to ShellTags.Drawer)) {
            h.at(w)
            val side = h.bounds(tag)
            // The drawer's surface runs to the window edge; its items keep clear. The rail is padded whole.
            val firstItem = if (tag == ShellTags.Drawer) h.bounds("stapel.shell.drawer.i.home") else side
            assertTrue(firstItem.top >= 24.dp - 0.5.dp, "$tag content below the status bar: ${firstItem.top}")
            assertTrue(firstItem.left >= 10.dp - 0.5.dp, "$tag content clear of the start inset: ${firstItem.left}")
            val top = h.bounds(ShellTags.TopBar)
            assertNear(24.dp, top.top, "top bar beside the $tag below the status bar")
            assertNear(side.right, top.left, "no second start inset beside the $tag")
            // The test window may be narrower than w; the content area ends at the window's edge.
            assertNear(h.bounds(ShellTags.Content).right - 12.dp, top.right, "top bar clear of the end inset beside the $tag")
            val content = h.bounds(ShellTags.Content)
            val inner = h.bounds("inner")
            assertEquals(DpRect(content.left, content.top, content.right, inner.bottom), inner, "$tag: top and sides consumed")
            assertNear(content.bottom - 16.dp, inner.bottom, "$tag: no bar below, so the screen keeps the bottom inset")
        }
    }

    @Test
    fun withoutABottomBarTheBottomInsetStaysWithTheScreen() = runComposeUiTest {
        val h = InsetsHarness(this, ShellTemplate.Stack)
        h.start()
        assertEquals(0, onAllNodesWithTag(ShellTags.BottomBar).fetchSemanticsNodes().size)
        val content = h.bounds(ShellTags.Content)
        val inner = h.bounds("inner")
        assertEquals(content.top, inner.top, "the top bar consumed the top")
        assertEquals(content.left, inner.left, "the top bar consumed the sides")
        assertNear(content.bottom - 16.dp, inner.bottom, "the screen keeps the bottom inset")
    }

    @Test
    fun listAndDetailSitSideBySideFromTabletWidthAndOneAtATimeOnAPhone() = runComposeUiTest {
        val h = InsetsHarness(this)
        h.start()
        runOnIdle { h.navigator.navigate(InboxListRoute) }
        waitForIdle()
        assertEquals(0, onAllNodesWithTag(ShellTags.DetailPlaceholder).fetchSemanticsNodes().size, "phone: no placeholder")
        h.at(900.dp)
        onNodeWithTag("inbox-list").assertIsDisplayed()
        onNodeWithTag(ShellTags.DetailPlaceholder).assertIsDisplayed()
        runOnIdle { h.navigator.navigate(InboxItemRoute("5")) }
        waitForIdle()
        onNodeWithTag("inbox-list").assertIsDisplayed()
        onNodeWithTag("inbox-item").assertIsDisplayed()
        assertTrue(h.bounds("inbox-list").right <= h.bounds("inbox-item").left, "list beside detail")
        h.at(390.dp)
        onNodeWithTag("inbox-item").assertIsDisplayed()
        assertEquals(0, onAllNodesWithTag("inbox-list").fetchSemanticsNodes().size, "phone: the detail alone")
        runOnIdle { h.navigator.back() }
        waitForIdle()
        onNodeWithTag("inbox-list").assertIsDisplayed()
    }
}
