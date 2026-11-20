package stapel.image.compose

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.ColorImage
import coil3.test.FakeImageLoaderEngine
import kotlinx.coroutines.CompletableDeferred
import stapel.image.Fit
import stapel.image.StapelImage
import stapel.tokens.ThemeMode
import stapel.ui.ASYNC_IMAGE_ERROR_TAG
import stapel.ui.ASYNC_IMAGE_PLACEHOLDER_TAG
import stapel.ui.StapelAsyncImage
import stapel.ui.StapelAvatar
import stapel.ui.StapelTheme
import stapel.ui.skin.AvatarSize
import stapel.ui.skin.FoundationSkin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val GREEN = 0xFF2E7D32.toInt()
private const val OK = "https://media.example/ok.png"
private const val MISSING = "https://media.example/missing.png"
private const val SLOW = "https://media.example/slow.png"

/** A Coil loader with no network: [OK] is a solid green image, [SLOW] never answers, anything else fails. */
private fun fakeLoader(): ImageLoader {
    val never = CompletableDeferred<ImageResult>()
    val engine = FakeImageLoaderEngine.Builder()
        .intercept(OK, ColorImage(GREEN, width = 200, height = 200))
        .intercept({ it == SLOW }, FakeImageLoaderEngine.OptionalInterceptor { never.await() })
        .default(Interceptor { chain -> ErrorResult(null, chain.request, IllegalStateException("not found")) })
        .build()
    return ImageLoader.Builder(PlatformContext.INSTANCE).components { add(engine) }.build()
}

@OptIn(ExperimentalTestApi::class)
class CoilStapelImagesTest {
    private fun ComposeUiTest.show(loader: ImageLoader?, content: @Composable () -> Unit) {
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, reduceMotion = true) {
                val column: @Composable () -> Unit = { androidx.compose.foundation.layout.Column { content() } }
                if (loader != null) ProvideStapelImages(loader, column) else column()
            }
        }
    }

    private fun ComposeUiTest.centre(tag: String): Color {
        val map = onNodeWithTag(tag).captureToImage().toPixelMap()
        return map[map.width / 2, map.height / 2]
    }

    private fun ComposeUiTest.awaitTag(tag: String) = waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun theAvatarDrawsTheLoadedImage() = runComposeUiTest {
        show(fakeLoader()) { StapelAvatar("Ada Lovelace", StapelImage.link(OK), size = AvatarSize.Large, testTag = "a") }
        waitUntil(timeoutMillis = 5_000) { centre("a") == Color(GREEN) }
        assertTrue(onAllNodesWithText("AL", useUnmergedTree = true).fetchSemanticsNodes().isEmpty(), "initials drawn over a loaded image")
    }

    @Test
    fun aFailingImageKeepsTheInitials() = runComposeUiTest {
        show(fakeLoader()) { StapelAvatar("Ada Lovelace", StapelImage.link(MISSING), testTag = "a") }
        waitForIdle()
        assertEquals(1, onAllNodesWithText("AL", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun withoutTheLoaderWiredTheAvatarFallsBackAndTheImageShowsItsErrorArm() = runComposeUiTest {
        show(loader = null) {
            StapelAvatar("Ada Lovelace", StapelImage.link(OK), testTag = "a")
            StapelAsyncImage(StapelImage.link(OK, aspect = 1.0), "Photo", Modifier.size(80.dp), testTag = "img")
        }
        waitForIdle()
        assertEquals(1, onAllNodesWithText("AL", useUnmergedTree = true).fetchSemanticsNodes().size)
        awaitTag("img$ASYNC_IMAGE_ERROR_TAG")
    }

    @Test
    fun theAsyncImageDrawsLoadedFailedAndLoadingArms() = runComposeUiTest {
        val preview = StapelImage(
            "cdn", SLOW, aspect = 1.0,
            previewB64 = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
        )
        show(fakeLoader()) {
            StapelAsyncImage(StapelImage.link(OK, aspect = 1.0), "Loaded", Modifier.size(80.dp), testTag = "ok")
            StapelAsyncImage(StapelImage.link(MISSING, aspect = 1.0), "Missing", Modifier.size(80.dp), testTag = "missing")
            StapelAsyncImage(preview, "Slow", Modifier.size(80.dp), fit = Fit.Cover, testTag = "slow")
        }
        waitUntil(timeoutMillis = 5_000) { centre("ok") == Color(GREEN) }
        awaitTag("missing$ASYNC_IMAGE_ERROR_TAG")
        awaitTag("slow$ASYNC_IMAGE_PLACEHOLDER_TAG")
        assertTrue(onAllNodesWithTag("ok$ASYNC_IMAGE_ERROR_TAG", useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
    }
}
