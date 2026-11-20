package stapel.image

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One generated variant file of an image (the CDN's variant ladder).
 *
 * @property tier a numeric px value as its decimal string (`"320"`) or the literal `"original"`.
 * @property branch `w` / `h` for a preview-class variant (resized so width / height == tier); `null`
 *   for a thumbnail-class (min-side) variant and for `"original"`.
 * @property url where the file is.
 * @property width actual pixel width of the file, when known.
 * @property height actual pixel height of the file, when known.
 */
@Serializable
public data class VariantMeta(
    val tier: String,
    val branch: Branch? = null,
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
) {
    /** The numeric px value of [tier], or null for `"original"` (or anything that is not a number). */
    val numericTier: Int? get() = if (tier == ORIGINAL_TIER) null else tier.toIntOrNull()
}

/** The tier name of the unresized file. */
public const val ORIGINAL_TIER: String = "original"

/** Which side a preview-class variant was resized by. */
@Serializable
public enum class Branch {
    /** Width equals the tier. */
    @SerialName("w")
    W,

    /** Height equals the tier. */
    @SerialName("h")
    H,
}

/** What the inline `preview_b64` placeholder is — known even while the preview itself is still null. */
@Serializable
public enum class PreviewKind {
    /** A micro thumbnail of a still image: drawn blurred up. */
    @SerialName("blur")
    Blur,

    /** A frame of a video: a real, sharp picture, never blurred. */
    @SerialName("poster")
    Poster,

    /** An audio amplitude strip: drawn whole (contain), never cropped or blurred. */
    @SerialName("waveform")
    Waveform,
}

/**
 * The aspect a slot is reserved at when the snapshot names the preview kind but carries no geometry:
 * a still photo can be any shape (null), a video frame is usually 16:9, a waveform strip 4:1.
 */
public fun PreviewKind.defaultAspect(): Double? = when (this) {
    PreviewKind.Blur -> null
    PreviewKind.Poster -> 16.0 / 9.0
    PreviewKind.Waveform -> 4.0
}

/**
 * A source-agnostic media descriptor (the backend's `stapel_core.media.StapelImage`): a CDN ladder
 * in [variants], or `[]` for an external link / unprocessed file, in which case [url] + [aspect] +
 * [previewB64] are all there is.
 *
 * @property source `cdn`, `file` or `link` (open on the wire).
 * @property url the display URL when there is no ladder; always present.
 * @property mime the MIME type, when known.
 * @property width pixel width of the original, when known.
 * @property height pixel height of the original, when known.
 * @property aspect width / height, when known.
 * @property square width equals height (any branch serves either axis).
 * @property previewB64 the inline placeholder, a `data:image/…;base64,…` URI; anything else is refused.
 * @property variants the ladder; empty without one.
 * @property kind the medium (`image`, `gif`, `video`, `audio`, `file`, or a host's own); only an
 *   image-like kind loads [url] as a picture, any other loads [posterUrl].
 * @property previewKind what [previewB64] is.
 * @property durationMs duration of a time-based medium; null = unmeasured, 0 = empty.
 * @property posterUrl a full-size still of a video.
 * @property metaStatus `ok`, `partial` or `missing`.
 * @property metaReason why [metaStatus] is not `ok`.
 */
@Serializable
public data class StapelImage(
    val source: String,
    val url: String,
    val mime: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val aspect: Double? = null,
    val square: Boolean = false,
    @SerialName("preview_b64") val previewB64: String? = null,
    val variants: List<VariantMeta> = emptyList(),
    val kind: String? = null,
    @SerialName("preview_kind") val previewKind: PreviewKind? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("meta_status") val metaStatus: String? = null,
    @SerialName("meta_reason") val metaReason: String? = null,
) {
    /** A picture can be loaded from [url] (no kind, `image` or `gif`); otherwise only [posterUrl] may be. */
    val isPicture: Boolean get() = kind == null || kind == "image" || kind == "gif"

    /**
     * The aspect to reserve: [aspect], else width / height, else the [previewKind]'s default shape,
     * else null (unconstrained).
     */
    val reservedAspect: Double?
        get() = aspect
            ?: (if (width != null && height != null && width > 0 && height > 0) width.toDouble() / height else null)
            ?: previewKind?.defaultAspect()

    /** Companion with a convenience constructor. */
    public companion object {
        /** An external picture at [url] with no ladder and no preview (a `link`). */
        public fun link(url: String, aspect: Double? = null): StapelImage = StapelImage(source = "link", url = url, aspect = aspect)
    }
}
