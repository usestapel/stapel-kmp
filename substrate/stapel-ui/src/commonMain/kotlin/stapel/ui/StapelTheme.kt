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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.i18n.WritingDirection
import stapel.i18n.writingDirectionOf
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

/**
 * The UI strings of the subtree. Default: the `stapel.ui.*` floor in `en`; an app passes strings
 * started in the platform locale (`stapelApp` feeds `LocaleProvider`), and their active locale also
 * sets the layout direction of [StapelTheme].
 */
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

/** The direction a project set explicitly on an enclosing [StapelTheme], inherited by nested ones. */
private val LocalExplicitLayoutDirection = staticCompositionLocalOf<LayoutDirection?> { null }

/** The layout direction of [locale] (a BCP 47 tag): right to left for a right-to-left script. */
public fun layoutDirectionOf(locale: String): LayoutDirection =
    if (writingDirectionOf(locale) == WritingDirection.Rtl) LayoutDirection.Rtl else LayoutDirection.Ltr

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

/** The type-scale step names of the token source, in scale order. */
public val TYPE_STEP_NAMES: List<String> = listOf("xs", "sm", "md", "lg", "xl", "2xl", "3xl")

/**
 * The seam for per-size-class type and spacing: a tokens object may carry responsive roles in
 * `responsive.extra` under `type.<step>` (font size), `type.<step>.line` (line height) and
 * `space.<n>` (spacing step 0–8). For each role present, [sizeClass] picks its value; every absent
 * role keeps the single scale. Pure: the theme calls it once per size class.
 */
public fun responsiveScales(tokens: StapelTokens, sizeClass: StapelSizeClass): Pair<List<TypeStep>, List<Float>> {
    val extra = tokens.responsive.extra
    fun pick(key: String): Float? = extra[key]?.let {
        when (sizeClass) {
            StapelSizeClass.Phone -> it.phone
            StapelSizeClass.Tablet -> it.tablet
            StapelSizeClass.Desktop -> it.desktop
        }
    }
    val fs = tokens.fontSize
    val base = listOf(fs.xs, fs.sm, fs.md, fs.lg, fs.xl, fs.xxl, fs.xxxl)
    val type = base.mapIndexed { i, step ->
        val name = TYPE_STEP_NAMES[i]
        val size = pick("type.$name")
        val line = pick("type.$name.line")
        if (size == null && line == null) step else TypeStep(size ?: step.fontSize, line ?: step.lineHeight)
    }
    val sp = tokens.spacing
    val space = listOf(sp.s0, sp.s1, sp.s2, sp.s3, sp.s4, sp.s5, sp.s6, sp.s7, sp.s8).mapIndexed { i, v -> pick("space.$i") ?: v }
    return type to space
}

internal fun buildThemeValues(
    tokens: StapelTokens,
    mode: ThemeMode,
    fonts: StapelFonts,
    layout: StapelLayout,
    skin: StapelSkin,
    icons: StapelIconSet,
): StapelThemeValues {
    val w = tokens.fontWeight
    val (fs, sp) = responsiveScales(tokens, layout.sizeClass)
    val r = tokens.radii
    val e = tokens.elevation
    return StapelThemeValues(
        tokens = tokens,
        mode = mode,
        colors = StapelColors(tokens.colors(mode)),
        type = StapelTypography(
            fs[0].style(fonts.sans, w.regular), fs[1].style(fonts.sans, w.regular), fs[2].style(fonts.sans, w.regular),
            fs[3].style(fonts.sans, w.regular), fs[4].style(fonts.sans, w.regular), fs[5].style(fonts.sans, w.regular),
            fs[6].style(fonts.sans, w.regular), w, fonts.sans, fonts.mono,
        ),
        spacing = StapelSpacing(sp[0].dp, sp[1].dp, sp[2].dp, sp[3].dp, sp[4].dp, sp[5].dp, sp[6].dp, sp[7].dp, sp[8].dp),
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
 * @param layoutDirection an explicit direction for the subtree; null (the default) derives it from the
 *   active locale of [strings] ([layoutDirectionOf]), so an in-app switch to a right-to-left language
 *   mirrors the UI. An explicit value is inherited by nested themes that pass none.
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
    layoutDirection: LayoutDirection? = null,
    content: @Composable () -> Unit,
) {
    val resolvedFonts = fonts.resolve(tokens.fontFamily)
    val values = remember(tokens, mode, resolvedFonts, layout, skin, icons) {
        buildThemeValues(tokens, mode, resolvedFonts, layout, skin, icons)
    }
    val locale by strings.locale.collectAsState()
    val explicit = layoutDirection ?: LocalExplicitLayoutDirection.current
    CompositionLocalProvider(
        LocalLayoutDirection provides (explicit ?: layoutDirectionOf(locale)),
        LocalExplicitLayoutDirection provides explicit,
        LocalStapelTheme provides values,
        LocalStapelStrings provides strings,
        LocalStapelContentColor provides values.colors.text,
        LocalStapelReduceMotion provides reduceMotion,
        content = content,
    )
}
