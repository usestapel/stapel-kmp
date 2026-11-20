package stapel.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.painter.Painter
import stapel.image.ImageError
import stapel.image.ImageRequest

/** Where one image load stands, as a slot sees it. */
@Stable
public sealed interface StapelImageState {
    /** Not ready yet: draw the placeholder / the fallback. */
    public data object Loading : StapelImageState

    /**
     * Ready to draw.
     *
     * @property painter the image.
     */
    public class Ready(public val painter: Painter) : StapelImageState

    /**
     * Will not load: draw the error arm / the fallback.
     *
     * @property error why.
     */
    public data class Failed(val error: ImageError) : StapelImageState
}

/**
 * The image seam of the UI: turns an image source or an [ImageRequest] into something to draw.
 * `stapel-ui` loads nothing itself: the app provides a loader through [LocalStapelImages]
 * (`stapel-image-compose`'s Coil loader, or a test fake). A slot draws its fallback (initials, a glyph,
 * the placeholder) while the answer is not ready — loading, failed, or no loader at all. Brick UI
 * never sees the loader's types.
 */
public fun interface StapelImages {
    /** The painter for [source] (a URL, a content id), or null while it is not available. */
    @Composable
    public fun painter(source: String): Painter?

    /** The state of [request]. The default asks [painter] for the request's URL (null = loading). */
    @Composable
    public fun load(request: ImageRequest): StapelImageState =
        painter(request.url)?.let { StapelImageState.Ready(it) } ?: StapelImageState.Loading
}

/** The loader of an app that wired none: nothing ever loads, every slot shows its fallback. */
public object NoStapelImages : StapelImages {
    @Composable
    override fun painter(source: String): Painter? = null

    @Composable
    override fun load(request: ImageRequest): StapelImageState = StapelImageState.Failed(ImageError.NoLoader)
}

/** The image loader of the subtree; the default, [NoStapelImages], draws nothing (every slot shows its fallback). */
public val LocalStapelImages: ProvidableCompositionLocal<StapelImages> = staticCompositionLocalOf { NoStapelImages }

/**
 * The initials of [name] for an avatar: the first letter of the first two words, upper-cased
 * ("Ada Lovelace" → "AL", "ada" → "A"); empty for a blank name.
 */
public fun avatarInitials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }
