package stapel.auth.ui.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import stapel.auth.ui.AuthUiBundle
import stapel.auth.ui.authErrorCatalogs
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.result.text.ErrorTextResolver
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
import stapel.ui.LocalStapelErrorText
import stapel.ui.StapelInput
import stapel.ui.StapelTheme
import stapel.ui.material3.StapelMaterialTheme
import stapel.ui.skin.FoundationSkin
import stapel.ui.stapelLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class AuthStoriesTest {
    @Test
    fun idsAreUnique() {
        assertEquals(AuthStories.size, AuthStories.map { it.id }.toSet().size)
        assertTrue(AuthStories.size >= 25)
    }

    @Test
    fun everyStoryRendersWithBothSkinsInBothLocales() = runComposeUiTest {
        var index by mutableStateOf(0)
        var material by mutableStateOf(false)
        var locale by mutableStateOf("en")
        setContent {
            val strings = androidx.compose.runtime.remember(locale) { StapelStrings { initialLocale = locale; register(CoreUiBundle); register(AuthUiBundle) } }
            val layout = stapelLayout(StapelDefaultTokens, 390.dp, input = StapelInput.Touch, platformTouchFloor = 44.dp)
            CompositionLocalProvider(LocalStapelErrorText provides ErrorTextResolver(authErrorCatalogs())) {
                val body = @androidx.compose.runtime.Composable { Box(Modifier.testTag("story")) { AuthStories[index].content() } }
                if (material) StapelMaterialTheme(mode = ThemeMode.Dark, strings = strings, layout = layout, content = body)
                else StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings, layout = layout, content = body)
            }
        }
        for (m in listOf(false, true)) for (l in listOf("en", "ru")) for (i in AuthStories.indices) {
            material = m
            locale = l
            index = i
            waitForIdle()
            onNodeWithTag("story").assertExists("story ${AuthStories[i].id} (material=$m, $l)")
        }
    }
}
