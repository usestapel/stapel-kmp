package stapel.ui.material3

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import stapel.showcase.contract.SkinContractTheme
import stapel.showcase.contract.assertSkinContract
import stapel.showcase.contract.assertTopBarSurvivesLargeText
import stapel.tokens.StapelPresetTokens
import stapel.tokens.StapelTokens
import stapel.tokens.ThemeMode
import stapel.ui.StapelButton
import stapel.ui.StapelIconButton
import stapel.ui.StapelIconIds
import stapel.ui.StapelLayout
import stapel.ui.StapelText
import stapel.ui.skin.ButtonSpec
import stapel.ui.skin.StapelSkin
import stapel.ui.toColor
import kotlin.test.Test
import kotlin.test.assertEquals

private object MaterialContractTheme : SkinContractTheme {
    @Composable
    override fun Wrap(skin: StapelSkin, tokens: StapelTokens, mode: ThemeMode, layout: StapelLayout, content: @Composable () -> Unit) {
        StapelMaterialTheme(tokens = tokens, mode = mode, skin = skin, layout = layout, content = content)
    }
}

/** One slot replaced by delegation; everything else stays Material. */
private object AppSkin : StapelSkin by Material3Skin {
    @Composable
    override fun Button(spec: ButtonSpec, modifier: Modifier) {
        Box(modifier.testTag("app.button")) { StapelText("APP ${spec.label}") }
    }
}

@OptIn(ExperimentalTestApi::class)
class Material3SkinTest {
    @Test
    fun material3SkinHonoursTheContractLight() = runComposeUiTest { assertSkinContract(Material3Skin, theme = MaterialContractTheme) }

    @Test
    fun theTopBarSurvivesTwiceTheTextSizeInEnglishAndRussian() = runComposeUiTest {
        assertTopBarSurvivesLargeText(Material3Skin, theme = MaterialContractTheme)
    }

    @Test
    fun material3SkinHonoursTheContractDarkWithAPreset() = runComposeUiTest {
        assertSkinContract(Material3Skin, tokens = StapelPresetTokens.crimson, mode = ThemeMode.Dark, theme = MaterialContractTheme)
    }

    @Test
    fun delegationReplacesOnlyTheButton() = runComposeUiTest {
        var back = 0
        var scheme: androidx.compose.material3.ColorScheme? = null
        setContent {
            StapelMaterialTheme(skin = AppSkin, mode = ThemeMode.Light) {
                scheme = MaterialTheme.colorScheme
                StapelButton("Pay", {})
                StapelIconButton(StapelIconIds.ArrowBack, "Back", { back++ }, testTag = "icon")
            }
        }
        onNodeWithText("APP Pay").assertExists()
        onNodeWithTag("app.button").assertExists()
        onNodeWithTag("icon").performClick()
        assertEquals(1, back)
        assertEquals(stapel.tokens.StapelDefaultTokens.light.brand.toColor(), scheme!!.primary)
    }
}
