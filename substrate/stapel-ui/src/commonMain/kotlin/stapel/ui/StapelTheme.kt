package stapel.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.result.text.ErrorTextResolver
import stapel.tokens.FontFamilies
import stapel.tokens.FontWeights
import stapel.tokens.Shadow
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.StapelTokens
import stapel.tokens.ThemeMode
import stapel.tokens.TypeStep
import stapel.ui.skin.StapelSkin

/**
 * The type scale as Compose text styles, with the semantic aliases bricks use (so a retuned scale
 * moves them together).
 */
@Immutable
public class StapelTypography(
    /** `xs`. */
    public val xs: TextStyle,
    /** `sm`. */
    public val sm: TextStyle,
    /** `md`. */
    public val md: TextStyle,
    /** `lg`. */
    public val lg: TextStyle,
    /** `xl`. */
    public val xl: TextStyle,
    /** `2xl`. */
    public val xxl: TextStyle,
    /** `3xl`. */
    public val xxxl: TextStyle,
    /** The weights of the source. */
    public val weights: FontWeights,
    /** UI font. */
    public val sans: FontFamily,
    /** Monospace font. */
    public val mono: FontFamily,
) {
    /** Body text: `md`, regular. */
    public val body: TextStyle get() = md

    /** Secondary body text: `sm`, regular. */
    public val bodySmall: TextStyle get() = sm

    /** Captions and hints: `xs`, regular. */
    public val caption: TextStyle get() = xs

    /** Control labels: `sm`, medium. */
    public val label: TextStyle get() = sm.copy(fontWeight = FontWeight(weights.medium))

    /** Section and card titles: `lg`, semibold. */
    public val title: TextStyle get() = lg.copy(fontWeight = FontWeight(weights.semibold))

    /** Page headings: `xl`, semibold. */
    public val heading: TextStyle get() = xl.copy(fontWeight = FontWeight(weights.semibold))

    /** Display text: `3xl`, bold. */
    public val display: TextStyle get() = xxxl.copy(fontWeight = FontWeight(weights.bold))
}

/** The spacing scale in dp. */
@Immutable
public class StapelSpacing(
    /** Step 0. */
    public val s0: Dp,
    /** Step 1 (4 by default). */
    public val s1: Dp,
    /** Step 2 (8). */
    public val s2: Dp,
    /** Step 3 (12). */
    public val s3: Dp,
    /** Step 4 (16). */
    public val s4: Dp,
    /** Step 5 (24). */
    public val s5: Dp,
    /** Step 6 (32). */
    public val s6: Dp,
    /** Step 7 (48). */
    public val s7: Dp,
    /** Step 8 (64). */
    public val s8: Dp,
)

/** Corner shapes from the radius scale. */
@Immutable
public class StapelShapes(
    /** Square. */
    public val none: Shape,
    /** Small. */
    public val sm: Shape,
    /** Medium: controls. */
    public val md: Shape,
    /** Large: cards. */
    public val lg: Shape,
    /** Extra large: sheets. */
    public val xl: Shape,
    /** Pill. */
    public val full: Shape,
)

/** The current mode's elevation shadows (null = none). */
@Immutable
public class StapelElevations(
    /** No shadow. */
    public val none: Shadow?,
    /** Low. */
    public val low: Shadow?,
    /** Medium. */
    public val medium: Shadow?,
    /** High. */
    public val high: Shadow?,
)

/** Font families for the token stacks. */
public class StapelFonts(
    /** UI font. */
    public val sans: FontFamily,
    /** Monospace font. */
    public val mono: FontFamily,
)

/** Turns the token font stacks into Compose font families (system fonts, a bundled font, a project font). */
public interface StapelFontResolver {
    /** The fonts for [families]. */
    @Composable
    public fun resolve(families: FontFamilies): StapelFonts
}

/** The platform's own fonts: zero bytes, full Latin/Cyrillic coverage, native rendering. */
public object SystemFonts : StapelFontResolver {
    @Composable
    override fun resolve(families: FontFamilies): StapelFonts = StapelFonts(FontFamily.Default, FontFamily.Monospace)
}

/** Light / dark / follow the system. */
public enum class ThemePreference {
    /** Always light. */
    Light,

