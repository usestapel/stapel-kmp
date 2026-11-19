package stapel.tokens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The mobile gutter canon: 4 px on a phone, 8 px on a tablet (the web's pageGutter test twin). */
internal fun gutterCanonViolations(tokens: StapelTokens): List<String> = buildList {
    val g = tokens.responsive.pageGutter
    if (g.phone != 4f) add("phone gutter ${g.phone} != 4")
    if (g.tablet != 8f) add("tablet gutter ${g.tablet} != 8")
}

class StapelDefaultTokensTest {
    @Test
    fun gutterCanonHoldsForTheDefaultAndEveryPreset() {
        assertEquals(emptyList(), gutterCanonViolations(StapelDefaultTokens))
        StapelPresetTokens.all.forEach { (name, t) -> assertEquals(emptyList(), gutterCanonViolations(t), name) }
        assertEquals(24f, StapelDefaultTokens.responsive.pageGutter.desktop)
    }

    @Test
    fun gutterCanonCheckRejectsAnotherGutter() {
        val d = StapelDefaultTokens
        val wide = StapelTokens(
            d.light, d.dark, d.fontFamily, d.fontSize, d.fontWeight, d.spacing, d.radii, d.controls, d.breakpoints,
            ResponsiveTokens(Responsive(phone = 8f, tablet = 8f, desktop = 24f)), d.elevation, "test",
        )
        assertEquals(1, gutterCanonViolations(wide).size, "an 8 px phone gutter must violate the canon")
    }

    @Test
    fun defaultValuesComeFromTheSource() {
        val t = StapelDefaultTokens
        assertEquals("#ffffff", t.light.surface.toHex())
        assertEquals("#0b0e14", t.dark.surface.toHex())
        assertEquals("#4657d9", t.light[ColorRole.Brand].toHex())
        assertEquals("#98a5fa", t.colors(ThemeMode.Dark).brand.toHex())
        assertEquals(listOf(0f, 768f, 1200f), listOf(t.breakpoints.phone, t.breakpoints.tablet, t.breakpoints.desktop))
        assertEquals(44f, t.controls.heightPhone)
        assertEquals(16f, t.fontSize.md.fontSize)
        assertEquals(36f, t.fontSize.xxxl.fontSize)
        assertEquals(8f, t.radii.md)
        assertEquals(16f, t.spacing[4])
        assertNull(t.elevation.none.light)
        assertEquals("#0f121814", t.elevation.low.light!!.color.toHex())
        assertEquals("Inter", t.fontFamily.sans.first())
        assertTrue(t.sourceId.startsWith("@stapel/tokens@"))
    }

    @Test
    fun everyCanonRoleIsAddressable() {
        ColorRole.entries.forEach { role ->
            assertEquals(StapelDefaultTokens.light[role], StapelDefaultTokens.light.getOrNull(role.key))
            assertEquals(role, ColorRole.fromKey(role.key))
        }
        assertEquals(33, ColorRole.entries.size)
        assertNull(StapelDefaultTokens.light.getOrNull("no-such-role"))
    }

    @Test
    fun statusFamiliesResolveCanonAndExtra() {
        val s = StapelDefaultTokens.light.status("error")!!
        assertEquals(StapelDefaultTokens.light.errorBg, s.bg)
        assertNull(StapelDefaultTokens.light.status("promo"))
        val d = StapelDefaultTokens.light
        val extra = ColorTokens(
            d.surface, d.surfaceRaised, d.surfaceSunken, d.surfaceOverlay, d.text, d.textMuted, d.textSubtle, d.textOnAccent,
            d.border, d.borderSubtle, d.focusRing, d.brand, d.brandHover, d.brandActive, d.brandSubtle, d.link, d.linkHover,
            d.success, d.successBg, d.successBorder, d.successOn, d.warning, d.warningBg, d.warningBorder, d.warningOn,
            d.error, d.errorBg, d.errorBorder, d.errorOn, d.info, d.infoBg, d.infoBorder, d.infoOn,
            extra = mapOf("promo" to Argb(1u), "promo-bg" to Argb(2u), "promo-border" to Argb(3u), "promo-on" to Argb(4u)),
        )
        assertEquals(Argb(3u), extra.status("promo")!!.border)
    }

    @Test
    fun presetsDifferOnlyInTheBrandRamp() {
        val ocean = StapelPresetTokens.all.getValue("ocean")
        assertEquals(6, StapelPresetTokens.all.size)
        assertTrue(StapelPresetTokens.all.getValue("default") === StapelDefaultTokens)
        assertNotEquals(StapelDefaultTokens.light.brand, ocean.light.brand)
        assertEquals(StapelDefaultTokens.light.surface, ocean.light.surface)
        assertEquals(StapelDefaultTokens.dark.error, ocean.dark.error)
    }

    @Test
    fun argbHexRoundTripAndContrast() {
        assertEquals("#0f121814", Argb.fromHex("#0f121814").toHex())
        assertEquals(Argb(0xFFAABBCCu), Argb.fromHex("#abc"))
        assertEquals(21.0, Argb.fromHex("#000000").contrastRatio(Argb.fromHex("#ffffff")), 1e-9)
        assertFailsWith<IllegalArgumentException> { Argb.fromHex("red") }
    }
}
