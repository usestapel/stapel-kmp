package stapel.image

import kotlin.io.encoding.Base64

/**
 * One image load as a loader receives it: what to fetch and what to show meanwhile. Built from a
 * [StapelImage] for a measured slot by [StapelImage.requestFor], or by hand for a plain URL.
 *
 * @property url what to fetch (`https://…`, or another scheme the app's loader understands).
 * @property widthPx the slot width in pixels the variant was chosen for, when measured.
 * @property heightPx the slot height in pixels, when measured.
 * @property placeholder what to show until the image is ready.
 * @property variant the ladder variant [url] belongs to, or null (no ladder / a plain URL).
 */
public data class ImageRequest(
    val url: String,
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val placeholder: ImagePlaceholder = ImagePlaceholder.None,
    val variant: VariantMeta? = null,
)

/** What a slot shows until its image is ready. */
public sealed interface ImagePlaceholder {
    /** Nothing (the slot's own fallback: initials, a glyph, a sunken box). */
    public data object None : ImagePlaceholder

    /**
     * The inline preview: encoded image bytes decoded locally (no request).
     *
     * @property bytes the encoded image (PNG, JPEG, WebP).
     * @property mime the MIME type from the data URI.
     * @property kind how to draw it: blurred up, sharp, or whole.
     */
    public class Preview(public val bytes: ByteArray, public val mime: String, public val kind: PreviewKind) : ImagePlaceholder {
        /** Equality by content. */
        override fun equals(other: Any?): Boolean =
            other is Preview && bytes.contentEquals(other.bytes) && mime == other.mime && kind == other.kind

        /** Consistent with [equals]. */
        override fun hashCode(): Int = (bytes.contentHashCode() * 31 + mime.hashCode()) * 31 + kind.hashCode()

        /** Debug form. */
        override fun toString(): String = "Preview($mime, ${bytes.size} bytes, $kind)"
    }
}

/** Why an image did not load. */
public sealed interface ImageError {
    /**
     * The server answered with a non-success [status].
     *
     * @property status the HTTP status.
     */
    public data class Http(val status: Int) : ImageError

    /**
     * The request did not complete (offline, timeout, TLS, DNS).
     *
     * @property message what the engine said.
     */
    public data class Network(val message: String) : ImageError

    /** The bytes arrived but are not an image this platform decodes. */
    public data object Decode : ImageError

    /**
     * The request was not made: an unsupported scheme, a medium that is not a picture with no poster.
     *
     * @property reason why.
     */
    public data class Refused(val reason: String) : ImageError

    /** No image loader is wired in this app (`stapel-image-compose` is not installed). */
    public data object NoLoader : ImageError
}

/**
 * Decodes an inline preview: only `data:image/<type>;base64,<payload>` is accepted (the value is
 * host-built as often as server-sent and is never fetched); anything else, or a payload that is not
 * valid base64, is null.
 */
public fun decodePreview(dataUri: String, kind: PreviewKind = PreviewKind.Blur): ImagePlaceholder.Preview? {
    if (!dataUri.startsWith("data:image/")) return null
    val comma = dataUri.indexOf(',')
    if (comma < 0) return null
    val header = dataUri.substring("data:".length, comma)
    val parts = header.split(';')
    if (parts.size < 2 || parts.last() != "base64") return null
    val mime = parts.first()
    if (!Regex("image/[a-z0-9.+-]+").matches(mime)) return null
    val bytes = try {
        Base64.decode(dataUri.substring(comma + 1))
    } catch (_: IllegalArgumentException) {
        return null
    }
    return if (bytes.isEmpty()) null else ImagePlaceholder.Preview(bytes, mime, kind)
}

/** The placeholder of this image: its inline preview when it has a valid one, else [ImagePlaceholder.None]. */
public fun StapelImage.placeholder(): ImagePlaceholder =
    previewB64?.let { decodePreview(it, previewKind ?: PreviewKind.Blur) } ?: ImagePlaceholder.None

/**
 * The request for a measured slot: a picture picks its ladder variant (tier math above; [url] when
 * there is no ladder), anything that is not a picture loads [StapelImage.posterUrl] — never the
 * medium itself — and has nothing to load without one (null). A zero-sided slot loads nothing (null).
 */
public fun StapelImage.requestFor(slotWidthDp: Float, slotHeightDp: Float, density: Float, fit: Fit): ImageRequest? {
    if (slotWidthDp <= 0f || slotHeightDp <= 0f) return null
    val widthPx = (slotWidthDp * density).toInt()
    val heightPx = (slotHeightDp * density).toInt()
    if (!isPicture) {
        val poster = posterUrl ?: return null
        return ImageRequest(poster, widthPx, heightPx, placeholder())
    }
    val imgAspect = reservedAspect ?: (slotWidthDp.toDouble() / slotHeightDp.toDouble())
    val variant = chooseVariant(slotWidthDp, slotHeightDp, density, imgAspect, fit, this)
    return ImageRequest(variant?.url ?: url, widthPx, heightPx, placeholder(), variant)
}

/**
 * Upgrade-only: the request to keep showing when [next] is computed for a resized slot while
 * [current] is on screen — a variant of a lower rank never replaces a higher one (a shrinking slot
 * keeps the sharper file), so [current] wins unless [next] ranks above it.
 */
public fun upgradeOnly(current: ImageRequest?, next: ImageRequest?): ImageRequest? {
    if (current == null || next == null) return next ?: current
    val c = current.variant ?: return next
    val n = next.variant ?: return next
    return if (n.rank > c.rank) next else current
}
