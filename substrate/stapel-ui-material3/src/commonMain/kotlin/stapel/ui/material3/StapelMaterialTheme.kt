package stapel.ui.material3

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import stapel.i18n.StapelStrings
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.StapelTokens
import stapel.tokens.ThemeMode
import stapel.ui.LocalStapelStrings
import stapel.ui.StapelFontResolver
import stapel.ui.StapelIconSet
import stapel.ui.StapelIcons
import stapel.ui.StapelLayout
import stapel.ui.StapelTheme
import stapel.ui.SystemFonts
import stapel.ui.rememberStapelLayout
import stapel.ui.skin.StapelSkin

/**
 * `StapelTheme(skin = Material3Skin)` plus a `MaterialTheme` built from the same tokens (colour scheme,
 * typography, shapes), so Material components an app uses directly agree with the skin.
 *
 * @param skin [Material3Skin] or a delegation of it (`object AppSkin : StapelSkin by Material3Skin { … }`).
 */
@Composable
public fun StapelMaterialTheme(
    tokens: StapelTokens = StapelDefaultTokens,
    mode: ThemeMode = if (isSystemInDarkTheme()) ThemeMode.Dark else ThemeMode.Light,
    skin: StapelSkin = Material3Skin,
    fonts: StapelFontResolver = SystemFonts,
    icons: StapelIconSet = StapelIcons,
    strings: StapelStrings = LocalStapelStrings.current,
    layout: StapelLayout = rememberStapelLayout(tokens),
    content: @Composable () -> Unit,
) {
    val resolved = fonts.resolve(tokens.fontFamily)
    val scheme = remember(tokens, mode) { tokens.toColorScheme(mode) }
    val typography = remember(tokens, resolved) { tokens.toTypography(resolved) }
    val shapes = remember(tokens) { tokens.toShapes() }
    StapelTheme(skin = skin, tokens = tokens, mode = mode, fonts = fonts, icons = icons, strings = strings, layout = layout) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
