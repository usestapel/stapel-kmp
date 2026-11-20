package stapel.notifications.e2e

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import stapel.notifications.api.NotificationsApi
import stapel.notifications.push.NotificationPushGroup
import stapel.notifications.push.NotificationPushMessage
import stapel.push.PushMessage
import stapel.push.Registration
import stapel.result.StapelResult
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Push registration against the stand: the token lifecycle meets the real device registry (G-E1). */
class PushRegistrationTest {
    private suspend fun Device.awaitRegistration(expected: Registration) {
        push.state.await { it.registration == expected }
    }

    private suspend fun waitUntil(what: String, condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            delay(100)
        }
        error("timed out: $what")
    }

    @Test
    fun `E-1 sign-in registers a session-bound row, sign-out removes it, the next account on the installation registers without a conflict`(): Unit =
        runBlocking {
            Device("e1").use { d ->
                d.signIn()
                assertEquals(Registration.Off, d.push.state.value.registration, "nothing is registered before the person turns push on")
                assertTrue(Stand.devices(d.userId).isEmpty())

                assertEquals(Registration.Registered, d.push.enable().registration)
                val first = Stand.devices(d.userId)
                assertEquals(1, first.size, first.describe())
                val row = first.single().jsonObject
                assertEquals(d.installation, row.getValue("device_id").jsonPrimitive.content, "the row carries the installation id")
                assertTrue(row.getValue("session_id") !is JsonNull, "the row is bound to the session (sid claim): ${row}")
                assertTrue(row.getValue("is_active").jsonPrimitive.boolean)
                assertEquals("ios", row.getValue("platform").jsonPrimitive.content)
                val firstToken = assertNotNull(d.provider.currentToken)

                val alice = d.userId
                d.auth.session.logout()
                waitUntil("alice's row gone") { Stand.devices(alice).isEmpty() }
                assertEquals(null, d.provider.currentToken, "the provider token is deleted when the session ends")
                println("[e1] client unregister: " + if (d.markers.has("stapel.push unregistered")) "succeeded" else "did not reach the server (server-side cleanup removed the row)")

                d.signIn()
                d.awaitRegistration(Registration.Registered)
                val bob = Stand.devices(d.userId)
                assertEquals(1, bob.size, bob.describe())
                assertEquals(d.installation, bob.single().jsonObject.getValue("device_id").jsonPrimitive.content)
                assertTrue(Stand.devices(alice).isEmpty(), "the previous account keeps nothing on this installation")
                assertNotEquals(firstToken.value, d.provider.currentToken?.value, "the next account got a fresh token")
                assertTrue(!d.markers.has("failed register"), "no conflict on the way: ${d.markers.lines}")
            }
        }

    @Test
    fun `E-2 the mock sender's captured message decodes with the generated types and a tap opens its link and marks it read`(): Unit =
        runBlocking {
            Device("e2").use { d ->
                d.signIn()
                assertEquals(Registration.Registered, d.push.enable().registration)
                val outbox = Stand.sendPush(d.userId, url = "shopmobile://app/orders/42")
                assertEquals(1, outbox.size, "one device, one message: $outbox")
                val captured = outbox.single().jsonObject.getValue("message")
                val message = Json { ignoreUnknownKeys = false }.decodeFromJsonElement(NotificationPushMessage.serializer(), captured)
                val payload = assertNotNull(message.payload, "data.v / required keys: ${message.data}")
                assertEquals("shopmobile://app/orders/42", payload.url)
                assertEquals(payload.group.wire, message.android.channelId)
                assertTrue(payload.group != NotificationPushGroup.Unknown, "group ${payload.group}")

                val api = d.app.koin.get<NotificationsApi>()
                fun readAt(): String? = (api.let { runBlocking { it.getNotificationFeed() } } as StapelResult.Ok).value.items
                    .single { it.id == payload.notificationId }.readAt
                assertEquals(null, readAt(), "the feed row is unread before the tap")

                val opened = mutableListOf<String>()
                d.push.attach { opened += it; true }
                d.provider.open(PushMessage(null, message.title, message.body, message.data, "fake"))
                waitUntil("the tap routed") { opened.isNotEmpty() }
                assertEquals(listOf("shopmobile://app/orders/42"), opened)
                waitUntil("the feed row read") { readAt() != null }
                assertTrue(d.markers.has("stapel.push opened ${payload.notificationType} link=opened"), d.markers.lines.toString())
            }
        }

    @Test
    fun `E-3 a guest session never registers`(): Unit = runBlocking {
        Device("e3").use { d ->
            d.app.koin.get<stapel.session.SessionManager>().restore()
            val guest = d.auth.session.ensureGuest()
            if (guest !is StapelResult.Ok) {
                println("[e3] the stand offers no guest sign-in ($guest); nothing to prove")
                return@runBlocking
            }
            d.push.enable()
            delay(500)
            assertTrue(d.provider.log.none { it.startsWith("token") } || Stand.devices(guest.value.id).isEmpty())
            assertTrue(Stand.devices(guest.value.id).isEmpty(), "a guest holds no device row")
        }
    }

    @Test
    fun `E-4 with the farewell run before the server revoke, the client's own unregister reaches the server`(): Unit = runBlocking {
        // What stapel-auth's sign-out must do (follow-up): SessionManager.farewell() before POST logout/,
        // which blacklists the access token. Today the auth brick revokes first, so the client's unregister
        // answers 401 (E-1) and the row goes only through the backend's session-revoked cleanup.
        Device("e4").use { d ->
            d.signIn()
            assertEquals(Registration.Registered, d.push.enable().registration)
            val user = d.userId
            d.app.koin.get<stapel.session.SessionManager>().farewell()
            assertTrue(d.markers.has("stapel.push unregistered"), d.markers.lines.toString())
            assertTrue(Stand.devices(user).isEmpty(), "the client's own call removed the row")
            d.auth.session.logout()
            assertTrue(!d.markers.has("unregister failed"), d.markers.lines.toString())
        }
    }

    @Suppress("unused")
    private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content
}
