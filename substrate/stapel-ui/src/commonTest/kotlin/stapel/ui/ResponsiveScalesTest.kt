package stapel.ui

import stapel.tokens.Responsive
import stapel.tokens.ResponsiveTokens
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.StapelTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ResponsiveScalesTest {
    private fun withExtra(extra: Map<String, Responsive<Float>>): StapelTokens = StapelDefaultTokens.let {
        StapelTokens(
            it.light, it.dark, it.fontFamily, it.fontSize, it.fontWeight, it.spacing, it.radii, it.controls, it.breakpoints,
            ResponsiveTokens(it.responsive.pageGutter, extra), it.elevation, it.sourceId,
        )
    }

    @Test
    fun withoutResponsiveRolesEverySizeClassKeepsTheSingleScale() {
        StapelSizeClass.entries.forEach { sc ->
            val (type, space) = responsiveScales(StapelDefaultTokens, sc)
            assertSame(StapelDefaultTokens.fontSize.md, type[2])
            assertEquals(StapelDefaultTokens.spacing.s4, space[4])
        }
    }

    @Test
    fun aResponsiveRolePicksItsSizeClassValueAndOnlyThatRoleMoves() {
        val tokens = withExtra(
            mapOf(
                "type.md" to Responsive(16f, 17f, 18f),
                "type.md.line" to Responsive(24f, 26f, 28f),
                "space.4" to Responsive(16f, 20f, 24f),
            ),
        )
        val (phoneType, phoneSpace) = responsiveScales(tokens, StapelSizeClass.Phone)
        val (deskType, deskSpace) = responsiveScales(tokens, StapelSizeClass.Desktop)
        assertEquals(16f, phoneType[2].fontSize)
        assertEquals(18f, deskType[2].fontSize)
        assertEquals(28f, deskType[2].lineHeight)
        assertEquals(20f, responsiveScales(tokens, StapelSizeClass.Tablet).second[4])
        assertEquals(16f, phoneSpace[4])
        assertEquals(24f, deskSpace[4])
        assertSame(StapelDefaultTokens.fontSize.lg, deskType[3])
        assertEquals(StapelDefaultTokens.spacing.s3, deskSpace[3])
    }

    @Test
    fun theThemeAppliesTheSizeClassScale() {
        val tokens = withExtra(mapOf("type.md" to Responsive(15f, 17f, 19f), "space.2" to Responsive(8f, 10f, 12f)))
        val tablet = buildThemeValues(tokens, stapel.tokens.ThemeMode.Light, StapelFonts(androidx.compose.ui.text.font.FontFamily.Default, androidx.compose.ui.text.font.FontFamily.Monospace),
            stapelLayout(tokens, androidx.compose.ui.unit.Dp(800f), StapelInput.Touch, androidx.compose.ui.unit.Dp(44f)), stapel.ui.skin.FoundationSkin, StapelIcons)
        assertEquals(17f, tablet.type.body.fontSize.value)
        assertEquals(10f, tablet.spacing.s2.value)
    }
}
