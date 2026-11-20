package stapel.profiles.avatar

import stapel.profiles.error.ProfilesError
import stapel.profiles.error.ProfilesErrorReason
import stapel.profiles.model.AvatarRef
import stapel.result.DomainError
import stapel.result.StapelResult
import stapel.wire.FilePart
import stapel.wire.MediaPurpose
import stapel.wire.MediaUploader

/**
 * Turns a picked picture into a stored avatar reference. stapel-profiles only stores the reference
 * (`avatar` + `avatar_source`); producing one from a file is the media service's job, so the profiles
 * library takes it through this seam and never depends on another brick.
 *
 * An implementation returns the reference together with its source ([AvatarRef]); the two are stored
 * in one `PATCH` by `MyProfileRepository.setAvatar`, so a reference never exists without its tag.
 */
public fun interface AvatarUploader {
    /**
     * Uploads [file] and answers the reference to store.
     *
     * @param onProgress upload progress `(sent, total)`, when the transport reports it.
     */
    public suspend fun upload(file: FilePart, onProgress: ((sent: Long, total: Long?) -> Unit)?): StapelResult<AvatarRef, ProfilesError>

    /** The uploader of an app that offers no avatar upload: every call is [ProfilesErrorReason.Unsupported]. */
    public object None : AvatarUploader {
        override suspend fun upload(file: FilePart, onProgress: ((sent: Long, total: Long?) -> Unit)?): StapelResult<AvatarRef, ProfilesError> =
            StapelResult.Err(ProfilesError.client(ProfilesErrorReason.Unsupported))
    }
}

/**
 * The avatar upload through the app's [MediaUploader] (stapel-cdn's when the app installs
 * `cdnModule()`): the file goes up for [MediaPurpose.Avatar] and the stored reference comes back with
 * its source. A refused file (400, 413, 415, 422) is [ProfilesErrorReason.InvalidAvatar]; anything else
 * is classified by [ProfilesError.of].
 *
 * @param media the app's media uploader.
 */
public class MediaAvatarUploader(private val media: MediaUploader) : AvatarUploader {
    /** Uploads [file] as [MediaPurpose.Avatar]; the stored reference comes back with its source. */
    override suspend fun upload(file: FilePart, onProgress: ((sent: Long, total: Long?) -> Unit)?): StapelResult<AvatarRef, ProfilesError> =
        when (val stored = media.upload(MediaPurpose.Avatar, file, onProgress)) {
            is StapelResult.Ok -> StapelResult.Ok(AvatarRef(stored.value.ref, stored.value.source))
            is StapelResult.Err -> StapelResult.Err(
                if (stored.error is DomainError && stored.error.status in REFUSED_FILE) {
                    ProfilesError(ProfilesErrorReason.InvalidAvatar, stored.error)
                } else {
                    ProfilesError.of(stored.error)
                },
            )
        }

    private companion object {
        val REFUSED_FILE = setOf(400, 413, 415, 422)
    }
}
