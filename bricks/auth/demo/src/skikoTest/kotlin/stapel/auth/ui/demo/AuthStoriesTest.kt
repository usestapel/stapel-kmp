package stapel.auth.ui.demo

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.showcase.StoryCanvas
import stapel.showcase.StoryTarget
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
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
    fun theCatalogueCoversTheScreenTable() {
        assertEquals("auth", storyCatalog.id)
        assertEquals(30, storyCatalog.stories.size)
        val covered = storyCatalog.stories.flatMap { it.covers }.filterIsInstance<StoryTarget.Screen>().map { it.name }.toSet()
        assertEquals(
            setOf("SignIn", "OtpCode", "TotpChallenge", "ForcedPasswordChange", "Locked", "GuestUpgrade", "Sessions", "SignOutConfirm"),
            covered,
        )
        assertTrue(storyCatalog.environment.locales.containsAll(listOf("en", "ru")))
    }

    @Test
    fun everyStoryRendersWithBothSkinsInBothLocales() = runComposeUiTest {
        val stories = storyCatalog.stories
        var index by mutableStateOf(0)
        var material by mutableStateOf(false)
        var locale by mutableStateOf("en")
        setContent {
            val strings = remember(locale) {
                StapelStrings { initialLocale = locale; register(CoreUiBundle); storyCatalog.environment.bundles.forEach { register(it) } }
            }
            val layout = stapelLayout(StapelDefaultTokens, 390.dp, input = StapelInput.Touch, platformTouchFloor = 44.dp)
            val body = @androidx.compose.runtime.Composable {
                storyCatalog.environment.provide { Box(Modifier.testTag("story")) { StoryCanvas(stories[index]) } }
            }
            if (material) StapelMaterialTheme(mode = ThemeMode.Dark, strings = strings, layout = layout, content = body)
            else StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings, layout = layout, content = body)
        }
        for (m in listOf(false, true)) for (l in listOf("en", "ru")) for (i in stories.indices) {
            material = m
            locale = l
            index = i
            waitForIdle()
            onNodeWithTag("story").assertExists("story ${stories[i].id} (material=$m, $l)")
        }
    }
}
