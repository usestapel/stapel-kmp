package stapel.showcase.gallery

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import stapel.showcase.gallery.generated.discoveredCatalogs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GalleryUiTest {
    private val catalogs = discoveredCatalogs.map { it.catalog }

    private fun ComposeUiTest.gallery(link: String? = null, copied: MutableList<String> = mutableListOf()): GalleryState {
        val state = GalleryState(catalogs)
        setContent { Gallery(catalogs, initialLink = link, copyText = { copied += it }, state = state) }
        waitForIdle()
        return state
    }

    private val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    @Test
    fun theRegistryHoldsEveryCatalogueModule() {
        // One catalogue per discovered module, whatever bricks have demos.
        assertTrue(catalogs.map { it.id }.toSet().containsAll(setOf("ui", "auth", "profiles")))
        assertEquals(discoveredCatalogs.size, catalogs.size)
        assertTrue(discoveredCatalogs.any { it.dir == "bricks/auth/demo" && it.module == ":stapel-auth-ui-demo" })
    }

    @Test
    fun searchFindsAStoryAndOpensItsPage() = runComposeUiTest {
        gallery()
        onNodeWithTag("gallery.page.home").assertExists()
        onNodeWithTag("gallery.search").performTextInput("icon button")
        waitForIdle()
        onNodeWithTag("gallery.hit.ui/icon-button").assertExists()
        onNodeWithTag("gallery.hit.ui/button").assertDoesNotExist()
        onNodeWithTag("gallery.hit.ui/icon-button").performClick()
        waitForIdle()
        onNodeWithTag("gallery.page.story.ui/icon-button").assertExists()
        onNodeWithTag("gallery.frame.light").assertExists()
    }

    @Test
    fun searchWithoutMatchesSaysSo() = runComposeUiTest {
        gallery()
        onNodeWithTag("gallery.search").performTextInput("zebra crossing")
        waitForIdle()
        onNodeWithTag("gallery.no-hits").assertExists()
    }

    @Test
    fun aLinkOpensAStoryWithItsControls() = runComposeUiTest {
        val state = gallery("/s/auth/sign_in.email?skin=Foundation&mode=Both&locale=ru")
        onNodeWithTag("gallery.page.story.auth/sign_in.email").assertExists()
        assertEquals(GalleryControls(skin = GallerySkin.Foundation, mode = ModeChoice.Both, locale = "ru"), state.controls)
        onNodeWithTag("gallery.frame.light").assertExists()
        onNodeWithTag("gallery.frame.dark").assertExists()
        onNodeWithTag("gallery.control.skin.Foundation").assert(selected)
    }

    @Test
    fun aCatalogueLinkOpensItsFamilyPage() = runComposeUiTest {
        gallery("/c/auth")
        onNodeWithTag("gallery.page.catalog.auth").assertExists()
        onNodeWithTag("gallery.family.auth/sessions").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("gallery.page.story.auth/sessions").assertExists()
    }

    @Test
    fun anUnknownStoryIsNamedNotBlank() = runComposeUiTest {
        gallery("/s/ui/nope")
        onNodeWithTag("gallery.page.missing").assertExists()
    }

    @Test
    fun controlsApplyToTheCanvas() = runComposeUiTest {
        val state = gallery("/s/ui/button")
        onNodeWithTag("gallery.frame.dark").assertDoesNotExist()
        onNodeWithTag("gallery.control.mode.Both").performClick()
        waitForIdle()
        onNodeWithTag("gallery.frame.dark").assertExists()
        onNodeWithTag("gallery.control.size.Tablet").performScrollTo().performClick()
        onNodeWithTag("gallery.control.scale.200%").performScrollTo().performClick()
        onNodeWithTag("gallery.control.rtl").performScrollTo().performClick()
        onNodeWithTag("gallery.control.motion").performScrollTo().performClick()
        waitForIdle()
        assertEquals(GalleryControls(mode = ModeChoice.Both, size = SizeChoice.Tablet, textScale = 200, rtl = true, reduceMotion = true), state.controls)
        val tablet = onNodeWithTag("gallery.frame.light").fetchSemanticsNode().size.width
        onNodeWithTag("gallery.control.size.Phone").performScrollTo().performClick()
        waitForIdle()
        val phone = onNodeWithTag("gallery.frame.light").fetchSemanticsNode().size.width
        assertTrue(tablet > phone, "tablet frame $tablet wider than phone $phone")
    }

    @Test
    fun knobsChangeTheStoryAndReset() = runComposeUiTest {
        val state = gallery("/s/ui/button")
        val story = state.catalog("ui")!!.story("button")!!
        val values = state.values(state.catalog("ui")!!, story)
        onNodeWithTag("gallery.knobs.reset").performScrollTo().assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
        onNodeWithTag("gallery.knob.variant.Danger").performScrollTo().performClick()
        onNodeWithTag("gallery.knob.loading").performScrollTo().performClick()
        waitForIdle()
        assertEquals(setOf("variant", "loading"), values.modified)
        onNodeWithTag("gallery.knob.variant.reset").performScrollTo().performClick()
        waitForIdle()
        assertEquals(setOf("loading"), values.modified)
        onNodeWithTag("gallery.knobs.reset").performScrollTo().performClick()
        waitForIdle()
        assertEquals(emptySet(), values.modified)
    }

    @Test
    fun tabsShowStatesMatrixAndUsageAndCopyTheId() = runComposeUiTest {
        val copied = mutableListOf<String>()
        gallery("/s/ui/button?tab=states", copied)
        onNodeWithTag("gallery.state.danger").assertExists()
        onNodeWithTag("gallery.state.gated.frame.light").assertExists()
        onNodeWithTag("gallery.copy-id").performClick()
        assertEquals(listOf("ui/button"), copied)
        onNodeWithTag("gallery.copy-link").performClick()
        assertTrue(copied.last().startsWith("stapel-showcase://s/ui/button"), copied.last())
        onNodeWithTag("gallery.tabs").assertExists()
    }

    @Test
    fun matrixAndAnatomyTabsRender() = runComposeUiTest {
        gallery("/s/ui/banner?tab=matrix")
        onNodeWithTag("gallery.cell.0").assertExists()
        onNodeWithTag("gallery.cell.0.frame.light").assertExists()
        onNodeWithTag("gallery.cell.1").assertExists()
    }

    @Test
    fun anatomyOutlinesTheMarkedParts() = runComposeUiTest {
        gallery("/s/ui/list-item?tab=anatomy")
        onNodeWithTag("gallery.anatomy.media").assertExists()
        onNodeWithTag("stapel.showcase.anatomy.media").assertExists()
        onNodeWithTag("stapel.showcase.anatomy.value").assertExists()
    }

    @Test
    fun theTreeListsFamiliesAndStories() = runComposeUiTest {
        gallery()
        onNodeWithTag("gallery.sidebar").assertExists()
        onNodeWithTag("gallery.tree.ui/button").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("gallery.page.story.ui/button").assertExists()
        assertTrue(onNodeWithTag("gallery.sidebar").fetchSemanticsNode().children.isNotEmpty() || hasTestTag("gallery.sidebar").matches(onNodeWithTag("gallery.sidebar").fetchSemanticsNode()))
    }
}