    /** Always dark. */
    Dark,

    /** Follow the system setting. */
    System,
}

/** Where the theme preference lives (the app binds a persistent store; [InMemoryThemePreferenceStore] by default). */
public interface ThemePreferenceStore {
    /** The current preference. */
    public val preference: StateFlow<ThemePreference>

    /** Stores [preference]. */
    public suspend fun set(preference: ThemePreference)
}

/** A [ThemePreferenceStore] that forgets on restart. */
public class InMemoryThemePreferenceStore(initial: ThemePreference = ThemePreference.System) : ThemePreferenceStore {
    private val state = MutableStateFlow(initial)
    override val preference: StateFlow<ThemePreference> = state.asStateFlow()

    override suspend fun set(preference: ThemePreference) {
        state.value = preference
    }
}

/** The mode [store] asks for; [ThemePreference.System] follows `isSystemInDarkTheme()`. */
@Composable
public fun rememberThemeMode(store: ThemePreferenceStore): ThemeMode {
    val preference by store.preference.collectAsState()
    val systemDark = isSystemInDarkTheme()
    return when (preference) {
        ThemePreference.Light -> ThemeMode.Light
        ThemePreference.Dark -> ThemeMode.Dark
        ThemePreference.System -> if (systemDark) ThemeMode.Dark else ThemeMode.Light
    }
}

/** Everything one [StapelTheme] provides. */
@Immutable
internal class StapelThemeValues(
    val tokens: StapelTokens,
    val mode: ThemeMode,
    val colors: StapelColors,
    val type: StapelTypography,
    val spacing: StapelSpacing,
    val shapes: StapelShapes,
    val elevation: StapelElevations,
    val layout: StapelLayout,
    val skin: StapelSkin,
    val icons: StapelIconSet,
)

internal val LocalStapelTheme = staticCompositionLocalOf<StapelThemeValues> {
    error("No StapelTheme in this composition: wrap the UI in StapelTheme(skin = …) or StapelMaterialTheme { … }")
}

/** The UI strings of the subtree (default: the `stapel.ui.*` floor in the platform locale). */
public val LocalStapelStrings: androidx.compose.runtime.ProvidableCompositionLocal<StapelStrings> =
    staticCompositionLocalOf { DefaultStrings }

private val DefaultStrings: StapelStrings by lazy { StapelStrings { register(CoreUiBundle) } }

/**
 * True when the user asked for less motion: skins draw still placeholders instead of shimmer, and
 * skip decorative animation. `StapelTheme(reduceMotion = …)` sets it (the platform setting by default).
 */
public val LocalStapelReduceMotion: androidx.compose.runtime.ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { false }

/** The error-text resolver of the subtree (the app registers its bricks' catalogs here). */
public val LocalStapelErrorText: androidx.compose.runtime.ProvidableCompositionLocal<ErrorTextResolver> =
    staticCompositionLocalOf { ErrorTextResolver(emptyList()) }

/** Read access to the current theme. Every getter throws outside a [StapelTheme]. */
public object StapelTheme {
    /** Colour roles of the current mode. */
    public val colors: StapelColors @Composable @ReadOnlyComposable get() = LocalStapelTheme.current.colors

    /** Type scale. */
    public val type: StapelTypography @Composable @ReadOnlyComposable get() = LocalStapelTheme.current.type

    /** Spacing scale. */
    public val spacing: StapelSpacing @Composable @ReadOnlyComposable get() = LocalStapelTheme.current.spacing

    /** Corner shapes. */
    public val shapes: StapelShapes @Composable @ReadOnlyComposable get() = LocalStapelTheme.current.shapes

    /** Elevation shadows of the current mode. */
    public val elevation: StapelElevations @Composable @ReadOnlyComposable get() = LocalStapelTheme.current.elevation

    /** Size class, input kind, page gutter, control height, dialog surface. */
    public val layout: StapelLayout @Composable @ReadOnlyComposable get() = LocalStapelTheme.current.layout

    /** Light or dark. */
    public val mode: ThemeMode @Composable @ReadOnlyComposable get() = LocalStapelTheme.current.mode

    /** The tokens this theme was built from. */
    public val tokens: StapelTokens @Composable @ReadOnlyComposable get() = LocalStapelTheme.current.tokens

