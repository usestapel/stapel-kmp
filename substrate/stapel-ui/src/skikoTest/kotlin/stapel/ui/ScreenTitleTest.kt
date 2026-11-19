package stapel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
import stapel.ui.skin.FoundationSkin
import kotlin.test.Test
import kotlin.test.assertEquals

/** A screen's title is drawn once: by the chrome hosting it, or as the screen's own heading when bare. */
@OptIn(ExperimentalTestApi::class)
class ScreenTitleTest {
    private fun ComposeUiTest.themed(content: @Composable () -> Unit) {
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, layout = stapelLayout(StapelDefaultTokens, 390.dp, StapelInput.Touch, 44.dp)) {
                Box(Modifier.size(390.dp, 800.dp)) { content() }
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.count(text: String): Int = onAllNodes(hasText(text)).fetchSemanticsNodes().size

    @Test
    fun hostedBareTheScreenDrawsItsHeading() = runComposeUiTest {
        themed { StapelPage { StapelScreenTitle("Sign in", testTag = "title") } }
        onNodeWithTag("title").assertTextEquals("Sign in").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        assertEquals(1, count("Sign in"))
    }

    @Test
    fun aTitledPageShowsTheScreensTitleInItsBarOnce() = runComposeUiTest {
        themed { StapelPage(title = "Page") { StapelScreenTitle("Sign in", testTag = "title") } }
        onNodeWithTag(STAPEL_PAGE_TOP_BAR_TAG, useUnmergedTree = true).assertExists()
        assertEquals(1, count("Sign in"), "drawn once, in the bar")
        assertEquals(0, count("Page"), "the screen's title replaces the page's")
        assertEquals(0, onAllNodes(hasTestTag("title")).fetchSemanticsNodes().size)
    }

    @Test
    fun anExplicitChromeTitleTakesThePublishedTitleAndDropsItWhenTheScreenLeaves() = runComposeUiTest {
        val chrome = StapelChromeTitle()
        var shown by mutableStateOf(true)
        themed {
            CompositionLocalProvider(LocalStapelChromeTitle provides chrome) {
                if (shown) StapelScreenTitle("Sessions")
            }
        }
        assertEquals("Sessions", chrome.current)
        assertEquals(0, count("Sessions"))
        shown = false
        waitForIdle()
        assertEquals(null, chrome.current)
    }

    @Test
    fun aTitledPageUnderAnOuterChromeHandsItsTitleUpAndDrawsNoSecondBar() = runComposeUiTest {
        val chrome = StapelChromeTitle()
        themed {
            CompositionLocalProvider(LocalStapelChromeTitle provides chrome) {
                StapelPage(title = "Orders", actions = listOf(stapel.ui.skin.IconButtonSpec(StapelIconIds.Search, "Search", {}))) {
                    StapelText("body")
                }
            }
        }
        assertEquals("Orders", chrome.current)
        assertEquals(0, count("Orders"), "the outer chrome draws it")
        assertEquals(0, onAllNodes(hasTestTag(STAPEL_PAGE_TOP_BAR_TAG), useUnmergedTree = true).fetchSemanticsNodes().size)
        onNodeWithTag(STAPEL_PAGE_ACTIONS_TAG).assertExists()
    }
}
