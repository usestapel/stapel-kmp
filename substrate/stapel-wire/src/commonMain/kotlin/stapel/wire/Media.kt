package stapel.wire

import stapel.result.StapelError
import stapel.result.StapelResult
import kotlin.jvm.JvmInline

/**
 * The seam between a brick that keeps a reference to an uploaded file (the profiles brick's avatar) and
 * the media service that stores the file (stapel-cdn). A brick never depends on another brick: the
 * media library binds an implementation (`stapel.cdn.di.cdnModule()`), the consuming brick asks for
 * one, the app installs both.
 */
public fun interface MediaUploader {
    /**
     * Stores [file] for [purpose] and answers the reference to keep.
     *
     * @param onProgress upload progress `(sent, total)` in bytes, as the transport writes the body.
     * @return the stored reference, or the media service's error (a refused file is a 4xx
     *   [stapel.result.DomainError]; no answer is a transport failure).
     */
    public suspend fun upload(
        purpose: MediaPurpose,
        file: FilePart,
        onProgress: ((sent: Long, total: Long?) -> Unit)?,
    ): StapelResult<StoredMedia, StapelError>
}

/** What an upload is for; the media service picks its endpoint and processing by it. */
@JvmInline
public value class MediaPurpose(
    /** The purpose name. */
    public val value: String,
) {
    /** Known purposes. */
    public companion object {
        /** A person's avatar (square crops, the avatar variant ladder). */
        public val Avatar: MediaPurpose = MediaPurpose("avatar")
    }
}

/**
 * A stored file as another service keeps it.
 *
 * @property ref the reference to store (stapel-cdn: the content prefix, `avatar/<sha256>`).
 * @property source the media service that issued [ref] (`cdn`); stored next to it.
 */
public data class StoredMedia(val ref: String, val source: String)
