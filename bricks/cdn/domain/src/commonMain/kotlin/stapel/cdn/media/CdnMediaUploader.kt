package stapel.cdn.media

import stapel.cdn.api.CdnApi
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.wire.FilePart
import stapel.wire.MediaPurpose
import stapel.wire.MediaUploader
import stapel.wire.StoredMedia

/**
 * stapel-cdn's [MediaUploader] over the generated [CdnApi]: [MediaPurpose.Avatar] goes to the avatar
 * upload (`POST /cdn/api/v1/upload/avatar/`); the stored reference is the answer's content `prefix`,
 * tagged with source [SOURCE]. Upload progress is the transport's (`OperationCall.onProgress`).
 * Any other purpose is refused before anything is sent ([IllegalArgumentException]).
 */
public class CdnMediaUploader(private val api: CdnApi) : MediaUploader {
    /** Uploads [file] to the endpoint of [purpose]; the answer's `image.prefix` comes back as the reference. */
    override suspend fun upload(
        purpose: MediaPurpose,
        file: FilePart,
        onProgress: ((sent: Long, total: Long?) -> Unit)?,
    ): StapelResult<StoredMedia, StapelError> {
        val answer = when (purpose) {
            MediaPurpose.Avatar -> api.uploadAvatar(file, onProgress)
            else -> throw IllegalArgumentException("stapel-cdn has no upload for purpose '${purpose.value}'")
        }
        return when (answer) {
            is StapelResult.Ok -> StapelResult.Ok(StoredMedia(answer.value.image.prefix, SOURCE))
            is StapelResult.Err -> answer
        }
    }

    /** Constants. */
    public companion object {
        /** The `source` tag of a stapel-cdn reference. */
        public const val SOURCE: String = "cdn"
    }
}
