package stapel.profiles.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import stapel.cache.QuerySpec
import stapel.cache.StapelCache
import stapel.profiles.api.ProfilesApi
import stapel.profiles.api.ProfilesInvalidation
import stapel.profiles.api.ProfilesKeys
import stapel.profiles.api.ProfilesOperations
import stapel.profiles.api.model.ContactCreateRequest
import stapel.profiles.api.model.ContactListResponse
import stapel.profiles.api.model.ContactVerifyConfirmRequest
import stapel.profiles.api.model.FollowersResponse
import stapel.profiles.api.model.FollowingResponse
import stapel.profiles.api.model.LanguageResponse
import stapel.profiles.api.model.PatchedContactUpdateRequest
import stapel.profiles.api.model.ProfileBatchRequest
import stapel.profiles.api.model.ProfilePublic
import stapel.profiles.api.model.ProfilePublicResponse
import stapel.profiles.api.model.ProfileResponse
import stapel.profiles.avatar.AvatarUploader
import stapel.profiles.error.ProfilesError
import stapel.profiles.internal.planned
import stapel.profiles.internal.toDomain
import stapel.profiles.internal.toRequest
import stapel.profiles.model.AvatarRef
import stapel.profiles.model.ConnectionKind
import stapel.profiles.model.Contact
import stapel.profiles.model.ContactPolicy
import stapel.profiles.model.Contacts
import stapel.profiles.model.Language
import stapel.profiles.model.MyProfile
import stapel.profiles.model.Person
import stapel.profiles.model.ProfileUpdate
import stapel.profiles.model.PublicProfile
import stapel.profiles.model.RelationshipStatus
import stapel.result.StapelError
import stapel.result.StapelResult
import stapel.result.state.Resource
import stapel.result.state.map
import stapel.wire.FilePart
import stapel.wire.Maybe

/**
 * The signed-in person's own profile (`GET`/`PATCH /me`): user-scoped (the account's cache partition,
 * wiped on every session end), stale-while-revalidate. A successful update writes the server's answer
 * into the cache at once, so every observer sees it without a refetch.
 *
 * @param api the generated client on the authenticated transport.
 * @param cache the app's cache.
 * @param uploader turns a picked picture into an avatar reference.
 */
public class MyProfileRepository(
    private val api: ProfilesApi,
    private val cache: StapelCache,
    private val uploader: AvatarUploader,
) {
    private val spec: QuerySpec<ProfileResponse> = planned(ProfilesOperations.getMyProfile.id, ProfilesKeys.getMyProfile(), ProfileResponse.serializer())

    /** The profile as a load state. Waits in `Loading` until the session's partition is open. */
    public fun me(): Flow<Resource<MyProfile>> = cache.observe(spec) { api.getMyProfile() }.map { r -> r.map { it.toDomain() } }

    /** One settled read. */
    public suspend fun get(): StapelResult<MyProfile, ProfilesError> = settle(cache.get(spec) { api.getMyProfile() }) { it.toDomain() }

    /** Applies [update] (`PATCH /me`) and stores the answer; an empty update sends nothing and re-reads. */
    public suspend fun update(update: ProfileUpdate): StapelResult<MyProfile, ProfilesError> {
        if (update.isEmpty) return get()
        return when (val r = api.updateMyProfile(update.toRequest())) {
            is StapelResult.Ok -> {
                cache.write(spec, r.value)
                ProfilesInvalidation.after(ProfilesOperations.updateMyProfile.id)
                    .filterNot { it.matches(spec.key) }
                    .forEach { cache.invalidate(it) }
                StapelResult.Ok(r.value.toDomain())
            }
            is StapelResult.Err -> StapelResult.Err(ProfilesError.of(r.error))
        }
    }

    /**
     * Sets a new avatar from [file] as ONE operation: the upload ([AvatarUploader]) and the `PATCH` that
     * stores the reference together with its source. Nothing is stored when the upload fails.
     *
     * @param onProgress upload progress `(sent, total)`.
     */
    public suspend fun setAvatar(file: FilePart, onProgress: ((sent: Long, total: Long?) -> Unit)? = null): StapelResult<MyProfile, ProfilesError> =
        when (val uploaded = uploader.upload(file, onProgress)) {
            is StapelResult.Ok -> setAvatar(uploaded.value)
            is StapelResult.Err -> uploaded
        }

    /** Stores [avatar] (reference and source in one `PATCH`). */
    public suspend fun setAvatar(avatar: AvatarRef): StapelResult<MyProfile, ProfilesError> = update(ProfileUpdate(avatar = avatar))
}

/**
 * The language catalogue (`GET /languages/`): public, app-scoped, cache-first.
 *
 * @param api the generated client.
 * @param cache the app's cache.
 */
public class LanguagesRepository(private val api: ProfilesApi, private val cache: StapelCache) {
    private val spec: QuerySpec<List<LanguageResponse>> = planned(ProfilesOperations.listLanguages.id, ProfilesKeys.listLanguages(), ListSerializer(LanguageResponse.serializer()))

