@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.profiles

import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import stapel.cache.Persistence
import stapel.cache.ReadPolicy
import stapel.profiles.api.ProfilesKeys
import stapel.profiles.api.ProfilesOperations
import stapel.profiles.api.model.FollowersResponse
import stapel.profiles.api.model.ProfileBatchRequest
import stapel.profiles.api.model.ProfileBatchResponse
import stapel.profiles.api.model.RelationshipActionResponse
import stapel.profiles.error.ProfilesError
import stapel.profiles.error.ProfilesErrorReason
import stapel.profiles.internal.planned
import stapel.profiles.model.AvatarRef
import stapel.profiles.model.ConnectionKind
import stapel.profiles.model.NotificationCategory
import stapel.profiles.model.NotificationChannel
import stapel.profiles.model.ProfileUpdate
import stapel.profiles.model.RelationshipStatus
import stapel.profiles.model.ThemePreference
import stapel.result.StapelResult
import stapel.result.state.Resource
import stapel.testing.FakeAnswer
import stapel.wire.WireJson
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryTest {
    @Test
    fun anUpdateSendsOnlyTheFieldsItSetsAndTheAnswerReachesObserversWithoutARefetch() = runTest {
        val f = Fixture(this)
        f.signIn()
        f.transport.on(Ops.ME) { FakeAnswer.Value(profile(name = "Ada")) }
        f.transport.on(Ops.UPDATE) { FakeAnswer.Value(profile(name = "Grace")) }
        assertEquals("Ada", f.profiles.me.me().filterIsInstance<Resource.Ready<*>>().first().let { (it.data as stapel.profiles.model.MyProfile).displayName })
        val r = f.profiles.me.update(ProfileUpdate(displayName = "Grace", theme = ThemePreference.Dark))
        assertEquals("Grace", (r as StapelResult.Ok).value.displayName)
        assertEquals(setOf("display_name", "theme"), WireJson.parseToJsonElement(f.patchBody()).jsonObject.keys)
        val reads = f.count(Ops.ME)
        assertEquals("Grace", f.profiles.me.get().let { (it as StapelResult.Ok).value.displayName })
        assertEquals(reads, f.count(Ops.ME), "the PATCH answer is written to the cache; no re-read")
    }

    @Test
    fun notificationSwitchesAreSentAllFour() = runTest {
        val f = Fixture(this)
        f.signIn()
        f.transport.on(Ops.UPDATE) { FakeAnswer.Value(profile()) }
        val prefs = stapel.profiles.model.NotificationPreferences(true, true, true, true)
            .with(NotificationCategory.Messages, NotificationChannel.Push, false)
        f.profiles.me.update(ProfileUpdate(notifications = prefs))
        val body = WireJson.parseToJsonElement(f.patchBody()).jsonObject
        assertEquals(setOf("email_messages", "email_system", "push_messages", "push_system"), body.keys)
        assertEquals("false", body["push_messages"].toString())
    }

    @Test
    fun setAvatarUploadsThenStoresTheReferenceWithItsSourceInOnePatch() = runTest {
        val f = Fixture(this)
        f.signIn()
        f.transport.on(Ops.UPDATE) { FakeAnswer.Value(profile(avatar = "avatar/abc", avatarSource = "cdn")) }
        val progress = mutableListOf<Pair<Long, Long?>>()
        val r = f.profiles.me.setAvatar(picture()) { s, t -> progress += s to t }
        assertEquals(AvatarRef("avatar/abc", "cdn"), (r as StapelResult.Ok).value.avatarRef)
        assertEquals(1, f.uploader.files.size)
        assertEquals(listOf<Pair<Long, Long?>>(5L to 10L), progress)
        val body = WireJson.parseToJsonElement(f.patchBody()).jsonObject
        assertEquals("\"avatar/abc\"", body["avatar"].toString())
        assertEquals("\"cdn\"", body["avatar_source"].toString(), "the reference never travels without its source")
    }

    @Test
    fun aFailedUploadStoresNothing() = runTest {
        val f = Fixture(this)
        f.signIn()
        f.uploader.answer = StapelResult.Err(ProfilesError.client(ProfilesErrorReason.InvalidAvatar))
        val r = f.profiles.me.setAvatar(picture())
        assertEquals(ProfilesErrorReason.InvalidAvatar, (r as StapelResult.Err).error.reason)
        assertEquals(0, f.count(Ops.UPDATE))
    }

    @Test
    fun withoutAnUploaderTheAvatarIsUnsupported() = runTest {
        val f = Fixture(this)
        val profiles = StapelProfiles(f.profiles.api, f.cache)
        assertTrue(!profiles.canUploadAvatar)
        val r = profiles.me.setAvatar(picture())
        assertEquals(ProfilesErrorReason.Unsupported, (r as StapelResult.Err).error.reason)
    }

    @Test
    fun theViewersOwnProfileReadsAsSelf() = runTest {
        val f = Fixture(this, viewer = "u1")
        f.signIn()
        f.transport.on(Ops.PROFILE) { call -> FakeAnswer.Value(publicProfile(call.pathParams.single(), status = "neutral")) }
        assertEquals(RelationshipStatus.Self, (f.profiles.people.get("u1") as StapelResult.Ok).value.relationship)
        assertEquals(RelationshipStatus.Neutral, (f.profiles.people.get("u2") as StapelResult.Ok).value.relationship)
    }

    @Test
    fun theBatchKeepsTheRequestedOrderChunksAtTheCeilingAndMarksMissingPeople() = runTest {
        val f = Fixture(this)
        val sizes = mutableListOf<Int>()
        f.transport.on(Ops.BATCH) { call ->
            val ids = (call.body as ProfileBatchRequest).userIds
            sizes += ids.size
            FakeAnswer.Value(ProfileBatchResponse(ids.filter { it != "x7" }.reversed().map { publicProfile(it) }, listOf("x7")))
        }
        val ids = (0 until 150).map { "x$it" }
        val people = (f.profiles.people.batch(ids) as StapelResult.Ok).value
        assertEquals(listOf(100, 50), sizes)
        assertEquals(ids, people.map { it.userId })
        assertNull(people[7].profile)
        assertEquals("P-x8", people[8].profile?.displayName)
    }

    @Test
    fun aRelationshipActionInvalidatesTheProfileTheListsAndTheOwnCounters() = runTest {
        val f = Fixture(this)
        f.signIn()
        f.transport.on(Ops.ME) { FakeAnswer.Value(profile()) }
        f.transport.on(Ops.PROFILE) { FakeAnswer.Value(publicProfile("u2")) }
        f.transport.on(Ops.FOLLOWERS) { FakeAnswer.Value(FollowersResponse(emptyList(), 0)) }
        f.transport.on(Ops.BATCH) { FakeAnswer.Value(ProfileBatchResponse(emptyList(), emptyList())) }
        f.transport.on(Ops.FOLLOW) { FakeAnswer.Value(RelationshipActionResponse(true, "following")) }
        f.profiles.me.get()
        f.profiles.people.get("u2")
        f.profiles.connections.load(ConnectionKind.Followers)
        val before = listOf(Ops.ME, Ops.PROFILE, Ops.FOLLOWERS).map(f::count)
        assertEquals(RelationshipStatus.Following, (f.profiles.people.follow("u2") as StapelResult.Ok).value)
        f.profiles.me.get()
        f.profiles.people.get("u2")
        f.profiles.connections.load(ConnectionKind.Followers)
        runCurrent()
        val after = listOf(Ops.ME, Ops.PROFILE, Ops.FOLLOWERS).map(f::count)
        assertEquals(before.map { it + 1 }, after, "each read went back to the network once")
    }

    @Test
    fun theCachePlanReachesTheReadSpecs() {
        // Phone numbers are memory-only; the own profile is durable and revalidated; the catalogue is app-wide.
        val contacts = planned(ProfilesOperations.listMyContacts.id, ProfilesKeys.listMyContacts(), String.serializer())
        assertEquals(Persistence.MemoryOnly, contacts.persistence)
        val me = planned(ProfilesOperations.getMyProfile.id, ProfilesKeys.getMyProfile(), String.serializer())
        assertEquals(Persistence.Durable, me.persistence)
        assertIs<ReadPolicy.StaleWhileRevalidate>(me.policy)
        val languages = planned(ProfilesOperations.listLanguages.id, ProfilesKeys.listLanguages(), String.serializer())
        assertIs<ReadPolicy.CacheFirst>(languages.policy)
        assertEquals(stapel.wire.CacheScope.App, ProfilesKeys.listLanguages().scope)
        assertIs<ReadPolicy.NetworkFirst>(planned(ProfilesOperations.getRelationship.id, ProfilesKeys.getRelationship("u"), String.serializer()).policy)
        val reveal = ProfilesKeys.hints.getValue(ProfilesOperations.revealContacts.id)
        assertEquals(true, reveal.sensitive)
    }

    @Test
    fun errorsAreClassifiedForTheScreens() {
        fun reason(code: String, status: Int) = ProfilesError.of(stapel.result.ServerRefusal(stapel.result.ErrorEnvelope(stapel.result.ErrorCode(code), status))).reason
        assertEquals(ProfilesErrorReason.InvalidAvatar, reason("error.400.avatar_not_found", 400))
        assertEquals(ProfilesErrorReason.Self, reason("error.400.cannot_follow_self", 400))
        assertEquals(ProfilesErrorReason.Duplicate, reason("error.409.contacts_duplicate", 409))
        assertEquals(ProfilesErrorReason.InvalidCode, reason("error.400.contacts_invalid_code", 400))
        assertEquals(ProfilesErrorReason.RegistrationRequired, reason("error.403.contacts_registration_required", 403))
        assertEquals(ProfilesErrorReason.RateLimited, reason("error.429.contacts_code_rate", 429))
        assertEquals(ProfilesErrorReason.NotFound, reason("error.404.profile_not_found", 404))
        assertEquals(ProfilesErrorReason.InvalidInput, reason("error.400.display_name_emoji", 400))
        assertEquals(ProfilesErrorReason.Network, ProfilesError.of(stapel.result.TransportFailure.Offline()).reason)
    }
}
