package stapel.ui

import androidx.compose.ui.unit.dp
import stapel.tokens.Breakpoints
import stapel.tokens.ColorRole
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.StapelPresetTokens
import stapel.tokens.StapelTokens
import stapel.tokens.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutTest {
    private val t = StapelDefaultTokens

    @Test
    fun sizeClassesComeFromTheTokenBreakpoints() {
        val b = t.breakpoints
        assertEquals(StapelSizeClass.Phone, StapelSizeClass.of(390f, b))
        assertEquals(StapelSizeClass.Phone, StapelSizeClass.of(767.9f, b))
        assertEquals(StapelSizeClass.Tablet, StapelSizeClass.of(768f, b))
        assertEquals(StapelSizeClass.Tablet, StapelSizeClass.of(1199f, b))
        assertEquals(StapelSizeClass.Desktop, StapelSizeClass.of(1200f, b))
        // Not Material's 600/840: 600 dp is still a phone here.
        assertEquals(StapelSizeClass.Phone, StapelSizeClass.of(600f, b))
    }

    @Test
    fun anotherThemesBreakpointsMoveTheClasses() {
        val custom = Breakpoints(phone = 0f, tablet = 600f, desktop = 900f)
        assertEquals(StapelSizeClass.Tablet, StapelSizeClass.of(600f, custom))
        assertEquals(StapelSizeClass.Desktop, StapelSizeClass.of(1000f, custom))
        val tokens = withBreakpoints(custom)
        assertEquals(StapelSizeClass.Tablet, stapelLayout(tokens, 700.dp, StapelInput.Touch, 0.dp).sizeClass)
        assertEquals(StapelSizeClass.Phone, stapelLayout(t, 700.dp, StapelInput.Touch, 0.dp).sizeClass)
    }

    @Test
    fun gutterCanonFollowsTheSizeClass() {
        assertEquals(4.dp, stapelLayout(t, 390.dp, StapelInput.Touch, 48.dp).pageGutter)
        assertEquals(8.dp, stapelLayout(t, 800.dp, StapelInput.Touch, 48.dp).pageGutter)
        assertEquals(24.dp, stapelLayout(t, 1280.dp, StapelInput.Pointer, 0.dp).pageGutter)
    }

    @Test
    fun touchAndPointerControlMetrics() {
        val android = stapelLayout(t, 390.dp, StapelInput.Touch, 48.dp)
        assertEquals(44.dp, android.controlHeight)
        assertEquals(48.dp, android.minTouchTarget)
        assertEquals(44.dp, stapelLayout(t, 390.dp, StapelInput.Touch, 44.dp).minTouchTarget)
        val desktop = stapelLayout(t, 1280.dp, StapelInput.Pointer, 0.dp)
        assertEquals(32.dp, desktop.controlHeight)
        assertEquals(0.dp, desktop.minTouchTarget)
    }

    @Test
    fun dialogIsASheetOnlyOnAPhone() {
        assertEquals(DialogSurface.Sheet, stapelLayout(t, 390.dp, StapelInput.Touch, 0.dp).dialogSurface)
        assertEquals(DialogSurface.Modal, stapelLayout(t, 800.dp, StapelInput.Touch, 0.dp).dialogSurface)
    }

    @Test
    fun colorsAreTheTokenRoles() {
        val presets = StapelPresetTokens.all.values
        for (tokens in presets) {
            for (mode in ThemeMode.entries) {
                val c = StapelColors(tokens.colors(mode))
                ColorRole.entries.forEach { role -> assertEquals(tokens.colors(mode)[role].toColor(), c[role]) }
                assertEquals(c.errorBg, c.status(StapelTone.Error).bg)
            }
        }
    }

    private fun withBreakpoints(b: Breakpoints) = StapelTokens(
        t.light, t.dark, t.fontFamily, t.fontSize, t.fontWeight, t.spacing, t.radii, t.controls, b, t.responsive, t.elevation, "test",
    )
}
