package stapel.showcase.stories.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import stapel.image.Fit
import stapel.image.ImageError
import stapel.image.ImageRequest
import stapel.image.PreviewKind
import stapel.image.StapelImage
import stapel.showcase.Knob
import stapel.showcase.Story
import stapel.showcase.StoryKind
import stapel.showcase.StoryState
import stapel.ui.LocalStapelImages
import stapel.ui.StapelAsyncImage
import stapel.ui.StapelAvatar
import stapel.ui.StapelImageState
import stapel.ui.StapelImages
import stapel.ui.StapelTheme
import stapel.ui.skin.AvatarSize

/** A 4×4 colour grid as an inline preview (what the CDN inlines as `preview_b64`). */
private const val PREVIEW = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAIAAAAmkwkpAAAAI0lEQVR4nGOI7n2vV2v0YYvNiYAKBjgLKMoAZwFFGeAsoCgAgKIYkZ4qiAIAAAAASUVORK5CYII="

/** The loader a story uses instead of the network: `story://ok…` is a brand-coloured image, `story://slow…` never loads, the rest fail. */
private class StoryImages(private val ok: Painter) : StapelImages {
    @Composable
    override fun painter(source: String): Painter? = if (source.startsWith("story://ok")) ok else null

    @Composable
    override fun load(request: ImageRequest): StapelImageState = when {
        request.url.startsWith("story://ok") -> StapelImageState.Ready(ok)
        request.url.startsWith("story://slow") -> StapelImageState.Loading
        else -> StapelImageState.Failed(ImageError.Http(404))
    }
}

/** What the image story's loader answers. */
internal enum class ImageArm { Loaded, Loading, Failed, NoLoader }

private val imageArm = Knob.enumOf("arm", "Loader answers", ImageArm.Loading)

private fun picture(arm: ImageArm, kind: PreviewKind = PreviewKind.Blur, aspect: Double = 4.0 / 3.0): StapelImage {
    val url = when (arm) {
        ImageArm.Loaded -> "story://ok/photo.webp"
        ImageArm.Loading -> "story://slow/photo.webp"
        ImageArm.Failed, ImageArm.NoLoader -> "story://gone/photo.webp"
    }
    return StapelImage("cdn", url, aspect = aspect, previewB64 = PREVIEW, previewKind = kind)
}

/** Images through the seam: a photo, a video poster, an avatar — loaded, loading over the inline preview, failed, or no loader wired. */
internal val imagesStory = Story(
    "async-image", "Images", "Composites", StoryKind.Composite,
    description = "StapelAsyncImage and StapelAvatar(image = …) through LocalStapelImages: the ladder variant for the slot, " +
        "the inline preview while loading (blurred for a photo, sharp for a poster), the error arm, and the avatar's initials as its fallback.",
    knobs = listOf(imageArm),
    states = listOf(
        StoryState("default", "Loading over the preview"),
        StoryState("loaded", "Loaded", mapOf("arm" to ImageArm.Loaded)),
        StoryState("failed", "Failed", mapOf("arm" to ImageArm.Failed)),
        StoryState("no-loader", "No loader wired", mapOf("arm" to ImageArm.NoLoader)),
    ),
    snippet = "StapelAsyncImage(listing.photo, contentDescription = listing.title, Modifier.fillMaxWidth())\n" +
        "StapelAvatar(profile.name, image = profile.avatar, size = AvatarSize.Large)",
) {
    val arm = value(imageArm)
    val ok = ColorPainter(StapelTheme.colors.brand)
    val body: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StapelAsyncImage(picture(arm), "Bike, side view", Modifier.width(160.dp))
                StapelAsyncImage(picture(arm, PreviewKind.Poster, 16.0 / 9.0), "Test ride video", Modifier.width(160.dp), fit = Fit.Cover)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StapelAvatar("Ada Lovelace", picture(arm, aspect = 1.0), size = AvatarSize.Large)
                StapelAvatar("Grace Hopper", picture(arm, aspect = 1.0))
            }
        }
    }
    if (arm == ImageArm.NoLoader) body() else CompositionLocalProvider(LocalStapelImages provides StoryImages(ok), content = body)
}
