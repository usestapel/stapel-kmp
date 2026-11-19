package stapel.auth.ui.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.tokens.ThemeMode
import stapel.ui.StapelTheme
import stapel.ui.material3.StapelMaterialTheme
import stapel.ui.skin.FoundationSkin

/** Which skin draws the live demo. */
enum class DemoSkin { Material3, Foundation }

/**
 * The theme of the live demo: the core and auth strings in [locale] and the auth catalogue's
 * environment ([authStoryEnvironment], the same one the gallery composes around the auth stories).
 */
@Composable
fun DemoTheme(skin: DemoSkin, locale: String, mode: ThemeMode, content: @Composable () -> Unit) {
    val strings = remember(locale) {
        StapelStrings { initialLocale = locale; register(CoreUiBundle); authStoryEnvironment.bundles.forEach { register(it) } }
    }
    authStoryEnvironment.provide {
        when (skin) {
            DemoSkin.Material3 -> StapelMaterialTheme(mode = mode, strings = strings, content = content)
            DemoSkin.Foundation -> StapelTheme(skin = FoundationSkin, mode = mode, strings = strings, content = content)
        }
    }
}
