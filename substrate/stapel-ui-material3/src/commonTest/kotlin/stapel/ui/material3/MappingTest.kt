package stapel.ui.material3

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import stapel.tokens.Argb
import stapel.tokens.ColorTokens
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.StapelPresetTokens
import stapel.tokens.StapelTokens
import stapel.tokens.ThemeMode
import stapel.ui.StapelFonts
import stapel.ui.toColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun Color.argb(): Argb = Argb(toArgb().toUInt())

/** Every pair the mapping creates below its minimum, computed with the WCAG formula, for [tokens] in both modes. */
internal fun contrastFailures(tokens: StapelTokens): List<String> = ThemeMode.entries.flatMap { mode ->
    mappingPairs(tokens.toColorScheme(mode), tokens, mode).mapNotNull { p ->
        val ratio = p.fg.argb().contrastRatio(p.bg.argb())
        if (ratio < p.minimum) "${tokens.sourceId} $mode ${p.name} = ${(ratio * 100).toInt() / 100.0}:1 < ${p.minimum}" else null
    }
}

class MappingTest {
    @Test
    fun everyMappedPairPassesForTheDefaultAndEveryPresetInBothModes() {
        assertEquals(6, StapelPresetTokens.all.size)
        val failures = StapelPresetTokens.all.values.flatMap(::contrastFailures)
        assertEquals(emptyList(), failures)
        // The pair list is not trivially small: 11 scheme pairs + 6 per surface x 10 + 10 extras.
        assertEquals(81, mappingPairs(StapelDefaultTokens.toColorScheme(ThemeMode.Light), StapelDefaultTokens, ThemeMode.Light).size)
    }

    @Test
    fun theGateCatchesALowContrastTheme() {
        val d = StapelDefaultTokens
        val l = d.light
        // text-on-accent equal to brand: the primary button label disappears.
        val broken = ColorTokens(
            l.surface, l.surfaceRaised, l.surfaceSunken, l.surfaceOverlay, l.text, l.textMuted, l.textSubtle, l.brand,
            l.border, l.borderSubtle, l.focusRing, l.brand, l.brandHover, l.brandActive, l.brandSubtle, l.link, l.linkHover,
            l.success, l.successBg, l.successBorder, l.successOn, Argb.fromHex("#f5c46b"), l.warningBg, l.warningBorder, l.warningOn,
            l.error, l.errorBg, l.errorBorder, l.errorOn, l.info, l.infoBg, l.infoBorder, l.infoOn,
        )
        val tokens = StapelTokens(broken, d.dark, d.fontFamily, d.fontSize, d.fontWeight, d.spacing, d.radii, d.controls, d.breakpoints, d.responsive, d.elevation, "broken")
        val failures = contrastFailures(tokens)
        assertTrue(failures.any { "Light onPrimary/primary" in it }, failures.toString())
        assertTrue(failures.any { "Light warning/surface" in it }, failures.toString())
        assertTrue(failures.none { "Dark" in it }, failures.toString())
    }

    @Test
    fun schemeSlotsComeFromRoles() {
        val t = StapelPresetTokens.ocean
        for (mode in ThemeMode.entries) {
            val c = t.colors(mode)
            val other = t.colors(if (mode == ThemeMode.Light) ThemeMode.Dark else ThemeMode.Light)
            val s = t.toColorScheme(mode)
            assertEquals(c.brand.toColor(), s.primary)
            assertEquals(c.brand.toColor(), s.secondary)
            assertEquals(c.textOnAccent.toColor(), s.onPrimary)
            assertEquals(c.surfaceRaised.toColor(), s.surfaceContainerLow)
            assertEquals(c.surfaceOverlay.toColor(), s.surfaceContainerHigh)
            assertEquals(c.textMuted.toColor(), s.onSurfaceVariant)
            assertEquals(c.border.toColor(), s.outline)
            assertEquals(c.errorBg.toColor(), s.errorContainer)
            assertEquals(other.brand.toColor(), s.inversePrimary)
            assertEquals(other.surfaceOverlay.toColor(), s.inverseSurface)
        }
    }

    @Test
    fun typographyAndShapesFollowTheScale() {
        val t = StapelDefaultTokens
        val type = t.toTypography(StapelFonts(FontFamily.Default, FontFamily.Monospace))
        assertEquals(16.sp, type.bodyLarge.fontSize)
        assertEquals(14.sp, type.bodyMedium.fontSize)
        assertEquals(36.sp, type.displayLarge.fontSize)
        assertEquals(22.sp, type.titleLarge.fontSize)
        assertEquals(0.sp, type.labelLarge.letterSpacing)
        val shapes = t.toShapes()
        assertEquals(androidx.compose.foundation.shape.RoundedCornerShape(t.radii.md.toInt().let { androidx.compose.ui.unit.Dp(it.toFloat()) }), shapes.small)
    }
}
