package stapel.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import stapel.navigation.NavAuthSource
import stapel.navigation.NavAuthState
import stapel.navigation.NavIcons
import stapel.navigation.NavPolicy
import stapel.navigation.PathCodec
import stapel.navigation.Route
import stapel.navigation.Surface
import stapel.navigation.compose.NavContribution
import stapel.navigation.compose.navContribution
import stapel.navigation.compose.rememberStapelNavigator
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
import stapel.ui.StapelInput
import stapel.ui.StapelScreenTitle
import stapel.ui.StapelTheme
import stapel.ui.skin.FoundationSkin
import stapel.ui.stapelLayout
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable private data object TitledRoute : Route
@Serializable private data object PlainRoute : Route

private fun titled(center: Boolean): NavContribution = navContribution("test:titles", "0") {
    destination<TitledRoute>(
        id = "t.titled", path = "/titled", labelKey = "Destination label", icon = NavIcons.Home,
        codec = PathCodec.constant(TitledRoute), requiresAuth = false, surface = Surface.Public, order = 0,
    ) {
        StapelScreenTitle("Screen title", testTag = "screen.heading")
        BasicText("body", Modifier.testTag("screen.body"))
    }
    destination<PlainRoute>(
        id = "t.plain", path = "/plain", labelKey = "Plain label", icon = NavIcons.Bell,
        codec = PathCodec.constant(PlainRoute), requiresAuth = false, surface = Surface.Public, order = 1,
    ) { BasicText("plain", Modifier.testTag("screen.plain")) }
    if (center) embed("e.center", Slots.TopbarCenter, order = 1) { BasicText("search", Modifier.testTag("e.center")) }
}

/** The chrome owns the title of the screen it hosts; the screen draws its heading only without one. */
@OptIn(ExperimentalTestApi::class)
class ShellTitleTest {
    private fun androidx.compose.ui.test.ComposeUiTest.start(center: Boolean) {
        val contribution = titled(center)
        val config = ShellConfig(
            contributions = listOf(contribution),
            slots = SlotPlan.offered(ShellTemplate.AdaptiveTabs, listOf(contribution)),
            policy = NavPolicy(signIn = null),
            auth = NavAuthSource { MutableStateFlow(NavAuthState.Member) },
            label = { it },
        )
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, layout = stapelLayout(StapelDefaultTokens, 390.dp, StapelInput.Touch, 44.dp)) {
                Box(Modifier.size(390.dp, 800.dp)) {
                    StapelShell(rememberStapelNavigator(config.contributions, config.policy, config.auth), config)
                }
            }
        }
        waitForIdle()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.count(text: String): Int =
        onAllNodes(hasText(text)).fetchSemanticsNodes().size

    @Test
    fun theTopBarShowsTheScreensTitleAndTheScreenDrawsNoSecondHeading() = runComposeUiTest {
        start(center = false)
        // The bar's title is the screen's own (the destination label stays on the tab).
        onNodeWithTag(ShellTags.Title).assertTextEquals("Screen title")
        assertEquals(1, count("Screen title"), "the title is drawn once")
        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasTestTag("screen.heading")).fetchSemanticsNodes().size)
    }

    @Test
    fun aCenterEmbedInTheBarLeavesTheTitleToTheScreen() = runComposeUiTest {
        start(center = true)
        onNodeWithTag("screen.heading").assertTextEquals("Screen title")
        assertEquals(0, onAllNodes(androidx.compose.ui.test.hasTestTag(ShellTags.Title)).fetchSemanticsNodes().size)
        assertEquals(1, count("Screen title"))
    }
}
