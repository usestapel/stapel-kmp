package stapel.profiles

import kotlinx.coroutines.test.TestScope
import stapel.cache.ManagedStapelCache
import stapel.cache.StapelCache
import stapel.cache.StapelCacheConfig
import stapel.mvi.PresenterContext
import stapel.profiles.api.ProfilesApiClient
import stapel.profiles.api.model.ContactListResponse
import stapel.profiles.api.model.ContactResponse
import stapel.profiles.api.model.LanguageResponse
import stapel.profiles.api.model.ProfileContactFlags
import stapel.profiles.api.model.ProfilePublicResponse
import stapel.profiles.api.model.ProfileResponse
import stapel.profiles.avatar.AvatarUploader
import stapel.profiles.error.ProfilesError
import stapel.profiles.model.AvatarRef
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.result.StapelResult
import stapel.testing.FakeAnswer
import stapel.testing.FakeApiTransport
import stapel.testing.virtualTimeClock
import stapel.wire.FilePart
import stapel.wire.WireJson

/** Operation ids used by the tests. */
internal object Ops {
    const val ME = "get_my_profile"
    const val UPDATE = "update_my_profile"
    const val PROFILE = "get_profile"
    const val BATCH = "batch_profiles"
    const val FOLLOW = "follow_user"
    const val FOLLOWERS = "get_my_followers"
    const val LANGUAGES = "list_languages"
    const val CONTACTS = "list_my_contacts"
    const val ADD_CONTACT = "add_my_contact"
    const val REQUEST_CODE = "request_contact_verification"
    const val CONFIRM_CODE = "confirm_contact_verification"
}

internal fun profile(
    userId: String = "u1",
    name: String = "Ada",
    emailMessages: Boolean = true,
    pushSystem: Boolean = true,
    avatar: String? = null,
    avatarSource: String = "file",
    language: String? = null,
): ProfileResponse = ProfileResponse(
    userId = userId, displayName = name, theme = "system", avatarSource = avatarSource, avatar = avatar, avatarImage = null,
    locationId = null, locationDisplayNameNarrow = "", locationDisplayNameBroad = "LU", appLanguage = language?.let { LanguageResponse(it, it.uppercase(), null) },
    understands = emptyList(), useDeviceLanguage = language == null, autoDetectedLanguage = "", autoTranslateContent = false,
    emailMessages = emailMessages, emailSystem = true, pushMessages = true, pushSystem = pushSystem, essentialCookiesAccepted = false,
    initialSetupPassed = true, followersCount = 2, followingCount = 3, createdAt = "2026-01-02T03:04:05Z", updatedAt = "2026-01-02T03:04:05Z",
)

internal fun publicProfile(userId: String, name: String = "P-$userId", status: String? = "neutral"): ProfilePublicResponse = ProfilePublicResponse(
    userId = userId, displayName = name, avatarSource = "file", avatar = null, avatarImage = null, locationId = null,
    locationDisplayNameNarrow = "", locationDisplayNameBroad = "", followersCount = 1, followingCount = 0,
    relationshipStatus = status, sellerType = null, createdAt = null, contacts = ProfileContactFlags(phone = false),
)

internal fun contact(id: Int, verified: Boolean = false): ContactResponse = ContactResponse(
    id = id, kind = "phone", value = "+15550100$id", label = "", policy = "members", enabled = true, verified = verified,
    verifiedAt = null, revealCount = 0, createdAt = "2026-01-02T03:04:05Z",
)

internal fun contacts(vararg items: ContactResponse): ContactListResponse = ContactListResponse(items.toList(), listOf("members", "verified", "nobody"))

internal fun refusal(code: String, status: Int, params: Map<String, String> = emptyMap()): FakeAnswer =
    FakeAnswer.Refusal(ErrorEnvelope(ErrorCode(code), status, ErrorParams(params), serverText = code))

/** An uploader that records what it was given and answers [answer]. */
internal class RecordingUploader(var answer: StapelResult<AvatarRef, ProfilesError> = StapelResult.Ok(AvatarRef.cdn("avatar/abc"))) : AvatarUploader {
    val files = mutableListOf<FilePart>()

    override suspend fun upload(file: FilePart, onProgress: ((sent: Long, total: Long?) -> Unit)?): StapelResult<AvatarRef, ProfilesError> {
        files += file
        onProgress?.invoke(5, 10)
        return answer
    }
}

/** The library over a scripted transport and a memory cache whose partition is the viewer's. */
internal class Fixture(scope: TestScope, viewer: String? = "u1", val uploader: RecordingUploader = RecordingUploader()) {
    val transport = FakeApiTransport()
    val cache: ManagedStapelCache = StapelCache(StapelCacheConfig(json = WireJson, scope = scope.backgroundScope, clock = scope.virtualTimeClock()))
    val profiles = StapelProfiles(ProfilesApiClient(transport), cache, uploader, viewer = { viewer })
    val context = PresenterContext(scope.backgroundScope)

    suspend fun signIn(account: String = "u1") = cache.setAccount(account)

    fun count(operationId: String): Int = transport.calls.count { it.first == operationId }

    /** The JSON the last `PATCH /me` sent. */
    fun patchBody(): String {
        val call = transport.calls.last { it.first == Ops.UPDATE }.second
        @Suppress("UNCHECKED_CAST")
        val body = call.body as stapel.profiles.api.model.PatchedProfileUpdateRequest
        return WireJson.encodeToString(stapel.profiles.api.model.PatchedProfileUpdateRequest.serializer(), body)
    }
}

internal fun picture(): FilePart = FilePart("a.png", "image/png", 4L) { kotlinx.io.Buffer().apply { write(byteArrayOf(1, 2, 3, 4)) } }
