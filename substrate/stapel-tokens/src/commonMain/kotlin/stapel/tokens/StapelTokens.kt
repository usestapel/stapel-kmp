package stapel.tokens

/**
 * One step of the type scale, in px (`stapel-ui` reads them as sp).
 *
 * @property fontSize font size.
 * @property lineHeight line height.
 */
public class TypeStep(public val fontSize: Float, public val lineHeight: Float)

/** The seven steps of the type scale (`xs` … `3xl` in the source). */
public class TypeScale(
    /** `xs`. */
    public val xs: TypeStep,
    /** `sm`. */
    public val sm: TypeStep,
    /** `md`. */
    public val md: TypeStep,
    /** `lg`. */
    public val lg: TypeStep,
    /** `xl`. */
    public val xl: TypeStep,
    /** `2xl`. */
    public val xxl: TypeStep,
    /** `3xl`. */
    public val xxxl: TypeStep,
)

/** Font weights (CSS numeric weights). */
public class FontWeights(
    /** Regular (400 by default). */
    public val regular: Int,
    /** Medium (500). */
    public val medium: Int,
    /** Semibold (600). */
    public val semibold: Int,
    /** Bold (700). */
    public val bold: Int,
)

/** Ordered font-family stacks; a font resolver picks the first family it can provide. */
public class FontFamilies(
    /** UI text stack. */
    public val sans: List<String>,
    /** Monospace stack. */
    public val mono: List<String>,
)

/** The spacing scale (px), steps `0` … `8`. */
public class SpacingScale(
    /** Step 0. */
    public val s0: Float,
    /** Step 1. */
    public val s1: Float,
    /** Step 2. */
    public val s2: Float,
    /** Step 3. */
    public val s3: Float,
    /** Step 4. */
    public val s4: Float,
    /** Step 5. */
    public val s5: Float,
    /** Step 6. */
    public val s6: Float,
    /** Step 7. */
    public val s7: Float,
    /** Step 8. */
    public val s8: Float,
) {
    /** The value of [step] (0..8). */
    public operator fun get(step: Int): Float = when (step) {
        0 -> s0
        1 -> s1
        2 -> s2
        3 -> s3
        4 -> s4
        5 -> s5
        6 -> s6
        7 -> s7
        8 -> s8
        else -> throw IndexOutOfBoundsException("spacing step $step is outside 0..8")
    }
}

/** Corner radii (px). */
public class RadiusScale(
    /** No rounding. */
    public val none: Float,
    /** Small. */
    public val sm: Float,
    /** Medium: controls (buttons, fields). */
    public val md: Float,
    /** Large. */
    public val lg: Float,
    /** Extra large. */
    public val xl: Float,
    /** Pill. */
    public val full: Float,
)

/** Control heights (px). */
public class ControlTokens(
    /** Height under a pointer (desktop). */
    public val height: Float,
    /** Height and touch floor on a touch screen. */
    public val heightPhone: Float,
)

/** Breakpoints: the minimum window width (px) where each size class starts. */
public class Breakpoints(
    /** Phone (0). */
    public val phone: Float,
    /** Tablet. */
    public val tablet: Float,
    /** Desktop. */
    public val desktop: Float,
)

/** A value per size class. */
public class Responsive<T>(
    /** On a phone-width window. */
    public val phone: T,
    /** On a tablet-width window. */
    public val tablet: T,
    /** On a desktop-width window. */
    public val desktop: T,
)

/** Scale roles whose value changes with the window width. */
public class ResponsiveTokens(
    /** How far page content sits from the window edge — THE horizontal page inset. */
    public val pageGutter: Responsive<Float>,
    /** A host's further responsive roles, by key. */
    public val extra: Map<String, Responsive<Float>> = emptyMap(),
)

/**
 * One drop shadow (px lengths).
 *
 * @property x horizontal offset.
 * @property y vertical offset.
 * @property blur blur radius.
 * @property spread spread.
 * @property color shadow colour (with alpha).
 */
public class Shadow(public val x: Float, public val y: Float, public val blur: Float, public val spread: Float, public val color: Argb)

/**
 * A light/dark pair.
 *
 * @property light the light-mode value.
 * @property dark the dark-mode value.
 */
public class Themed<T>(public val light: T, public val dark: T) {
    /** The value for [mode]. */
    public operator fun get(mode: ThemeMode): T = if (mode == ThemeMode.Light) light else dark
}

/** Elevation steps; a null shadow draws none. */
public class ElevationScale(
    /** No elevation. */
    public val none: Themed<Shadow?>,
    /** Low (cards). */
    public val low: Themed<Shadow?>,
    /** Medium (menus). */
    public val medium: Themed<Shadow?>,
    /** High (dialogs). */
    public val high: Themed<Shadow?>,
)

/**
 * A whole resolved theme as a value: an app theme, a second brand and a test theme are all
 * instances of this type. Generated from `stapel.theme.json` by `tools/tokens-gen` (`compose@1`).
 */
public class StapelTokens(
    /** Light-mode colours. */
    public val light: ColorTokens,
    /** Dark-mode colours. */
    public val dark: ColorTokens,
    /** Font stacks. */
    public val fontFamily: FontFamilies,
    /** Type scale. */
    public val fontSize: TypeScale,
    /** Font weights. */
    public val fontWeight: FontWeights,
    /** Spacing scale. */
    public val spacing: SpacingScale,
    /** Radii. */
    public val radii: RadiusScale,
    /** Control heights. */
    public val controls: ControlTokens,
    /** Breakpoints. */
    public val breakpoints: Breakpoints,
    /** Responsive roles (page gutter). */
    public val responsive: ResponsiveTokens,
    /** Elevation shadows. */
    public val elevation: ElevationScale,
    /** `<package>@<version>+<theme sha8>`: which source this theme was generated from. */
    public val sourceId: String,
) {
    /** The colours of [mode]. */
    public fun colors(mode: ThemeMode): ColorTokens = if (mode == ThemeMode.Light) light else dark
}
