package stapel.showcase.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import stapel.showcase.Story
import stapel.showcase.StoryCatalog
import stapel.showcase.gallery.generated.DiscoveredCatalog
import stapel.showcase.gallery.generated.discoveredCatalogs
import stapel.showcase.gallery.tools.SNAPSHOT_VARIANTS
import stapel.showcase.gallery.tools.indexJson
import stapel.showcase.gallery.tools.record
import stapel.showcase.gallery.tools.verify
import stapel.tokens.ColorRole
import stapel.ui.StapelTheme
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The snapshot verifier is proven able to fail; the index carries what the coverage gate reads. */
class SnapshotToolTest {
    private fun probe(color: ColorRole, extraStory: Boolean = false) = DiscoveredCatalog(
        ":probe", "probe",
        StoryCatalog(
            "probe", "Probe",
            listOfNotNull(
                Story("swatch", "Swatch", "Probe") { Box(Modifier.size(40.dp).background(StapelTheme.colors[color])) },
                if (extraStory) Story("extra", "Extra", "Probe") { Box(Modifier.size(10.dp)) } else null,
            ),
        ),
    )

    @Test
    fun verifyIsGreenOnItsOwnRecordingAndRedWhenAColourChanges() {
        val root = Files.createTempDirectory("snapshots").toFile()
        val report = File(root, "report")
        val (written, unchanged, removed) = record(probe(ColorRole.Brand), root)
        assertEquals(Triple(SNAPSHOT_VARIANTS.size, 0, 0), Triple(written, unchanged, removed))
        assertEquals(emptyList(), verify(probe(ColorRole.Brand), root, report))
        assertEquals(Triple(0, SNAPSHOT_VARIANTS.size, 0), record(probe(ColorRole.Brand), root), "re-recording unchanged pixels rewrites nothing")

        val red = verify(probe(ColorRole.Error), root, report)
        assertEquals(SNAPSHOT_VARIANTS.size, red.size, red.toString())
        assertTrue(red.all { it.reason.endsWith("pixel(s) differ") }, red.toString())
        assertTrue(File(report, "probe/swatch__default__material3-light/diff.png").isFile)
        assertTrue(File(report, "probe/swatch__default__material3-light/actual.png").isFile)
    }

    @Test
    fun missingAndOrphanBaselinesAreProblems() {
        val root = Files.createTempDirectory("snapshots").toFile()
        record(probe(ColorRole.Brand), root)
        val missing = verify(probe(ColorRole.Brand, extraStory = true), root, File(root, "r1"))
        assertEquals(SNAPSHOT_VARIANTS.size, missing.count { it.reason.startsWith("no baseline") })
        File(root, "probe/snapshots/gone__default__material3-light.png").writeBytes(File(root, "probe/snapshots/swatch__default__material3-light.png").readBytes())
        val orphan = verify(probe(ColorRole.Brand), root, File(root, "r2"))
        assertEquals(listOf("gone__default__material3-light.png"), orphan.map { it.file })
        assertEquals(Triple(0, SNAPSHOT_VARIANTS.size, 1), record(probe(ColorRole.Brand), root), "record removes the orphan")
    }

    @Test
    fun anRtlStateIsRecordedRightToLeftAndAFixedCanvasKeepsItsHeight() {
        val story = Story(
            "dir", "Direction", "Probe",
            states = listOf(stapel.showcase.StoryState("default", "LTR"), stapel.showcase.StoryState("rtl", "RTL", rtl = true, locale = "ar")),
            canvas = stapel.showcase.CanvasOptions(padding = 0.dp, height = 60.dp),
        ) {
            androidx.compose.foundation.layout.Row {
                Box(Modifier.size(40.dp).background(StapelTheme.colors[ColorRole.Brand]))
                Box(Modifier.size(40.dp).background(StapelTheme.colors[ColorRole.Error]))
            }
        }
        val entry = DiscoveredCatalog(":probe", "probe", StoryCatalog("probe", "Probe", listOf(story)))
        val controls = GalleryControls(reduceMotion = true)
        fun png(state: Int) = stapel.showcase.gallery.tools.decode(
            stapel.showcase.gallery.tools.renderPng(entry, story, story.states[state], controls, stapel.tokens.ThemeMode.Light),
        )
        val ltr = png(0)
        val rtl = png(1)
        assertEquals(60, ltr.height, "a fixed canvas height is the image height")
        assertEquals(ltr.getRGB(20, 20), rtl.getRGB(ltr.width - 21, 20), "the first box is leftmost LTR and rightmost RTL")
        assertTrue(ltr.getRGB(20, 20) != rtl.getRGB(20, 20), "the RTL state is not the LTR picture")
    }

    @Test
    fun screensRenderInTheViewportAndAreNotBlank() {
        val auth = discoveredCatalogs.first { it.catalog.id == "auth" }
        val story = auth.catalog.story("code.error")!!
        val img = stapel.showcase.gallery.tools.decode(
            stapel.showcase.gallery.tools.renderPng(auth, story, story.states.first(), GalleryControls(reduceMotion = true), stapel.tokens.ThemeMode.Light),
        )
        assertEquals(390 to 844, img.width to img.height, "a screen story is drawn in the phone viewport")
        val colours = img.getRGB(0, 0, img.width, 300, null, 0, img.width).toSet()
        assertTrue(colours.size > 16, "the screen is not blank (${colours.size} colours)")
    }

    @Test
    fun theIndexCarriesCoverageAndStates() {
        val index = indexJson(discoveredCatalogs)
        val catalogs = index["catalogs"]!!.jsonArray.map { it.jsonObject }
        val ui = catalogs.first { it["id"]!!.jsonPrimitive.content == "ui" }
        assertEquals(":showcase:stories:ui", ui["module"]!!.jsonPrimitive.content)
        val button = ui["stories"]!!.jsonArray.map { it.jsonObject }.first { it["id"]!!.jsonPrimitive.content == "button" }
        assertEquals(listOf("slot:Button"), button["covers"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertTrue(button["states"]!!.jsonArray.size >= 3)
        assertEquals("choice", button["knobs"]!!.jsonArray.first().jsonObject["kind"]!!.jsonPrimitive.content)
    }
}
