package stapel.notifications.e2e

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import stapel.auth.session.AuthState
import stapel.auth.session.LogoutResult
import stapel.auth.session.SignOutReason
import stapel.notifications.api.NotificationsApi
import stapel.notifications.push.NotificationPushGroup
import stapel.notifications.push.NotificationPushMessage
import stapel.push.PushMessage
import stapel.push.Registration
import stapel.result.StapelResult
import stapel.session.SessionManager
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

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
                assertEquals(LogoutResult.RevokedOnServer, d.auth.session.logout())
                d.assertFarewellBeforeRevoke()
                assertTrue(Stand.devices(alice).isEmpty())
                assertEquals(null, d.provider.currentToken, "the provider token is deleted when the session ends")

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
    fun `E-4 a farewell hook that fails and one that hangs never block the sign-out, and push's own unregister still lands first`(): Unit =
        runBlocking {
            Device("e4").use { d ->
                d.signIn()
                val sessions = d.app.koin.get<SessionManager>()
                sessions.registerFarewellHook { throw IllegalStateException("a failing farewell") }
                sessions.registerFarewellHook { awaitCancellation() }
                assertEquals(Registration.Registered, d.push.enable().registration)
                val user = d.userId
                val took = measureTime { assertEquals(LogoutResult.RevokedOnServer, d.auth.session.logout()) }
                assertTrue(took < 10.seconds, "the sign-out waited for a hanging hook: $took")
                d.assertFarewellBeforeRevoke()
                assertTrue(Stand.devices(user).isEmpty())
                assertEquals(AuthState.SignedOut(SignOutReason.UserRequested), d.auth.session.state.value)
            }
        }

    /**
     * The sign-out order on the wire: push's own `POST devices/unregister/` answered 204 (the row deleted) with the bearer
     * still valid, before `POST logout/` went out, and when the revoke left the server already held no
     * row for the account — the client's call removed it, not the backend's session-revoked cleanup.
     */
    private fun Device.assertFarewellBeforeRevoke() {
        val unregister = wire.indexOf(Wire.UNREGISTER_OPERATION)
        val revoke = wire.indexOf(Wire.LOGOUT_OPERATION)
        assertTrue(unregister >= 0 && revoke >= 0, "both calls went out: ${wire.attempts}")
        assertEquals("${Wire.UNREGISTER_OPERATION} 204", wire.attempts[unregister], "the client's unregister deleted the row (204, not 401 and not the 404 of nothing left): ${wire.attempts}")
        assertTrue(unregister < revoke, "unregister before the revoke: ${wire.attempts}")
        assertEquals(0, wire.rowsBeforeRevoke, "rows of the account when the revoke left")
        assertTrue(markers.has("stapel.push unregistered"), markers.lines.toString())
        assertTrue(!markers.has("unregister failed"), markers.lines.toString())
    }

    @Suppress("unused")
    private fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content
}
