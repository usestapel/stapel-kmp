package stapel.profiles.e2e

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import stapel.navigation.DestinationId
import stapel.navigation.resolveNav
import stapel.profiles.model.ConnectionKind
import stapel.profiles.model.ContactPolicy
import stapel.profiles.model.NotificationCategory
import stapel.profiles.model.NotificationChannel
import stapel.profiles.model.ProfileUpdate
import stapel.profiles.model.RelationshipStatus
import stapel.profiles.model.ThemePreference
import stapel.profiles.presentation.ProfileSettingsIntent
import stapel.profiles.presentation.ProfileSettingsPresenter
import stapel.profiles.ui.ProfilesNavIds
import stapel.profiles.ui.settingsSections
import stapel.result.StapelResult
import stapel.result.transportFailureOrNull
import stapel.result.state.Resource
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The profiles brick against the stand, wired through stapelApp with the auth brick. */
class ProfileScenariosTest {
    @Test
    fun `P-1 a new account reads its own profile and a rename and theme reach the server`(): Unit = runBlocking {
        Device("p1").use { d ->
            d.signIn()
            val me = (d.profiles.me.get() as StapelResult.Ok).value
            assertEquals(d.userId, me.userId)
            val name = "Ada " + d.userId.take(6)
            val updated = d.profiles.me.update(ProfileUpdate(displayName = name, theme = ThemePreference.Dark))
            assertEquals(name, (updated as StapelResult.Ok).value.displayName)
            val server = Stand.get("/profiles/api/v1/me", d.bearer())
            assertEquals(name, server["display_name"]!!.jsonPrimitive.content)
            assertEquals("dark", server["theme"]!!.jsonPrimitive.content)
            // The answer was written to the cache: the observed profile is the new one without a re-read.
            val observed = d.profiles.me.me().settled()
            assertEquals(name, assertIs<Resource.Ready<stapel.profiles.model.MyProfile>>(observed).data.displayName)
        }
    }

    @Test
    fun `P-2 the language catalogue is read and the app language is stored then handed back to the device`(): Unit = runBlocking {
        Device("p2").use { d ->
            d.signIn()
            val catalogue = assertIs<Resource.Ready<List<stapel.profiles.model.Language>>>(d.profiles.languages.languages().settled()).data
            assertTrue(catalogue.any { it.code == "ru" }, "catalogue: ${catalogue.map { it.code }}")
            val presenter = ProfileSettingsPresenter(d.profiles, d.context())
            presenter.state.await { it.profile != null }
            presenter.dispatch(ProfileSettingsIntent.SetLanguage("ru"))
            val ru = presenter.state.await { it.profile?.appLanguage?.code == "ru" && !it.saving }
            assertEquals(false, ru.profile?.useDeviceLanguage)
            assertEquals("ru", Stand.get("/profiles/api/v1/me", d.bearer())["app_language"]!!.jsonObject["code"]!!.jsonPrimitive.content)
            presenter.dispatch(ProfileSettingsIntent.SetLanguage(null))
            presenter.state.await { it.profile?.useDeviceLanguage == true && !it.saving }
            assertEquals(true, Stand.get("/profiles/api/v1/me", d.bearer())["use_device_language"]!!.jsonPrimitive.boolean)
        }
    }

    @Test
    fun `P-3 two notification switches flipped at once both reach the server`(): Unit = runBlocking {
        Device("p3").use { d ->
            d.signIn()
            val presenter = ProfileSettingsPresenter(d.profiles, d.context())
            presenter.state.await { it.profile != null }
            presenter.dispatch(ProfileSettingsIntent.SetNotification(NotificationCategory.Messages, NotificationChannel.Push, false))
            presenter.dispatch(ProfileSettingsIntent.SetNotification(NotificationCategory.System, NotificationChannel.Email, false))
            presenter.state.await { !it.saving && it.profile?.notifications?.pushMessages == false && it.profile?.notifications?.emailSystem == false }
            kotlinx.coroutines.delay(500)
            val server = Stand.get("/profiles/api/v1/me", d.bearer())
            assertEquals(false, server["push_messages"]!!.jsonPrimitive.boolean)
            assertEquals(false, server["email_system"]!!.jsonPrimitive.boolean)
            assertEquals(true, server["email_messages"]!!.jsonPrimitive.boolean)
        }
    }

