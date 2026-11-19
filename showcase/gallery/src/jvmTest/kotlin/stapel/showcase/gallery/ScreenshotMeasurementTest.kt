package stapel.showcase.gallery

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
import java.awt.GraphicsEnvironment
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Measures what ARCH-UI §testing left open before any baseline is committed: does capture run with no
 * display (`java.awt.headless=true`), and is it deterministic on one machine. The numbers are written
 * to `build/captures/measurement.txt`. Cross-OS identity (macOS vs Linux) is not measurable here.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotMeasurementTest {
    private val dir = File(System.getProperty("stapel.showcase.captureDir") ?: "build/captures").apply { mkdirs() }

    private fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun pixels(file: File): IntArray {
        val img = ImageIO.read(file)
        return img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
    }

    @Test
    fun headlessCaptureIsDeterministicOnOneMachine() {
        // macOS only until the Linux runner's headless behaviour is measured on its own (CI uses xvfb).
        org.junit.Assume.assumeTrue(System.getProperty("os.name").startsWith("Mac"))
        val shots = mutableListOf<File>()
        val started = System.nanoTime()
        repeat(2) { run ->
            runDesktopComposeUiTest(width = 420, height = 2600) {
                mainClock.autoAdvance = false
                setContent { GalleryColumn(GallerySkin.Material3, StapelDefaultTokens, ThemeMode.Light, 390.dp, ruler = true) }
                mainClock.advanceTimeBy(1_000)
                waitForIdle()
                val roborazzi = File(dir, "gallery-m3-light-run$run.png")
                onRoot().captureRoboImage(roborazzi.path)
                val plain = File(dir, "gallery-m3-light-run$run.plain.png")
                ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", plain)
                shots += roborazzi
                shots += plain
            }
        }
        val millis = (System.nanoTime() - started) / 1_000_000
        val (r0, p0, r1, p1) = shots
        val img = ImageIO.read(r0)
        val report = buildString {
            appendLine("os=${System.getProperty("os.name")} ${System.getProperty("os.arch")} java=${System.getProperty("java.version")}")
            appendLine("headless=${GraphicsEnvironment.isHeadless()}")
            appendLine("size=${img.width}x${img.height} two runs in ${millis} ms")
            appendLine("roborazzi run0=${sha(r0)} run1=${sha(r1)}")
            appendLine("plain     run0=${sha(p0)} run1=${sha(p1)}")
            appendLine("pixels roborazzi==plain: ${pixels(r0).contentEquals(pixels(p0))}")
        }
        File(dir, "measurement.txt").writeText(report)
        println(report)
        assertTrue(GraphicsEnvironment.isHeadless(), "the measurement must run with no display")
        assertTrue(img.width > 300 && img.height > 1000, "capture has the gallery's size")
        assertTrue(pixels(r0).toSet().size > 16, "capture is not blank")
        assertEquals(pixels(r0).toList(), pixels(r1).toList(), "two runs on one machine give identical pixels")
    }
}
