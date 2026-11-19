package stapel.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import stapel.tokens.ThemeMode
import stapel.ui.skin.FoundationSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class IconsTest {
    @Test
    fun everyDefaultIdLoadsFromTheBundledResourcesAndDraws() = runComposeUiTest {
        val ids = StapelIconIdsDrawn.toList()
        val resolved = mutableMapOf<String, ImageVector?>()
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light) {
                androidx.compose.foundation.layout.Column {
                    ids.chunked(6).forEach { row ->
                        Row {
                            row.forEach { id ->
                                resolved[id] = StapelIcons.resolve(id)
                                StapelIconView(StapelIcon(id), null, Modifier.testTag("icon.$id"), size = 32.dp)
                            }
                        }
                    }
                }
                resolved["no-such-icon"] = StapelIcons.resolve("no-such-icon")
            }
        }
        waitForIdle()
        assertNull(resolved["no-such-icon"], "an id the set does not draw resolves to null")
        assertEquals(28, ids.size)
        for (id in ids) {
            val v = resolved[id]
            assertTrue(v != null && v.viewportWidth == 24f, "'$id' loads from composeResources")
            val px = onNodeWithTag("icon.$id").captureToImage().toPixelMap()
            var ink = 0
            for (x in 0 until px.width) for (y in 0 until px.height) if (px[x, y].alpha > 0.3f) ink++
            assertTrue(ink > 5, "'$id' draws visible strokes ($ink pixels)")
        }
        assertTrue(resolved["arrow-back"]!!.autoMirror, "directional glyphs mirror in RTL")
    }
}
