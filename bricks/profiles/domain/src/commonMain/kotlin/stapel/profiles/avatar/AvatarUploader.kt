package stapel.profiles.avatar

import kotlinx.serialization.Serializable
import stapel.profiles.error.ProfilesError
import stapel.profiles.error.ProfilesErrorReason
import stapel.profiles.model.AvatarRef
import stapel.result.ErrorEnvelope
import stapel.result.ServerRefusal
import stapel.result.StapelResult
import stapel.result.TransportFailure
import stapel.wire.ApiTransport
import stapel.wire.AuthRequirement
import stapel.wire.BodyEncoding
import stapel.wire.ErrorDecoder
import stapel.wire.FilePart
import stapel.wire.FormPart
import stapel.wire.OperationCall
import stapel.wire.OperationKind
import stapel.wire.OperationSpec
import stapel.wire.ResponseKind

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
 * The stapel-cdn avatar upload (`POST /cdn/api/v1/upload/avatar/`, multipart field `file`) over the
 * app's authenticated [ApiTransport]; the request goes to the endpoint the environment gives module
 * `cdn` (its own, or the default origin).
 *
 * This is the one cross-service call of the profiles library and it is written by hand: stapel-kmp has
 * no stapel-cdn client yet. When one exists, an app passes an uploader built on it instead and this
 * class is deleted; until then it reads only `image.prefix` of the answer, the reference the profile
 * stores.
 *
 * @param transport the authenticated transport (the upload needs the session).
 * @param path the upload path; the stapel-cdn default unless a deployment mounts it elsewhere.
 */
public class CdnAvatarUploader(
    private val transport: ApiTransport,
    private val path: String = DEFAULT_PATH,
) : AvatarUploader {
    override suspend fun upload(file: FilePart, onProgress: ((sent: Long, total: Long?) -> Unit)?): StapelResult<AvatarRef, ProfilesError> {
        val spec = OperationSpec<Unit, UploadAnswer, ProfilesError>(
            id = OPERATION_ID,
            module = "cdn",
            method = "POST",
            path = path,
            kind = OperationKind.Mutation,
            auth = AuthRequirement.Required,
            bodyEncoding = BodyEncoding.Multipart,
            body = null,
            responseKind = ResponseKind.Json,
            success = mapOf(200 to UploadAnswer.serializer(), 201 to UploadAnswer.serializer()),
            errors = Decoder,
        )
        val call = OperationCall<Unit>(parts = listOf(FormPart.File(FIELD, file)), onProgress = onProgress)
        return when (val r = transport.execute(spec, call)) {
            is StapelResult.Ok -> StapelResult.Ok(AvatarRef.cdn(r.value.image.prefix))
            is StapelResult.Err -> r
        }
    }

    /** Constants of the stapel-cdn upload. */
    public companion object {
        /** Where stapel-cdn mounts the avatar upload. */
        public const val DEFAULT_PATH: String = "/cdn/api/v1/upload/avatar/"

        /** The multipart field carrying the file. */
        public const val FIELD: String = "file"

        /** The operation id this call reports to the transport (logs, fakes). */
        public const val OPERATION_ID: String = "cdn_upload_avatar"
    }

    @Serializable
    internal class UploadAnswer(val image: Image) {
        @Serializable
        internal class Image(val prefix: String)
    }

    private object Decoder : ErrorDecoder<ProfilesError> {
        override fun domain(envelope: ErrorEnvelope): ProfilesError {
            val refusal = ServerRefusal(envelope)
            return if (envelope.status in REFUSED_FILE) ProfilesError(ProfilesErrorReason.InvalidAvatar, refusal) else ProfilesError.of(refusal)
        }

        override fun transport(failure: TransportFailure): ProfilesError = ProfilesError.of(failure)

        private val REFUSED_FILE = setOf(400, 413, 415, 422)
    }
}
