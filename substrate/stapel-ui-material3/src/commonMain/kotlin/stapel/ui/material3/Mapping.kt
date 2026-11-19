package stapel.ui.material3

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import stapel.tokens.StapelTokens
import stapel.tokens.ThemeMode
import stapel.tokens.TypeStep
import stapel.ui.StapelFonts
import stapel.ui.toColor

/**
 * The §68 roles as a Material 3 [ColorScheme] for [mode] (the fallback for Material components an app
 * uses directly; `Material3Skin` colours its slots from the roles explicitly). Every `on-X / X` pair
 * the scheme creates is a pair the token contrast gate already checks. `secondary`/`tertiary` collapse
 * onto `brand` (the dictionary has one accent); the inverse slots take the other mode's roles; `scrim`
 * keeps Material's default. Dynamic colour is never used.
 */
public fun StapelTokens.toColorScheme(mode: ThemeMode): ColorScheme {
    val c = colors(mode)
    val other = colors(if (mode == ThemeMode.Light) ThemeMode.Dark else ThemeMode.Light)
    val brand = c.brand.toColor()
    val onBrand = c.textOnAccent.toColor()
    val brandSubtle = c.brandSubtle.toColor()
    val text = c.text.toColor()
    val surface = c.surface.toColor()
    val raised = c.surfaceRaised.toColor()
    val sunken = c.surfaceSunken.toColor()
    val overlay = c.surfaceOverlay.toColor()
    return ColorScheme(
        primary = brand, onPrimary = onBrand, primaryContainer = brandSubtle, onPrimaryContainer = text,
        inversePrimary = other.brand.toColor(),
        secondary = brand, onSecondary = onBrand, secondaryContainer = brandSubtle, onSecondaryContainer = text,
        tertiary = brand, onTertiary = onBrand, tertiaryContainer = brandSubtle, onTertiaryContainer = text,
        background = surface, onBackground = text, surface = surface, onSurface = text,
        surfaceVariant = sunken, onSurfaceVariant = c.textMuted.toColor(), surfaceTint = brand,
        inverseSurface = other.surfaceOverlay.toColor(), inverseOnSurface = other.text.toColor(),
        error = c.error.toColor(), onError = c.errorOn.toColor(), errorContainer = c.errorBg.toColor(), onErrorContainer = text,
        outline = c.border.toColor(), outlineVariant = c.borderSubtle.toColor(), scrim = Color.Black.copy(alpha = 0.32f),
        surfaceBright = raised, surfaceDim = sunken, surfaceContainer = raised, surfaceContainerHigh = overlay,
        surfaceContainerHighest = sunken, surfaceContainerLow = raised, surfaceContainerLowest = surface,
        primaryFixed = brandSubtle, primaryFixedDim = brand, onPrimaryFixed = text, onPrimaryFixedVariant = text,
        secondaryFixed = brandSubtle, secondaryFixedDim = brand, onSecondaryFixed = text, onSecondaryFixedVariant = text,
        tertiaryFixed = brandSubtle, tertiaryFixedDim = brand, onTertiaryFixed = text, onTertiaryFixedVariant = text,
    )
}

private fun TypeStep.style(fonts: StapelFonts, weight: Int) =
    TextStyle(fontFamily = fonts.sans, fontWeight = FontWeight(weight), fontSize = fontSize.sp, lineHeight = lineHeight.sp, letterSpacing = 0.sp)

/** The type scale as Material 3 [Typography] (`lg` has no Material slot; letter spacing 0). */
public fun StapelTokens.toTypography(fonts: StapelFonts): Typography {
    val s = fontSize
    val w = fontWeight
    return Typography(
        displayLarge = s.xxxl.style(fonts, w.bold),
        displayMedium = s.xxxl.style(fonts, w.bold),
        displaySmall = s.xxxl.style(fonts, w.bold),
        headlineLarge = s.xxl.style(fonts, w.semibold),
        headlineMedium = s.xxl.style(fonts, w.semibold),
        headlineSmall = s.xl.style(fonts, w.semibold),
        titleLarge = s.xl.style(fonts, w.semibold),
        titleMedium = s.md.style(fonts, w.semibold),
        titleSmall = s.sm.style(fonts, w.medium),
        bodyLarge = s.md.style(fonts, w.regular),
        bodyMedium = s.sm.style(fonts, w.regular),
        bodySmall = s.xs.style(fonts, w.regular),
        labelLarge = s.sm.style(fonts, w.medium),
        labelMedium = s.xs.style(fonts, w.medium),
        labelSmall = s.xs.style(fonts, w.medium),
    )
}

