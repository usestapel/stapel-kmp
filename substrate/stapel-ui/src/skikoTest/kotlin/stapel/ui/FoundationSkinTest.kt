package stapel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import stapel.i18n.CoreUiBundle
import stapel.i18n.CoreUiStrings
import stapel.i18n.StapelStrings
import stapel.result.ErrorEnvelope
import stapel.result.ServerRefusal
import stapel.result.state.Freshness
import stapel.result.state.Resource
import stapel.showcase.contract.assertSkinContract
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
import stapel.ui.skin.ButtonSpec
import stapel.ui.skin.FoundationSkin
import stapel.ui.skin.StapelSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/** A skin that breaks one duty: its button runs the action while loading. */
private object LeakyLoadingSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Button(spec: ButtonSpec, modifier: Modifier) {
        FoundationSkin.Button(ButtonSpec(spec.label, spec.onClick, spec.variant, spec.size, spec.enabled, false, spec.gatedReason, testTag = spec.testTag), modifier)
    }
}

/** A project skin replacing exactly one slot by interface delegation. */
private object BrandButtonSkin : StapelSkin by FoundationSkin {
    @Composable
    override fun Button(spec: ButtonSpec, modifier: Modifier) {
        Box(modifier.testTag("brand.button")) { StapelText("BRAND ${spec.label}") }
    }
}

@OptIn(ExperimentalTestApi::class)
class FoundationSkinTest {
    @Test
    fun foundationSkinHonoursTheContractLight() = runComposeUiTest { assertSkinContract(FoundationSkin) }

    @Test
    fun foundationSkinHonoursTheContractDark() = runComposeUiTest { assertSkinContract(FoundationSkin, mode = ThemeMode.Dark) }

    @Test
    fun contractCatchesABrokenSkin() = runComposeUiTest {
        val e = assertFails { assertSkinContract(LeakyLoadingSkin) }
        assertTrue(e.message.orEmpty().startsWith("skin contract duty Button.loading"), e.message)
    }

    @Test
    fun delegationReplacesExactlyOneSlot() = runComposeUiTest {
        var back = 0
        setContent {
            StapelTheme(skin = BrandButtonSkin, mode = ThemeMode.Light) {
                StapelButton("Pay", {})
                StapelIconButton(StapelIconIds.ArrowBack, "Back", { back++ }, testTag = "icon")
            }
        }
        onNodeWithText("BRAND Pay").assertExists()
        onNodeWithText("Pay").assertDoesNotExist()
        onNodeWithTag("icon").performClick()
        assertEquals(1, back)
    }

    @Test
    fun pageGutterIsTheTokenValuePerSizeClass() = runComposeUiTest {
        var width by mutableStateOf(390.dp)
        var edge by mutableStateOf(false)
        setContent {
            val layout = stapelLayout(StapelDefaultTokens, width, StapelInput.Touch, 44.dp)
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, layout = layout) {
                Box(Modifier.size(width, 600.dp)) {
                    StapelPage(edgeToEdge = edge, measure = PaneMeasure.Full) { Box(Modifier.size(10.dp).testTag("child")) }
                }
            }
        }
        assertEquals(4.dp, onNodeWithTag("child").getUnclippedBoundsInRoot().left)
        width = 800.dp
        waitForIdle()
        assertEquals(8.dp, onNodeWithTag("child").getUnclippedBoundsInRoot().left)
        edge = true
        waitForIdle()
        assertEquals(0.dp, onNodeWithTag("child").getUnclippedBoundsInRoot().left)
    }

    @Test
    fun stringsRecomposeOnARuntimeLocaleSwitch() = runComposeUiTest {
        val strings = StapelStrings {
            initialLocale = "en"
            register(CoreUiBundle)
            override("ru", mapOf("stapel.ui.retry" to "Ещё раз"))
        }
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings) {
                StapelText(stapelString(CoreUiStrings.Retry))
            }
        }
        onNodeWithText("Try again").assertExists()
        runBlocking { strings.setLocale("ru") }
        waitForIdle()
        onNodeWithText("Ещё раз").assertExists()
    }

    @Test
    fun loadBoundaryHasDistinctArmsAndErrorsUseTheLadder() = runComposeUiTest {
        var state by mutableStateOf<Resource<String>>(Resource.Loading)
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = StapelStrings { initialLocale = "en"; register(CoreUiBundle) }) {
                LoadBoundary(state, onRetry = {}) { StapelText("Data: $it") }
            }
        }
        onNodeWithText("Data", substring = true).assertDoesNotExist()
        state = Resource.Failed(ServerRefusal(ErrorEnvelope.synthesized(503)))
        waitForIdle()
        onNodeWithText("The service is temporarily unavailable. Please try again shortly.", useUnmergedTree = true).assertExists()
        onNodeWithText("Try again").assertExists()
        state = Resource.Ready("x", Freshness.network(0))
        waitForIdle()
        onNodeWithText("Data: x").assertExists()
    }
}