    /** The catalogue as a load state, sorted by name. */
    public fun languages(): Flow<Resource<List<Language>>> =
        cache.observe(spec) { api.listLanguages() }.map { r -> r.map { list -> list.map { it.toDomain() }.sortedBy { it.name } } }
}

/**
 * Other people: public profiles (`GET /{user_id}`), the people batch (`POST /batch`) and the
 * relationship actions (follow, unfollow, block, unblock). Every action invalidates the relationship,
 * the profiles, the three lists and the own counters, per the cache plan.
 *
 * @param api the generated client.
 * @param cache the app's cache.
 * @param viewer the signed-in account id, or null; a profile of the viewer is [RelationshipStatus.Self].
 */
public class PeopleRepository(
    private val api: ProfilesApi,
    private val cache: StapelCache,
    private val viewer: () -> String?,
) {
    private fun spec(userId: String): QuerySpec<ProfilePublicResponse> = planned(ProfilesOperations.getProfile.id, ProfilesKeys.getProfile(userId), ProfilePublicResponse.serializer())

    /** The public profile of [userId] as a load state. */
    public fun profile(userId: String): Flow<Resource<PublicProfile>> =
        cache.observe(spec(userId)) { api.getProfile(userId) }.map { r -> r.map { it.toDomain(viewer()) } }

    /** One settled read of [userId]'s public profile. */
    public suspend fun get(userId: String): StapelResult<PublicProfile, ProfilesError> =
        settle(cache.get(spec(userId)) { api.getProfile(userId) }) { it.toDomain(viewer()) }

    /**
     * The profiles of [userIds] in that order (`POST /batch`, at most [BATCH_MAX] per request); people
     * without a profile come back with a null [Person.profile].
     */
    public suspend fun batch(userIds: List<String>): StapelResult<List<Person>, ProfilesError> {
        val found = HashMap<String, PublicProfile>()
        for (chunk in userIds.distinct().chunked(BATCH_MAX)) {
            when (val r = api.batchProfiles(ProfileBatchRequest(chunk))) {
                is StapelResult.Ok -> r.value.profiles.forEach { found[it.userId] = it.toDomain(viewer()) }
                is StapelResult.Err -> return StapelResult.Err(ProfilesError.of(r.error))
            }
        }
        return StapelResult.Ok(userIds.map { Person(it, found[it]) })
    }

    /** Follows [userId]; answers the new status. */
    public suspend fun follow(userId: String): StapelResult<RelationshipStatus, ProfilesError> =
        act(ProfilesOperations.followUser.id) { api.followUser(userId) }

    /** Stops following [userId]. */
    public suspend fun unfollow(userId: String): StapelResult<RelationshipStatus, ProfilesError> =
        act(ProfilesOperations.unfollowUser.id) { api.unfollowUser(userId) }

    /** Blocks [userId] (they stop following the viewer). */
    public suspend fun block(userId: String): StapelResult<RelationshipStatus, ProfilesError> =
        act(ProfilesOperations.blockUser.id) { api.blockUser(userId) }

    /** Unblocks [userId]. */
    public suspend fun unblock(userId: String): StapelResult<RelationshipStatus, ProfilesError> =
        act(ProfilesOperations.unblockUser.id) { api.unblockUser(userId) }

    private suspend fun act(
        operationId: String,
        call: suspend () -> StapelResult<stapel.profiles.api.model.RelationshipActionResponse, StapelError>,
    ): StapelResult<RelationshipStatus, ProfilesError> = when (val r = call()) {
        is StapelResult.Ok -> {
            ProfilesInvalidation.after(operationId).forEach { cache.invalidate(it) }
            StapelResult.Ok(RelationshipStatus.of(r.value.status))
        }
        is StapelResult.Err -> StapelResult.Err(ProfilesError.of(r.error))
    }

    /** Limits. */
    public companion object {
        /** Ids per `/batch` request (the backend's default ceiling). */
        public const val BATCH_MAX: Int = 100
    }
}

/**
 * The viewer's followers, following and blocked lists. Followers and following carry ids, resolved
 * through the people batch; the blocked list carries the profiles itself.
 *
 * @param api the generated client.
 * @param cache the app's cache.
 * @param people the batch for the id lists.
 * @param viewer the signed-in account id, or null.
 */
