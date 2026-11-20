@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package stapel.showcase.gallery.tools

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.EncodedImageFormat
import stapel.showcase.Story
import stapel.showcase.StoryKind
import stapel.showcase.StoryState
import stapel.showcase.gallery.GalleryControls
import stapel.showcase.gallery.GallerySkin
import stapel.showcase.gallery.SizeChoice
import stapel.showcase.gallery.StoryFrame
import stapel.showcase.gallery.generated.DiscoveredCatalog
import stapel.tokens.ThemeMode
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/** One snapshot variant: a skin and a mode. */
data class Variant(val skin: GallerySkin, val mode: ThemeMode) {
    /** The file-name part (`material3-light`). */
    val id: String get() = "${skin.name.lowercase()}-${mode.name.lowercase()}"
}

/** The baseline matrix: {Material3, Foundation} × {light, dark}. */
val SNAPSHOT_VARIANTS: List<Variant> = GallerySkin.entries.flatMap { s -> listOf(ThemeMode.Light, ThemeMode.Dark).map { Variant(s, it) } }

/**
 * The pinned render policy of baselines: phone frame (390 dp), density 1, English, reduced motion,
 * default preset, a fixed frame time. Fonts are the platform's: baselines are recorded and verified on
 * macOS only (measured: Linux text rasterisation differs by ~4.6 % of pixels).
 */
fun snapshotControls(variant: Variant): GalleryControls =
    GalleryControls(skin = variant.skin, mode = stapel.showcase.gallery.ModeChoice.Light, locale = "en", size = SizeChoice.Phone, reduceMotion = true)

/** Baseline file name of [story] in [state] under [variant]. */
fun snapshotName(story: Story, state: StoryState, variant: Variant): String = "${story.id}__${state.id}__${variant.id}.png"

/** Renders [story] in [state] to PNG bytes: width from the controls' frame, height from the content. */
fun renderPng(entry: DiscoveredCatalog, story: Story, state: StoryState, controls: GalleryControls, mode: ThemeMode, density: Float = 1f): ByteArray {
    val widthPx = (controls.size.width.value * density).roundToInt()
    val content: @Composable () -> Unit = { StoryFrame(story, entry.catalog.environment, controls, mode, state = state) }
    val fixed = story.canvas.height
    val height = if (fixed != null && story.kind != StoryKind.Screen && story.kind != StoryKind.Flow) {
        // A fixed canvas (a story with a popup layer): the overlay cannot be measured, the canvas says the height.
        (fixed.value * density).roundToInt().coerceIn(1, MAX_HEIGHT)
    } else {
        val probe = ImageComposeScene(widthPx, MAX_HEIGHT, Density(density), content = content)
        try {
            probe.render(0)
            probe.calculateContentSize().height.coerceIn(1, MAX_HEIGHT)
        } finally {
            probe.close()
        }
    }
    val scene = ImageComposeScene(widthPx, height, Density(density), content = content)
    try {
        scene.render(0)
        val image = scene.render(FRAME_TIME_NANOS)
        return requireNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "PNG encoding failed for ${story.id}" }.bytes
    } finally {
        scene.close()
    }
}

private const val MAX_HEIGHT = 4000
private const val FRAME_TIME_NANOS = 1_000_000_000L

/** Decodes PNG bytes. */
fun decode(png: ByteArray): BufferedImage = ImageIO.read(ByteArrayInputStream(png))

/** Number of differing pixels, or -1 when the sizes differ. */
fun pixelDifference(a: BufferedImage, b: BufferedImage): Int {
    if (a.width != b.width || a.height != b.height) return -1
    val pa = a.getRGB(0, 0, a.width, a.height, null, 0, a.width)
    val pb = b.getRGB(0, 0, b.width, b.height, null, 0, b.width)
    return pa.indices.count { pa[it] != pb[it] }
}

