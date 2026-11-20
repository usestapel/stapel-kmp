package stapel.notifications.push

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.Koin
import org.koin.dsl.module
import stapel.di.stapelKoin
import stapel.network.WireBody
import stapel.network.di.networkModule
import stapel.notifications.push.di.notificationsPushModule
import stapel.push.DevicePlatform
import stapel.push.DeviceStatus
import stapel.push.PushDevice
import stapel.push.PushDeviceRegistry
import stapel.push.PushMessage
import stapel.push.PushOpenListener
import stapel.push.PushToken
import stapel.push.RegistryError
import stapel.push.TokenKind
import stapel.result.StapelResult
import stapel.runtime.PlatformContext
import stapel.runtime.di.runtimeModule
import stapel.session.RefreshOutcome
import stapel.session.SessionKind
import stapel.session.SessionManager
import stapel.session.TokenPair
import stapel.session.TokenRefresher
import stapel.session.di.sessionModule
import stapel.session.vault.InMemoryTokenVault
import stapel.testing.FakeTransport
import stapel.testing.TestDispatchers
import stapel.testing.di.testOverridesModule
import stapel.testing.testEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The adapter through the real network pipeline on a fake wire (G-W1), and the generated payload types. */
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationsPushTest {
    private val token = PushToken("fcm-token-1", TokenKind.Fcm, "firebase")
    private val device = PushDevice(token, DevicePlatform.Ios, "install-0123456789")

    private suspend fun TestScope.graph(wire: FakeTransport): Koin {
        val koin = stapelKoin(
            modules = listOf(
                runtimeModule(object : PlatformContext {}),
                networkModule(testEnvironment()),
                sessionModule(),
                module { single<TokenRefresher> { TokenRefresher { RefreshOutcome.Transient("tests") } } },
                notificationsPushModule(),
            ),
            overrides = listOf(
                testOverridesModule(
                    appScope = backgroundScope,
                    dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)),
                    transport = wire,
                    vault = InMemoryTokenVault(),
                ),
            ),
            global = false,
        ).koin
        val session = koin.get<SessionManager>()
        session.restore()
        session.adopt(TokenPair("access-1", "refresh-1"), SessionKind.Authenticated)
        runCurrent()
        return koin
    }

    private fun FakeTransport.body(i: Int): JsonObject = Json.parseToJsonElement((requests[i].body as WireBody.Bytes).text()) as JsonObject

    @Test
    fun registerPostsTokenPlatformAndInstallationWithTheBearer() = runTest {
        val wire = FakeTransport()
        val registry = graph(wire).get<PushDeviceRegistry>()
        wire.enqueueJson(201, """{"token":"fcm-token-1","platform":"ios","device_id":"install-0123456789","session_bound":true}""")
        assertEquals(StapelResult.Ok(Unit), registry.register(device))
        val sent = wire.requests.single()
        assertEquals("POST", sent.method)
        assertEquals("https://api.test/notifications/api/v1/devices/", sent.url)
        assertEquals("Bearer access-1", sent.header("Authorization"))
        val body = wire.body(0)
        assertEquals(setOf("token", "platform", "device_id"), body.keys, "the session comes from the token's claim, never the body")
        assertEquals("ios", body.getValue("platform").jsonPrimitive.content)
        assertEquals("install-0123456789", body.getValue("device_id").jsonPrimitive.content)
    }

    @Test
    fun refusalsMapToWhatTheLifecycleActsOn() = runTest {
        val wire = FakeTransport()
        val registry = graph(wire).get<PushDeviceRegistry>()
        wire.enqueueEnvelope(409, "error.409.conflict")
        assertIs<RegistryError.HeldByAnotherAccount>((registry.register(device) as StapelResult.Err).error)
        wire.enqueueEnvelope(403, "error.403.forbidden")
        assertIs<RegistryError.NotAllowed>((registry.register(device) as StapelResult.Err).error)
        wire.enqueueEnvelope(400, "error.400.invalid_platform")
        val failed = assertIs<RegistryError.Failed>((registry.register(device) as StapelResult.Err).error)
        assertEquals("error.400.invalid_platform", failed.code.value)
    }

    @Test
    fun unregisterSendsTheInstallationInTheBodyAndTreatsAbsentAsDone() = runTest {
        val wire = FakeTransport()
        val registry = graph(wire).get<PushDeviceRegistry>()
        wire.enqueue(204)
        assertEquals(StapelResult.Ok(Unit), registry.unregister("install-0123456789"))
        val sent = wire.requests.single()
        assertEquals("https://api.test/notifications/api/v1/devices/unregister/", sent.url)
        assertTrue("fcm-token" !in sent.url, "a token never travels in a URL")
        assertEquals(setOf("device_id"), wire.body(0).keys)
        wire.enqueueEnvelope(404, "error.404.device_not_found")
        assertEquals(StapelResult.Ok(Unit), registry.unregister("install-0123456789"))
        wire.enqueueEnvelope(403, "error.403.forbidden")
        assertIs<RegistryError.NotAllowed>((registry.unregister("install-0123456789") as StapelResult.Err).error)
    }

    @Test
    fun statusMatchesTheFingerprintTheServerLists() = runTest {
        val wire = FakeTransport()
        val registry = graph(wire).get<PushDeviceRegistry>()
        fun list(fingerprint: String, active: Boolean) =
            """[{"id":1,"token_fingerprint":"$fingerprint","platform":"ios","is_active":$active,"created_at":"2026-11-20T10:00:00Z","last_seen":"2026-11-20T10:00:00Z"}]"""
        wire.enqueueJson(200, list(token.fingerprint, true))
        assertEquals(StapelResult.Ok(DeviceStatus.Registered), registry.status(token))
        wire.enqueueJson(200, list(token.fingerprint, false))
        assertEquals(StapelResult.Ok(DeviceStatus.Inactive), registry.status(token))
        wire.enqueueJson(200, list("0".repeat(64), true))
        assertEquals(StapelResult.Ok(DeviceStatus.Absent), registry.status(token))
    }

    @Test
    fun aTapMarksItsFeedRowReadAndAMessageWithoutOneSendsNothing() = runTest {
        val wire = FakeTransport()
        val listener = graph(wire).get<PushOpenListener>()
        wire.enqueueJson(200, """{"marked":1,"unread_count":0}""")
        listener.onOpened(PushMessage("m", "T", "B", SAMPLE_DATA, "firebase"))
        val sent = wire.requests.single()
        assertEquals("https://api.test/notifications/api/v1/feed/read/", sent.url)
        assertEquals(listOf("550e8400-e29b-41d4-a716-446655440000"), wire.body(0).getValue("ids").jsonArray.map { it.jsonPrimitive.content })
        listener.onOpened(PushMessage("m", "T", "B", mapOf("notification_type" to "x"), "firebase"))
        assertEquals(1, wire.requests.size)
    }

    @Test
    fun theCapturedMessageDecodesWithTheGeneratedTypes() {
        // The shape stapel-notifications' mock sender captures (compose()), strictly: unknown keys fail.
        val strict = Json { ignoreUnknownKeys = false }
        val message = strict.decodeFromString(NotificationPushMessage.serializer(), CAPTURED)
        assertEquals("messages", message.android.channelId)
        assertEquals(3L, message.apns.badge)
        val payload = assertNotNull(message.payload)
        assertEquals(NotificationPushGroup.Messages, payload.group)
        assertEquals("app://chat/42", payload.url)
        assertEquals(mapOf("chat_id" to "42"), payload.extras)
        assertNull(NotificationPushData.parse(SAMPLE_DATA + ("v" to "2")), "another payload version is not read as this one")
        assertNull(NotificationPushData.parse(SAMPLE_DATA - "notification_id"), "a required key is missing")
        assertEquals(NotificationPushGroup.Unknown, NotificationPushData.parse(SAMPLE_DATA + ("group" to "promo"))?.group)
    }

    @Test
    fun routingReadsTheCanonicalLinkFirstAndHasAChannelPerGroup() {
        val routing = notificationsPushRouting(fallbackUri = "app://notifications")
        assertEquals("url", routing.linkKeys.first())
        assertEquals(listOf("auth", "billing", "messages", "system"), routing.channels.map { it.id })
        assertEquals("app://notifications", routing.fallbackUri)
    }

    private companion object {
        val SAMPLE_DATA = mapOf(
            "v" to "1",
            "notification_id" to "550e8400-e29b-41d4-a716-446655440000",
            "notification_type" to "new_message",
            "group" to "messages",
            "badge" to "3",
            "url" to "app://chat/42",
        )
        const val CAPTURED = """{"title":"New message","body":"Hi","data":{"v":"1","notification_id":"550e8400-e29b-41d4-a716-446655440000","notification_type":"new_message","group":"messages","badge":"3","url":"app://chat/42","chat_url":"app://chat/42","chat_id":"42"},"android":{"channel_id":"messages"},"apns":{"badge":3,"sound":"default","thread_id":"messages"}}"""
    }
}