public class ConnectionsRepository(
    private val api: ProfilesApi,
    private val cache: StapelCache,
    private val people: PeopleRepository,
    private val viewer: () -> String?,
) {
    private val followers: QuerySpec<FollowersResponse> = planned(ProfilesOperations.getMyFollowers.id, ProfilesKeys.getMyFollowers(), FollowersResponse.serializer())
    private val following: QuerySpec<FollowingResponse> = planned(ProfilesOperations.getMyFollowing.id, ProfilesKeys.getMyFollowing(), FollowingResponse.serializer())
    private val blocked: QuerySpec<List<ProfilePublic>> = planned(ProfilesOperations.getMyBlocked.id, ProfilesKeys.getMyBlocked(), ListSerializer(ProfilePublic.serializer()))

    /** The people of [kind], resolved to profiles. */
    public suspend fun load(kind: ConnectionKind): StapelResult<List<Person>, ProfilesError> = when (kind) {
        ConnectionKind.Followers -> ids(cache.get(followers) { api.getMyFollowers() }) { it.followers }
        ConnectionKind.Following -> ids(cache.get(following) { api.getMyFollowing() }) { it.following }
        ConnectionKind.Blocked -> settle(cache.get(blocked) { api.getMyBlocked() }) { list ->
            list.map { p -> Person(p.userId, p.toDomain(viewer())) }
        }
    }

    private suspend fun <T> ids(result: StapelResult<T, StapelError>, pick: (T) -> List<String>): StapelResult<List<Person>, ProfilesError> =
        when (result) {
            is StapelResult.Ok -> people.batch(pick(result.value))
            is StapelResult.Err -> StapelResult.Err(ProfilesError.of(result.error))
        }

}

/**
 * The person's own phone numbers (`/contacts`): user-scoped, memory only (phone numbers never reach
 * the disk), stale-while-revalidate; every change invalidates the list and the own profile.
 *
 * @param api the generated client.
 * @param cache the app's cache.
 */
public class ContactsRepository(private val api: ProfilesApi, private val cache: StapelCache) {
    private val spec: QuerySpec<ContactListResponse> = planned(ProfilesOperations.listMyContacts.id, ProfilesKeys.listMyContacts(), ContactListResponse.serializer())

    /** The numbers and the offered policies as a load state. */
    public fun contacts(): Flow<Resource<Contacts>> = cache.observe(spec) { api.listMyContacts() }.map { r -> r.map { it.toDomain() } }

    /** Adds [number] with [label] and [policy] (null = the server's default). */
    public suspend fun add(number: String, label: String = "", policy: ContactPolicy? = null): StapelResult<Contact, ProfilesError> =
        changed(ProfilesOperations.addMyContact.id, api.addMyContact(ContactCreateRequest(value = number, label = label.ifEmpty { null }, policy = policy?.wire)))

    /** Changes the label, policy or switch of [contactId]; null leaves a field as it is. */
    public suspend fun update(contactId: Int, label: String? = null, policy: ContactPolicy? = null, enabled: Boolean? = null): StapelResult<Contact, ProfilesError> =
        changed(
            ProfilesOperations.updateMyContact.id,
            api.updateMyContact(
                contactId,
                PatchedContactUpdateRequest(
                    label = label?.let { Maybe.Present(it) } ?: Maybe.Absent,
                    policy = policy?.let { Maybe.Present(it.wire) } ?: Maybe.Absent,
                    enabled = enabled?.let { Maybe.Present(it) } ?: Maybe.Absent,
                ),
            ),
        )

    /** Deletes [contactId]. */
    public suspend fun delete(contactId: Int): StapelResult<Unit, ProfilesError> = when (val r = api.deleteMyContact(contactId)) {
        is StapelResult.Ok -> {
            invalidateAfter(ProfilesOperations.deleteMyContact.id)
            StapelResult.Ok(Unit)
        }
        is StapelResult.Err -> StapelResult.Err(ProfilesError.of(r.error))
    }

    /** Sends a verification code to [contactId] by SMS; answers how long the code lives, when the server said. */
    public suspend fun requestCode(contactId: Int): StapelResult<Int?, ProfilesError> = when (val r = api.requestContactVerification(contactId)) {
        is StapelResult.Ok -> StapelResult.Ok(r.value.expiresIn)
        is StapelResult.Err -> StapelResult.Err(ProfilesError.of(r.error))
    }

    /** Confirms [contactId] with [code]. */
    public suspend fun confirmCode(contactId: Int, code: String): StapelResult<Contact, ProfilesError> =
        changed(ProfilesOperations.confirmContactVerification.id, api.confirmContactVerification(contactId, ContactVerifyConfirmRequest(code)))

    private suspend fun changed(
        operationId: String,
        result: StapelResult<stapel.profiles.api.model.ContactResponse, StapelError>,
    ): StapelResult<Contact, ProfilesError> = when (result) {
        is StapelResult.Ok -> {
            invalidateAfter(operationId)
            StapelResult.Ok(result.value.toDomain())
        }
        is StapelResult.Err -> StapelResult.Err(ProfilesError.of(result.error))
    }

    private suspend fun invalidateAfter(operationId: String) {
        ProfilesInvalidation.after(operationId).forEach { cache.invalidate(it) }
    }
}

internal inline fun <V, R> settle(result: StapelResult<V, StapelError>, map: (V) -> R): StapelResult<R, ProfilesError> =
    when (result) {
        is StapelResult.Ok -> StapelResult.Ok(map(result.value))
        is StapelResult.Err -> StapelResult.Err(ProfilesError.of(result.error))
    }