    /** The skin drawing every slot. */
    public val skin: StapelSkin @Composable @ReadOnlyComposable get() = LocalStapelTheme.current.skin

    /** The icon set. */
    public val icons: StapelIconSet @Composable @ReadOnlyComposable get() = LocalStapelTheme.current.icons
}

private fun TypeStep.style(family: FontFamily, weight: Int): TextStyle =
    TextStyle(fontSize = fontSize.sp, lineHeight = lineHeight.sp, fontFamily = family, fontWeight = FontWeight(weight))

internal fun buildThemeValues(
    tokens: StapelTokens,
    mode: ThemeMode,
    fonts: StapelFonts,
    layout: StapelLayout,
    skin: StapelSkin,
    icons: StapelIconSet,
): StapelThemeValues {
    val w = tokens.fontWeight
    val fs = tokens.fontSize
    val sp = tokens.spacing
    val r = tokens.radii
    val e = tokens.elevation
    return StapelThemeValues(
        tokens = tokens,
        mode = mode,
        colors = StapelColors(tokens.colors(mode)),
        type = StapelTypography(
            fs.xs.style(fonts.sans, w.regular), fs.sm.style(fonts.sans, w.regular), fs.md.style(fonts.sans, w.regular),
            fs.lg.style(fonts.sans, w.regular), fs.xl.style(fonts.sans, w.regular), fs.xxl.style(fonts.sans, w.regular),
            fs.xxxl.style(fonts.sans, w.regular), w, fonts.sans, fonts.mono,
        ),
        spacing = StapelSpacing(sp.s0.dp, sp.s1.dp, sp.s2.dp, sp.s3.dp, sp.s4.dp, sp.s5.dp, sp.s6.dp, sp.s7.dp, sp.s8.dp),
        shapes = StapelShapes(
            RoundedCornerShape(r.none.dp), RoundedCornerShape(r.sm.dp), RoundedCornerShape(r.md.dp),
            RoundedCornerShape(r.lg.dp), RoundedCornerShape(r.xl.dp), RoundedCornerShape(r.full.dp),
        ),
        elevation = StapelElevations(e.none[mode], e.low[mode], e.medium[mode], e.high[mode]),
        layout = layout,
        skin = skin,
        icons = icons,
    )
}

/**
 * The paradigm-neutral theme root. There is no implicit skin: the app passes one
 * (`Material3Skin` from `stapel-ui-material3`, [stapel.ui.skin.FoundationSkin], or its own). A nested
 * `StapelTheme` re-states only what differs (another mode or skin for a subtree).
 *
 * @param skin draws every slot.
 * @param tokens the theme (an app's generated tokens object, a preset, a test theme).
 * @param mode light or dark (see [rememberThemeMode]).
 * @param fonts turns the token font stacks into font families.
 * @param icons the icon set.
 * @param strings the UI strings.
 * @param layout size class, input kind, gutter; derived from the window width by default.
 * @param reduceMotion the user asked for less motion (the platform setting by default); read through [LocalStapelReduceMotion].
 * @param content the UI.
 */
@Composable
public fun StapelTheme(
    skin: StapelSkin,
    tokens: StapelTokens = StapelDefaultTokens,
    mode: ThemeMode = if (isSystemInDarkTheme()) ThemeMode.Dark else ThemeMode.Light,
    fonts: StapelFontResolver = SystemFonts,
    icons: StapelIconSet = StapelIcons,
    strings: StapelStrings = LocalStapelStrings.current,
    layout: StapelLayout = rememberStapelLayout(tokens),
    reduceMotion: Boolean = platformReduceMotion(),
    content: @Composable () -> Unit,
) {
    val resolvedFonts = fonts.resolve(tokens.fontFamily)
    val values = remember(tokens, mode, resolvedFonts, layout, skin, icons) {
        buildThemeValues(tokens, mode, resolvedFonts, layout, skin, icons)
    }
    CompositionLocalProvider(
        LocalStapelTheme provides values,
        LocalStapelStrings provides strings,
        LocalStapelContentColor provides values.colors.text,
        LocalStapelReduceMotion provides reduceMotion,
        content = content,
    )
}
