package stapel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
import stapel.ui.skin.ButtonSpec
import stapel.ui.skin.FoundationSkin
import stapel.ui.skin.LARGE_TITLE_TAG_SUFFIX
import stapel.ui.skin.PagerDot
import stapel.ui.skin.SnackbarSpec
import stapel.ui.skin.StapelSkin
import stapel.ui.skin.pagerDotWindow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class CompositesTest {
    private val countries = listOf("Austria", "Belgium", "Česko", "Denmark", "España", "France")

    private fun androidx.compose.ui.test.ComposeUiTest.themed(skin: StapelSkin = FoundationSkin, width: Int = 390, content: @androidx.compose.runtime.Composable () -> Unit) {
        setContent {
            StapelTheme(skin = skin, mode = ThemeMode.Light, layout = stapelLayout(StapelDefaultTokens, width.dp, StapelInput.Touch, 44.dp), reduceMotion = true) {
                Box(Modifier.size(width.dp, 800.dp)) { content() }
            }
        }
    }

    @Test
    fun aSingleChoicePickerSelectsAndCloses() = runComposeUiTest {
        var picked: String? = "Belgium"
        themed {
            var p by mutableStateOf(picked)
            StapelSelect("Country", countries, p, { p = it; picked = it }, { it }, "Close", testTag = "country")
        }
        onNodeWithTag("country").assert(hasText("Belgium", substring = true)).performClick()
        onNodeWithTag("country.sheet.option.1").assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
        onNodeWithTag("country.sheet.option.3").performClick()
        waitForIdle()
        assertEquals("Denmark", picked)
        onNodeWithTag("country.sheet.option.3").assertDoesNotExist()
        onNodeWithTag("country").assert(hasText("Denmark", substring = true))
    }

    @Test
    fun aMultiPickerCommitsOnlyOnDone() = runComposeUiTest {
        var picked = setOf("Austria")
        themed {
            var p by mutableStateOf(picked)
            StapelMultiSelect("Countries", countries, p, { p = it; picked = it }, { it }, "Close", "Apply", testTag = "multi")
        }
        onNodeWithTag("multi").performClick()
        onNodeWithTag("multi.sheet.option.5").performClick()
        onNodeWithTag("multi.sheet.option.0").performClick()
        waitForIdle()
        assertEquals(setOf("Austria"), picked, "taps only change the draft")
        onNodeWithText("Apply").performClick()
        waitForIdle()
        assertEquals(setOf("France"), picked)
        onNodeWithTag("multi").assert(hasText("France", substring = true))
    }

    @Test
    fun pickerSearchIsCaseAndAccentInsensitiveAndShowsNoMatches() = runComposeUiTest {
        themed {
            PickerSheet(
                "Country", countries, { it }, emptySet(), {}, {}, "Close",
                searchLabel = "Search countries", clearLabel = "Clear", noMatchesText = "No such country", testTag = "p",
            )
        }
        onNodeWithTag("p.search").performTextInput("CES")
        waitForIdle()
        onNodeWithText("Česko").assertExists()
        onNodeWithText("Austria").assertDoesNotExist()
        onNodeWithContentDescription("Clear").performClick()
        onNodeWithTag("p.search").performTextInput("zz")
        waitForIdle()
        onNodeWithText("No such country").assertExists()
    }

    @Test
    fun aHugePickerComposesOnlyVisibleRows() = runComposeUiTest {
        val many = (0 until 10_000).map { "Option $it" }
        themed { PickerSheet("Big", many, { it }, emptySet(), {}, {}, "Close", testTag = "big") }
        val composed = onAllNodesWithText("Option ", substring = true).fetchSemanticsNodes().size
        assertTrue(composed in 1..100, "a lazy list composes the visible rows only (composed $composed)")
    }

    @Test
    fun pickerMatchingFoldsCaseAndAccents() {
        assertTrue(pickerMatches("España", "espa"))
        assertTrue(pickerMatches("Ёлка", "ел"))
        assertTrue(pickerMatches("anything", "  "))
        assertTrue(!pickerMatches("France", "germ"))
    }

    @Test
    fun pagerWindowKeepsTheCurrentPageAndShrinksCutEdges() {
        assertEquals((0..4).map { PagerDot(it, false) }, pagerDotWindow(5, 2, 7))
        val w = pagerDotWindow(20, 10, 5)
        assertEquals(listOf(PagerDot(8, true), PagerDot(9, false), PagerDot(10, false), PagerDot(11, false), PagerDot(12, true)), w)
        assertEquals(listOf(0, 1, 2, 3, 4), pagerDotWindow(20, 0, 5).map { it.page })
        assertTrue(!pagerDotWindow(20, 0, 5).first().small && pagerDotWindow(20, 0, 5).last().small)
        assertEquals(19, pagerDotWindow(20, 19, 5).last().page)
        assertEquals(emptyList(), pagerDotWindow(0, 0, 5))
    }

    @Test
    fun adaptiveColumnsFollowTheMinimumCellWidth() {
        assertEquals(1, adaptiveColumns(100.dp, 160.dp, 12.dp))
        assertEquals(2, adaptiveColumns(360.dp, 160.dp, 12.dp))
        assertEquals(2, adaptiveColumns(343.dp, 160.dp, 12.dp))
        assertEquals(1, adaptiveColumns(331.dp, 160.dp, 12.dp))
        assertEquals(4, adaptiveColumns(700.dp, 160.dp, 12.dp))
    }

    @Test
    fun adaptiveGridLaysOutColumnsFromTheWidth() = runComposeUiTest {
        var width by mutableStateOf(360)
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light) {
                AdaptiveGrid((0 until 5).toList(), 160.dp, Modifier.width(width.dp), spacing = 10.dp) { Box(Modifier.height(40.dp).testTag("cell$it")) }
            }
        }
        assertEquals(185.dp, onNodeWithTag("cell1").getUnclippedBoundsInRoot().left)
        assertEquals(50.dp, onNodeWithTag("cell2").getUnclippedBoundsInRoot().top)
        width = 700
        waitForIdle()
        assertEquals(0.dp, onNodeWithTag("cell4").getUnclippedBoundsInRoot().left)
        assertEquals(50.dp, onNodeWithTag("cell4").getUnclippedBoundsInRoot().top)
    }

    @Test
    fun statusPageAndSectionHeaderHaveOneHeadingAndTheirActions() = runComposeUiTest {
        var primary = 0
        var secondary = 0
        var link = 0
        themed {
            androidx.compose.foundation.layout.Column {
                SectionHeader("Recent orders", caption = "This week", description = "Paid and pending", action = SectionHeaderAction.Link("See all") { link++ }, testTag = "h")
                StatusPage(
                    "Order placed", description = "We sent the receipt", icon = StapelIconIds.CheckCircle, tone = StapelTone.Success,
                    primaryAction = ButtonSpec("Track order", { primary++ }), secondaryAction = ButtonSpec("Keep shopping", { secondary++ }),
                    footer = "Order 1024", testTag = "s",
                )
                EmptyState("No orders", secondaryAction = ButtonSpec("Browse", { secondary++ }), action = ButtonSpec("Import", { primary++ }))
            }
        }
        onNode(hasText("Recent orders") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true).assertExists()
        onNode(hasText("Order placed") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true).assertExists()
        onNodeWithText("This week", useUnmergedTree = true).assertExists()
        onNodeWithText("Order 1024", useUnmergedTree = true).assertExists()
        onNodeWithText("See all").performClick()
        onNodeWithText("Track order").performClick()
        onNodeWithText("Keep shopping").performClick()
        onNodeWithText("Browse").performClick()
        assertEquals(1, link)
        assertEquals(1, primary)
        assertEquals(2, secondary)
    }

    @Test
    fun aPageCollapsesItsLargeTitleAsTheContentScrolls() = runComposeUiTest {
        themed {
            StapelPage(title = "Inbox", subtitle = "12 unread", largeTitle = true) {
                repeat(40) { StapelText("Message $it") }
            }
        }
        val expanded = onNodeWithTag(STAPEL_PAGE_TOP_BAR_TAG + LARGE_TITLE_TAG_SUFFIX, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        assertTrue(expanded > 10f, "the large title is drawn while expanded ($expanded)")
        onNodeWithTag(STAPEL_PAGE_CONTENT_TAG).performTouchInput { swipeUp() }
        waitForIdle()
        onNodeWithTag(STAPEL_PAGE_TOP_BAR_TAG + LARGE_TITLE_TAG_SUFFIX, useUnmergedTree = true).assertDoesNotExist()
        onNode(hasText("Inbox") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true).assertExists()
    }

    @Test
    fun aPageKeepsContentAboveTheKeyboardInsetAndShowsSnackbars() = runComposeUiTest {
        val host = StapelSnackbarHostState()
        themed {
            val scope = rememberCoroutineScope()
            StapelPage(insets = WindowInsets(bottom = 300.dp), snackbars = host, scrollable = false) {
                Box(Modifier.size(10.dp).testTag("top"))
                StapelButton("Notify", { scope.launch { host.show(SnackbarSpec("Saved", testTag = "snack")) } })
            }
        }
        val page = onNodeWithTag(STAPEL_PAGE_CONTENT_TAG).getUnclippedBoundsInRoot()
        assertTrue(page.bottom <= 500.dp, "content stops above a 300 dp inset in an 800 dp window (bottom ${page.bottom})")
        onNodeWithText("Notify").performClick()
        waitForIdle()
        val snack = onNodeWithTag("snack").getUnclippedBoundsInRoot()
        assertTrue(snack.bottom <= 500.dp + 0.5.dp, "the snackbar sits above the inset (${snack.bottom})")
    }

    @Test
    fun aPageWiresPullToRefreshOnce() = runComposeUiTest {
        var refreshed = 0
        themed {
            StapelPage(title = "Feed", refresh = stapel.ui.skin.PullToRefreshSpec(false, { refreshed++ }, "Refresh feed")) { StapelText("Item") }
        }
        val node = onNode(SemanticsMatcher("refresh action") { n ->
            n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.CustomActions) { null }?.any { it.label == "Refresh feed" } == true
        })
        node.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions].first().action()
        assertEquals(1, refreshed)
        onNode(hasTestTag(STAPEL_PAGE_TOP_BAR_TAG)).assertExists()
    }
}
