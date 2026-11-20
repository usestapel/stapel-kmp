package stapel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.tokens.ThemeMode
import stapel.ui.skin.FoundationSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The active strings locale sets the layout direction; an explicit direction overrides it. */
@OptIn(ExperimentalTestApi::class)
class LayoutDirectionTest {
    private var seen: LayoutDirection? = null

    @Composable
    private fun Probe() {
        seen = LocalLayoutDirection.current
        Row(Modifier.size(200.dp, 20.dp)) {
            Box(Modifier.size(20.dp).testTag("first"))
            Box(Modifier.size(20.dp).testTag("second"))
        }
    }

    private fun ComposeUiTest.firstIsRightOfSecond(): Boolean =
        onNodeWithTag("first").getUnclippedBoundsInRoot().left > onNodeWithTag("second").getUnclippedBoundsInRoot().left

    @Test
    fun anInAppSwitchToAnRtlLocaleMirrorsTheUi() = runComposeUiTest {
        val strings = StapelStrings { initialLocale = "en"; register(CoreUiBundle) }
        setContent {
            // The platform side says LTR; the strings locale is the truth.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings) { Probe() }
            }
        }
        waitForIdle()
        assertEquals(LayoutDirection.Ltr, seen)
        assertTrue(!firstIsRightOfSecond())

        runBlocking { strings.setLocale("ar") }
        waitForIdle()
        assertEquals(LayoutDirection.Rtl, seen)
        assertTrue(firstIsRightOfSecond(), "start is on the right after switching to Arabic")

        runBlocking { strings.setLocale("ru") }
        waitForIdle()
        assertEquals(LayoutDirection.Ltr, seen)
    }

    @Test
    fun anRtlPlatformDirectionDoesNotWinOverAnLtrStringsLocale() = runComposeUiTest {
        val strings = StapelStrings { initialLocale = "en"; register(CoreUiBundle) }
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings) { Probe() }
            }
        }
        waitForIdle()
        assertEquals(LayoutDirection.Ltr, seen)
    }

    @Test
    fun aBareThemeWithoutAppStringsFollowsThePlatformDirection() = runComposeUiTest {
        var platform by mutableStateOf(LayoutDirection.Rtl)
        setContent {
            // The host's direction comes from the platform locale; no app strings means no app locale.
            CompositionLocalProvider(LocalLayoutDirection provides platform) {
                StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light) { Probe() }
            }
        }
        waitForIdle()
        assertEquals(LayoutDirection.Rtl, seen)
        assertTrue(firstIsRightOfSecond(), "an RTL platform mirrors a bare theme")
        platform = LayoutDirection.Ltr
        waitForIdle()
        assertEquals(LayoutDirection.Ltr, seen)
    }

    @Test
    fun anExplicitDirectionOverridesTheLocaleAndIsInheritedByNestedThemes() = runComposeUiTest {
        val strings = StapelStrings { initialLocale = "ar"; register(CoreUiBundle) }
        var nested: LayoutDirection? = null
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings, layoutDirection = LayoutDirection.Ltr) {
                Probe()
                StapelTheme(skin = FoundationSkin, mode = ThemeMode.Dark) { nested = LocalLayoutDirection.current }
            }
        }
        waitForIdle()
        assertEquals(LayoutDirection.Ltr, seen)
        assertEquals(LayoutDirection.Ltr, nested, "a nested theme keeps the project's explicit direction")
    }

    @Test
    fun theDefaultDerivesFromTheStringsLocale() = runComposeUiTest {
        var nested: LayoutDirection? = null
        val strings = StapelStrings { initialLocale = "he"; register(CoreUiBundle) }
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings) {
                StapelTheme(skin = FoundationSkin, mode = ThemeMode.Dark) { nested = LocalLayoutDirection.current }
            }
        }
        waitForIdle()
        assertEquals(LayoutDirection.Rtl, nested)
    }
}