/** The radius scale as Material 3 [Shapes]: extraSmall = sm, small = md, medium = lg, large = extraLarge = xl. */
public fun StapelTokens.toShapes(): Shapes = Shapes(
    extraSmall = RoundedCornerShape(radii.sm.dp),
    small = RoundedCornerShape(radii.md.dp),
    medium = RoundedCornerShape(radii.lg.dp),
    large = RoundedCornerShape(radii.xl.dp),
    extraLarge = RoundedCornerShape(radii.xl.dp),
)

/**
 * The text-on-fill pairs the [toColorScheme] mapping creates plus the ones `Material3Skin` draws
 * (brand and error text on every surface, brand on brand-subtle, subtle text on sunken, status text
 * on surface), as `(name, foreground, background, minimum ratio)`.
 */
internal fun mappingPairs(scheme: ColorScheme, tokens: StapelTokens, mode: ThemeMode): List<ContrastPair> {
    val c = tokens.colors(mode)
    val surfaces = listOf(
        "surface" to scheme.surface, "background" to scheme.background, "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        "surfaceContainerLow" to scheme.surfaceContainerLow, "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh, "surfaceContainerHighest" to scheme.surfaceContainerHighest,
        "surfaceVariant" to scheme.surfaceVariant, "surfaceBright" to scheme.surfaceBright, "surfaceDim" to scheme.surfaceDim,
    )
    val text = 4.5
    return buildList {
        add(ContrastPair("onPrimary/primary", scheme.onPrimary, scheme.primary, text))
        add(ContrastPair("onSecondary/secondary", scheme.onSecondary, scheme.secondary, text))
        add(ContrastPair("onTertiary/tertiary", scheme.onTertiary, scheme.tertiary, text))
        add(ContrastPair("onPrimaryContainer/primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer, text))
        add(ContrastPair("onSecondaryContainer/secondaryContainer", scheme.onSecondaryContainer, scheme.secondaryContainer, text))
        add(ContrastPair("onTertiaryContainer/tertiaryContainer", scheme.onTertiaryContainer, scheme.tertiaryContainer, text))
        add(ContrastPair("onError/error", scheme.onError, scheme.error, text))
        add(ContrastPair("onErrorContainer/errorContainer", scheme.onErrorContainer, scheme.errorContainer, text))
        add(ContrastPair("inverseOnSurface/inverseSurface", scheme.inverseOnSurface, scheme.inverseSurface, text))
        add(ContrastPair("onPrimaryFixed/primaryFixed", scheme.onPrimaryFixed, scheme.primaryFixed, text))
        add(ContrastPair("onPrimaryFixedVariant/primaryFixed", scheme.onPrimaryFixedVariant, scheme.primaryFixed, text))
        for ((name, bg) in surfaces) {
            add(ContrastPair("onSurface/$name", scheme.onSurface, bg, text))
            add(ContrastPair("onBackground/$name", scheme.onBackground, bg, text))
            add(ContrastPair("onSurfaceVariant/$name", scheme.onSurfaceVariant, bg, text))
            add(ContrastPair("primary(text button)/$name", scheme.primary, bg, text))
            add(ContrastPair("error(field error)/$name", scheme.error, bg, text))
            add(ContrastPair("focus-ring/$name", c.focusRing.toColor(), bg, 3.0))
        }
        add(ContrastPair("brand/brand-subtle", c.brand.toColor(), c.brandSubtle.toColor(), text))
        add(ContrastPair("text-subtle/surface-sunken", c.textSubtle.toColor(), c.surfaceSunken.toColor(), text))
        for (status in listOf("success", "warning", "info", "error")) {
            add(ContrastPair("$status/surface", c.getOrNull(status)!!.toColor(), c.surface.toColor(), text))
            add(ContrastPair("$status/surface-overlay", c.getOrNull(status)!!.toColor(), c.surfaceOverlay.toColor(), text))
        }
    }
}

/** One foreground/background pair with its WCAG minimum. */
internal class ContrastPair(val name: String, val fg: Color, val bg: Color, val minimum: Double)
