package stapel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.decodeToImageBitmap
import stapel.image.Fit
import stapel.image.ImageError
import stapel.image.ImagePlaceholder
import stapel.image.ImageRequest
import stapel.image.PreviewKind
import stapel.image.StapelImage
import stapel.image.placeholder
import stapel.image.requestFor
import stapel.image.upgradeOnly
import stapel.ui.skin.AvatarSize
import stapel.ui.skin.BadgePlacement
import stapel.ui.skin.BadgeSpec
import kotlinx.coroutines.CancellationException

/** Suffix of the test tag of a [StapelAsyncImage]'s placeholder. */
public const val ASYNC_IMAGE_PLACEHOLDER_TAG: String = ".placeholder"

/** Suffix of the test tag of a [StapelAsyncImage]'s error arm. */
public const val ASYNC_IMAGE_ERROR_TAG: String = ".error"

/**
 * A backend image ([StapelImage]) in a slot, through the app's loader ([LocalStapelImages]). The
 * slot is measured and the ladder variant chosen from its pixel size (`stapel-image`'s tier math,
 * the web's rule); once a variant is on screen a smaller one never replaces it (upgrade-only); a
 * zero-sided slot loads nothing. Until the image is ready the inline preview is drawn (blurred up for
 * [PreviewKind.Blur], sharp for a poster, whole for a waveform), else a sunken box; a failure (or no
 * loader) draws [error], by default a sunken box with an alert glyph and the description. A medium
 * that is not a picture loads its poster, never itself.
 *
 * @param contentDescription the accessible name; null for a decorative image.
 * @param aspectRatio the slot's aspect; default the image's ([StapelImage.reservedAspect]), else unconstrained.
 */
@Composable
public fun StapelAsyncImage(
    image: StapelImage,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    fit: Fit = Fit.Cover,
    aspectRatio: Float? = null,
    testTag: String? = null,
    error: (@Composable (ImageError) -> Unit)? = null,
) {
    var size by remember(image) { mutableStateOf(IntSize.Zero) }
    var shown by remember(image) { mutableStateOf<ImageRequest?>(null) }
    val density = LocalDensity.current.density
    val next = if (size.width > 0 && size.height > 0) image.requestFor(size.width / density, size.height / density, density, fit) else null
    val request = upgradeOnly(shown, next)
    SideEffect { if (request != shown) shown = request }
    val ratio = aspectRatio ?: image.reservedAspect?.toFloat()
    Box(
        modifier
            .then(if (ratio != null && ratio > 0f) Modifier.aspectRatio(ratio) else Modifier)
            .onSizeChanged { size = it }
            .clipToBounds()
            .semantics {
                if (contentDescription != null) {
                    this.contentDescription = contentDescription
                    role = Role.Image
                }
            }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val state = when {
            request != null -> LocalStapelImages.current.load(request)
            size.width > 0 && size.height > 0 && !image.isPicture -> StapelImageState.Failed(ImageError.Refused("not a picture and no poster"))
            else -> StapelImageState.Loading
        }
        val scale = if (fit == Fit.Cover) ContentScale.Crop else ContentScale.Fit
        when (state) {
            is StapelImageState.Ready -> Image(state.painter, null, Modifier.fillMaxSize(), contentScale = scale)
            StapelImageState.Loading -> Placeholder(request?.placeholder ?: image.placeholder(), fit, testTag)
            is StapelImageState.Failed -> Box(
                Modifier.fillMaxSize().then(if (testTag != null) Modifier.testTag(testTag + ASYNC_IMAGE_ERROR_TAG) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (error != null) error(state.error) else DefaultImageError(contentDescription)
            }
        }
    }
}

@Composable
private fun Placeholder(placeholder: ImagePlaceholder, fit: Fit, testTag: String?) {
    val tag = if (testTag != null) Modifier.testTag(testTag + ASYNC_IMAGE_PLACEHOLDER_TAG) else Modifier
    val bitmap = (placeholder as? ImagePlaceholder.Preview)?.let { remember(it) { decodeOrNull(it.bytes) } }
    if (placeholder is ImagePlaceholder.Preview && bitmap != null) {
        val kind = placeholder.kind
        Image(
            bitmap,
            null,
            Modifier.fillMaxSize().then(if (kind == PreviewKind.Blur) Modifier.blur(12.dp) else Modifier).then(tag),
            contentScale = if (kind == PreviewKind.Waveform || fit == Fit.Contain) ContentScale.Fit else ContentScale.Crop,
            filterQuality = if (kind == PreviewKind.Blur) FilterQuality.Low else FilterQuality.Medium,
        )
    } else {
        Box(Modifier.fillMaxSize().background(StapelTheme.colors.surfaceSunken).then(tag))
    }
}

private fun decodeOrNull(bytes: ByteArray): ImageBitmap? = try {
    bytes.decodeToImageBitmap()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}

@Composable
private fun DefaultImageError(description: String?) {
    Column(
        Modifier.fillMaxSize().background(StapelTheme.colors.surfaceSunken).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StapelIconView(StapelIconIds.AlertCircle, null, size = 24.dp, tint = StapelTheme.colors.textSubtle)
        if (description != null) {
            StapelText(description, style = StapelTheme.type.caption, color = StapelTheme.colors.textMuted, maxLines = 2, textAlign = TextAlign.Center)
        }
    }
}

/**
 * An avatar showing a backend image: the variant for the avatar's pixel size (square, cover) is
 * handed to the skin's Avatar slot, which asks [LocalStapelImages] for it and keeps its content
 * ladder (image → initials → glyph) while the image is loading, failed, or no loader is wired.
 */
@Composable
public fun StapelAvatar(
    contentDescription: String,
    image: StapelImage?,
    modifier: Modifier = Modifier,
    initials: String? = avatarInitials(contentDescription),
    size: AvatarSize = AvatarSize.Medium,
    icon: StapelIcon? = null,
    badge: BadgeSpec? = null,
    badgePlacement: BadgePlacement = BadgePlacement.BottomEnd,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
) {
    val density = LocalDensity.current.density
    val source = image?.requestFor(size.dp.toFloat(), size.dp.toFloat(), density, Fit.Cover)?.url
    StapelAvatar(contentDescription, modifier, initials, size, icon, source, badge, badgePlacement, onClick, testTag)
}
