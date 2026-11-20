@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.profiles

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import stapel.profiles.api.model.ContactVerifyRequestResponse
import stapel.profiles.api.model.FollowersResponse
import stapel.profiles.api.model.FollowingResponse
import stapel.profiles.api.model.ProfileBatchRequest
import stapel.profiles.api.model.ProfileBatchResponse
import stapel.profiles.api.model.RelationshipActionResponse
import stapel.profiles.error.ProfilesErrorReason
import stapel.profiles.model.ConnectionKind
import stapel.profiles.model.NotificationCategory
import stapel.profiles.model.NotificationChannel
import stapel.profiles.presentation.ConnectionsIntent
import stapel.profiles.presentation.ConnectionsPresenter
import stapel.profiles.presentation.ContactsIntent
import stapel.profiles.presentation.ContactsPresenter
import stapel.profiles.presentation.ProfileSettingsIntent
import stapel.profiles.presentation.ProfileSettingsPresenter
import stapel.profiles.presentation.PublicProfileIntent
import stapel.profiles.presentation.PublicProfilePresenter
import stapel.profiles.presentation.RelationshipAction
import stapel.testing.FakeAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PresenterTest {
    private suspend fun TestScope.signedIn(): Fixture = Fixture(this).also { it.signIn() }

    @Test
    fun settingsShowTheProfileAndARenameIsStored() = runTest {
        val f = signedIn()
        f.transport.on(Ops.ME) { FakeAnswer.Value(profile(name = "Ada")) }
        f.transport.on(Ops.UPDATE) { FakeAnswer.Value(profile(name = "Grace")) }
        val p = ProfileSettingsPresenter(f.profiles, f.context)
        runCurrent()
        assertEquals("Ada", p.state.value.profile?.displayName)
        p.dispatch(ProfileSettingsIntent.SetDisplayName("  Ada "))
        runCurrent()
        assertEquals(0, f.count(Ops.UPDATE), "an unchanged name sends nothing")
        p.dispatch(ProfileSettingsIntent.SetDisplayName("Grace"))
        runCurrent()
        assertEquals("Grace", p.state.value.profile?.displayName)
        assertEquals(false, p.state.value.saving)
    }

    @Test
    fun aRefusedNotificationSwitchGoesBackToWhatTheServerHolds() = runTest {
        val f = signedIn()
        f.transport.on(Ops.ME) { FakeAnswer.Value(profile(emailMessages = true)) }
        f.transport.on(Ops.UPDATE) { refusal("error.400.validation_error", 400) }
        val p = ProfileSettingsPresenter(f.profiles, f.context)
        runCurrent()
        p.dispatch(ProfileSettingsIntent.SetNotification(NotificationCategory.Messages, NotificationChannel.Email, false))
        runCurrent()
        assertEquals(true, p.state.value.profile?.notifications?.emailMessages)
        assertEquals(ProfilesErrorReason.InvalidInput, p.state.value.saveError?.reason)
    }

    @Test
    fun twoQuickSwitchesBothReachTheServer() = runTest {
        val f = signedIn()
        f.transport.on(Ops.ME) { FakeAnswer.Value(profile()) }
        f.transport.on(Ops.UPDATE) { FakeAnswer.Value(profile(emailMessages = false, pushSystem = false)) }
        val p = ProfileSettingsPresenter(f.profiles, f.context)
        runCurrent()
        p.dispatch(ProfileSettingsIntent.SetNotification(NotificationCategory.Messages, NotificationChannel.Email, false))
        p.dispatch(ProfileSettingsIntent.SetNotification(NotificationCategory.System, NotificationChannel.Push, false))
        runCurrent()
        assertEquals(2, f.count(Ops.UPDATE))
        // The second PATCH carries the first switch too: it was built on the optimistic state.
        assertTrue("\"email_messages\":false" in f.patchBody(), f.patchBody())
    }

    @Test
    fun theAvatarIsUploadedAndShown() = runTest {
        val f = signedIn()
        f.transport.on(Ops.ME) { FakeAnswer.Value(profile()) }
        f.transport.on(Ops.UPDATE) { FakeAnswer.Value(profile(avatar = "avatar/abc", avatarSource = "cdn")) }
        val p = ProfileSettingsPresenter(f.profiles, f.context)
        runCurrent()
        assertTrue(p.state.value.canUploadAvatar)
        p.dispatch(ProfileSettingsIntent.SetAvatar(picture()))
        runCurrent()
        assertEquals("avatar/abc", p.state.value.profile?.avatarRef?.ref)
        assertEquals(false, p.state.value.uploading)
    }

    @Test
    fun aPublicProfileOffersActionsOnlyToASignedInStranger() = runTest {
        val f = signedIn()
        val followed = mutableSetOf<String>()
        f.transport.on(Ops.PROFILE) { call ->
            val id = call.pathParams.single()
            FakeAnswer.Value(publicProfile(id, status = if (id in followed) "following" else "neutral"))
        }
        f.transport.on(Ops.FOLLOW) { call ->
            followed += call.pathParams.single()
            FakeAnswer.Value(RelationshipActionResponse(true, "following"))
        }
        val self = PublicProfilePresenter(f.profiles, f.context, "u1", signedIn = true)
        val anonymous = PublicProfilePresenter(f.profiles, f.context, "u2", signedIn = false)
        val stranger = PublicProfilePresenter(f.profiles, f.context, "u3", signedIn = true)
        runCurrent()
        assertEquals(emptyList(), self.state.value.actions)
        assertEquals(emptyList(), anonymous.state.value.actions)
        assertEquals(listOf(RelationshipAction.Follow, RelationshipAction.Block), stranger.state.value.actions)
        stranger.dispatch(PublicProfileIntent.Act(RelationshipAction.Follow))
        runCurrent()
        assertEquals(listOf(RelationshipAction.Unfollow, RelationshipAction.Block), stranger.state.value.actions)
    }

    @Test
    fun aProfileThatDoesNotExistIsNotFound() = runTest {
        val f = signedIn()
        f.transport.on(Ops.PROFILE) { refusal("error.404.profile_not_found", 404) }
        val p = PublicProfilePresenter(f.profiles, f.context, "ghost", signedIn = true)
        runCurrent()
        assertEquals(ProfilesErrorReason.NotFound, p.state.value.loadError?.reason)
        assertNull(p.state.value.profile)
    }

    @Test
    fun connectionsSwitchListsAndResolvePeopleThroughTheBatch() = runTest {
        val f = signedIn()
        f.transport.on(Ops.FOLLOWERS) { FakeAnswer.Value(FollowersResponse(listOf("a"), 1)) }
        f.transport.on("get_my_following") { FakeAnswer.Value(FollowingResponse(listOf("b", "c"), 2)) }
        f.transport.on(Ops.BATCH) { call -> FakeAnswer.Value(ProfileBatchResponse((call.body as ProfileBatchRequest).userIds.map { publicProfile(it) }, emptyList())) }
        val p = ConnectionsPresenter(f.profiles, f.context)
        runCurrent()
        assertEquals(listOf("a"), p.state.value.people?.map { it.userId })
        p.dispatch(ConnectionsIntent.Show(ConnectionKind.Following))
        runCurrent()
        assertEquals(ConnectionKind.Following, p.state.value.kind)
        assertEquals(listOf("P-b", "P-c"), p.state.value.people?.map { it.profile?.displayName })
    }

    @Test
    fun aNumberIsAddedAndConfirmedBySms() = runTest {
        val f = signedIn()
        var verified = false
        f.transport.on(Ops.CONTACTS) { FakeAnswer.Value(if (f.count(Ops.ADD_CONTACT) == 0) contacts() else contacts(contact(1, verified))) }
        f.transport.on(Ops.ADD_CONTACT) { FakeAnswer.Value(contact(1)) }
        f.transport.on(Ops.REQUEST_CODE) { FakeAnswer.Value(ContactVerifyRequestResponse(true, 300)) }
        f.transport.on(Ops.CONFIRM_CODE) { call ->
            if ((call.body as stapel.profiles.api.model.ContactVerifyConfirmRequest).code == "0000") {
                verified = true
                FakeAnswer.Value(contact(1, verified = true))
            } else {
                refusal("error.400.contacts_invalid_code", 400, mapOf("attempts_remaining" to "2"))
            }
        }
        val p = ContactsPresenter(f.profiles, f.context)
        runCurrent()
        assertEquals(emptyList(), p.state.value.contacts?.contacts)
        p.dispatch(ContactsIntent.Add("+155501001"))
        runCurrent()
        assertEquals(listOf(1), p.state.value.contacts?.contacts?.map { it.id }, "the list is re-read after the add")
        p.dispatch(ContactsIntent.RequestCode(1))
        runCurrent()
        assertEquals(300, p.state.value.verification?.expiresInSeconds)
        p.dispatch(ContactsIntent.ConfirmCode("1111"))
        runCurrent()
        assertEquals(2, p.state.value.error?.attemptsRemaining)
        assertNotNull(p.state.value.verification)
        p.dispatch(ContactsIntent.ConfirmCode("0000"))
        runCurrent()
        assertNull(p.state.value.verification)
        assertEquals(true, p.state.value.contacts?.contacts?.single()?.verified)
    }
}
