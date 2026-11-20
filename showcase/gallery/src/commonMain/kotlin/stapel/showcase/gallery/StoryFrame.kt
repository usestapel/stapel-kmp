package stapel.showcase.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.showcase.KnobValues
import stapel.showcase.Story
import stapel.showcase.StoryCanvas
import stapel.showcase.StoryEnvironment
import stapel.showcase.StoryKind
import stapel.showcase.StoryState
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.StapelPresetTokens
import stapel.tokens.ThemeMode
import stapel.ui.LocalStapelReduceMotion
import stapel.ui.StapelInput
import stapel.ui.StapelTheme
import stapel.ui.material3.StapelMaterialTheme
import stapel.ui.stapelLayout

/**
 * One story under the gallery's [controls] in one [mode]: skin, preset, strings in the chosen locale
 * (core floor + the catalogue's bundles), the frame width's layout, text scale, direction, reduced
 * motion, then the catalogue's environment, then the story's canvas on the theme's surface (screens
 * and flows in a viewport of the size class's height, components at their own height). The
 * gallery, the snapshot runner and the docs export all draw a story through this function.
 */
@Composable
fun StoryFrame(
    story: Story,
    environment: StoryEnvironment,
    controls: GalleryControls,
    mode: ThemeMode,
    modifier: Modifier = Modifier,
    state: StoryState = story.states.first(),
    values: KnobValues = remember(story) { KnobValues(story.knobs) },
    overrides: Map<String, Any?> = emptyMap(),
    onEvent: (String) -> Unit = {},
    showAnatomy: Boolean = false,
) {
    val tokens = StapelPresetTokens.all[controls.preset] ?: StapelDefaultTokens
    val locale = state.locale ?: controls.locale
    val strings = remember(locale, environment) {
        StapelStrings { initialLocale = locale; register(CoreUiBundle); environment.bundles.forEach { register(it) } }
    }
    val width = controls.size.width
    val layout = stapelLayout(
        tokens, width,
        input = if (controls.size == SizeChoice.Phone) StapelInput.Touch else StapelInput.Pointer,
        platformTouchFloor = if (controls.size == SizeChoice.Phone) 44.dp else 24.dp,
    )
    val density = LocalDensity.current
    // The direction follows the strings locale inside the theme; the gallery's RTL switch (and a state
    // marked `rtl`) forces it, as an app's explicit override would.
    val direction = if (controls.rtl || state.rtl) LayoutDirection.Rtl else null
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, controls.textScale / 100f),
    ) {
        val body: @Composable () -> Unit = {
            CompositionLocalProvider(LocalStapelReduceMotion provides controls.reduceMotion) {
                environment.provide {
                    val viewport = story.kind == StoryKind.Screen || story.kind == StoryKind.Flow
                    val sized = if (viewport) modifier.size(width, controls.size.height) else modifier.width(width)
                    Box(sized.background(StapelTheme.colors.surface)) {
                        StoryCanvas(story, state, values, overrides = overrides, onEvent = onEvent, showAnatomy = showAnatomy)
                    }
                }
            }
        }
        when (controls.skin) {
            GallerySkin.Material3 -> StapelMaterialTheme(tokens = tokens, mode = mode, skin = controls.skin.skin, strings = strings, layout = layout, layoutDirection = direction, content = body)
            GallerySkin.Foundation -> StapelTheme(skin = controls.skin.skin, tokens = tokens, mode = mode, strings = strings, layout = layout, reduceMotion = controls.reduceMotion, layoutDirection = direction, content = body)
        }
    }
}