    @Test
    fun `P-4 follow, the lists, the counters and the public profile seen by a stranger, a follower and oneself`(): Unit = runBlocking {
        Device("p4-a").use { a ->
            Device("p4-b").use { b ->
                a.signIn()
                b.signIn()
                a.profiles.me.update(ProfileUpdate(displayName = "Followed One"))
                assertEquals(RelationshipStatus.Neutral, (b.profiles.people.get(a.userId) as StapelResult.Ok).value.relationship)
                assertEquals(RelationshipStatus.Following, (b.profiles.people.follow(a.userId) as StapelResult.Ok).value)
                val seen = (b.profiles.people.get(a.userId) as StapelResult.Ok).value
                assertEquals(RelationshipStatus.Following, seen.relationship, "the action invalidated the cached profile")
                assertEquals(1, seen.followersCount)
                assertEquals(RelationshipStatus.Self, (a.profiles.people.get(a.userId) as StapelResult.Ok).value.relationship)
                val followers = (a.profiles.connections.load(ConnectionKind.Followers) as StapelResult.Ok).value
                assertEquals(listOf(b.userId), followers.map { it.userId })
                val following = (b.profiles.connections.load(ConnectionKind.Following) as StapelResult.Ok).value
                assertEquals("Followed One", following.single().profile?.displayName)
                b.profiles.people.block(a.userId)
                assertEquals(listOf(a.userId), (b.profiles.connections.load(ConnectionKind.Blocked) as StapelResult.Ok).value.map { it.userId })
                // No session at all: the operation is security-optional and the server answers 200, but
                // with a reduced body that omits fields the contract marks required (location_*, the
                // counters, relationship_status) — a backend defect of stapel-profiles 0.23.1, pinned
                // here: when fixed, this fails and is flipped to expect the profile.
                Device("p4-anon").use { anon ->
                    anon.app.koin.get<stapel.session.SessionManager>().restore()
                    when (val seenByAnyone = anon.profiles.people.get(a.userId)) {
                        is StapelResult.Ok -> kotlin.test.fail("anonymous profile now decodes (${seenByAnyone.value}): the backend defect is fixed — expect Ok")
                        is StapelResult.Err -> {
                            val violation = assertIs<stapel.result.TransportFailure.ContractViolation>(seenByAnyone.error.cause?.transportFailureOrNull())
                            assertTrue("followers_count" in violation.detail && "relationship_status" in violation.detail, violation.detail)
                        }
                    }
                    val raw = Stand.get("/profiles/api/v1/${a.userId}")
                    assertEquals("Followed One", raw["display_name"]!!.jsonPrimitive.content)
                    assertTrue("followers_count" !in raw, "the reduced anonymous body")
                }
            }
        }
    }

    @Test
    fun `P-5 a phone number is added, confirmed with the mock SMS code, restricted and deleted`(): Unit = runBlocking {
        Device("p5").use { d ->
            d.signIn()
            val number = "+1202555" + (100..199).random().toString().padStart(4, '0')
            val added = (d.profiles.contacts.add(number, "Work") as StapelResult.Ok).value
            assertEquals(false, added.verified)
            assertTrue((d.profiles.contacts.requestCode(added.id) as StapelResult.Ok).value != null)
            val wrong = d.profiles.contacts.confirmCode(added.id, "9999")
            assertIs<StapelResult.Err<*>>(wrong)
            assertEquals(true, (d.profiles.contacts.confirmCode(added.id, Stand.MOCK_CODE) as StapelResult.Ok).value.verified)
            assertEquals(ContactPolicy.Nobody, (d.profiles.contacts.update(added.id, policy = ContactPolicy.Nobody) as StapelResult.Ok).value.policy)
            val list = assertIs<Resource.Ready<stapel.profiles.model.Contacts>>(d.profiles.contacts.contacts().settled()).data
            assertEquals(listOf(true), list.contacts.map { it.verified })
            assertIs<StapelResult.Ok<Unit>>(d.profiles.contacts.delete(added.id))
        }
    }

    @Test
    fun `P-6 the security screen of the auth brick is a section of the profiles settings`(): Unit = runBlocking {
        Device("p6").use { d ->
            val nav = resolveNav(d.app.contributions.map { it.manifest })
            val sections = nav.byId.getValue(ProfilesNavIds.Settings).settingsSections().map { it.id }
            assertEquals(DestinationId("auth.security"), sections.first())
            assertTrue(nav.byId.getValue(DestinationId("auth.security")).absolutePath.endsWith("/settings/security"))
        }
    }
}