/** Red where [a] and [b] differ, a faded [a] elsewhere (same size required). */
fun diffImage(a: BufferedImage, b: BufferedImage): BufferedImage {
    val out = BufferedImage(a.width, a.height, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until a.height) for (x in 0 until a.width) {
        val pa = a.getRGB(x, y)
        out.setRGB(x, y, if (pa != b.getRGB(x, y)) 0xFFFF0000.toInt() else (pa and 0x00FFFFFF) or 0x40000000)
    }
    return out
}

/** Every expected baseline of [entry]: file name → (story, state, variant). */
fun expectedSnapshots(entry: DiscoveredCatalog): Map<String, Triple<Story, StoryState, Variant>> =
    entry.catalog.stories.flatMap { story ->
        story.states.flatMap { state -> SNAPSHOT_VARIANTS.map { v -> snapshotName(story, state, v) to Triple(story, state, v) } }
    }.toMap()

/** Writes the baselines of [entry] under `<root>/<dir>/snapshots`; unchanged pixels keep the old file; stale files go. Returns (written, unchanged, removed). */
fun record(entry: DiscoveredCatalog, root: File): Triple<Int, Int, Int> {
    val dir = File(root, "${entry.dir}/snapshots").apply { mkdirs() }
    val expected = expectedSnapshots(entry)
    var written = 0
    var unchanged = 0
    for ((name, spec) in expected) {
        val (story, state, variant) = spec
        val png = renderPng(entry, story, state, snapshotControls(variant), variant.mode)
        val file = File(dir, name)
        if (file.isFile && pixelDifference(decode(file.readBytes()), decode(png)) == 0) {
            unchanged++
        } else {
            file.writeBytes(png)
            written++
        }
    }
    val stale = dir.listFiles().orEmpty().filter { it.extension == "png" && it.name !in expected }
    stale.forEach { it.delete() }
    return Triple(written, unchanged, stale.size)
}

/** One verification failure. */
data class SnapshotProblem(val catalog: String, val file: String, val reason: String)

/**
 * Renders every expected baseline of [entry] and compares it with `<root>/<dir>/snapshots` pixel by
 * pixel. Missing, differing and orphan files are problems; actual and diff images go to [report].
 */
fun verify(entry: DiscoveredCatalog, root: File, report: File): List<SnapshotProblem> {
    val dir = File(root, "${entry.dir}/snapshots")
    val expected = expectedSnapshots(entry)
    val problems = mutableListOf<SnapshotProblem>()
    for ((name, spec) in expected) {
        val (story, state, variant) = spec
        val file = File(dir, name)
        if (!file.isFile) {
            problems += SnapshotProblem(entry.catalog.id, name, "no baseline (run ./gradlew :showcase:gallery:recordScreenshots on macOS)")
            continue
        }
        val actualPng = renderPng(entry, story, state, snapshotControls(variant), variant.mode)
        val expectedImg = decode(file.readBytes())
        val actualImg = decode(actualPng)
        val diff = pixelDifference(expectedImg, actualImg)
        if (diff != 0) {
            val out = File(report, "${entry.catalog.id}/${name.removeSuffix(".png")}").apply { mkdirs() }
            File(out, "actual.png").writeBytes(actualPng)
            file.copyTo(File(out, "expected.png"), overwrite = true)
            if (diff > 0) ImageIO.write(diffImage(expectedImg, actualImg), "png", File(out, "diff.png"))
            problems += SnapshotProblem(
                entry.catalog.id, name,
                if (diff < 0) "size ${actualImg.width}x${actualImg.height} != baseline ${expectedImg.width}x${expectedImg.height}" else "$diff pixel(s) differ",
            )
        }
    }
    dir.listFiles().orEmpty().filter { it.extension == "png" && it.name !in expected }.forEach {
        problems += SnapshotProblem(entry.catalog.id, it.name, "orphan baseline: no story/state produces it (re-record)")
    }
    return problems
}
