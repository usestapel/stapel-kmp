package stapel.push.firebase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import stapel.push.DevicePlatform
import stapel.push.PushError
import stapel.push.PushEvent
import stapel.push.PushMessage
import stapel.push.PushPermission
import stapel.push.PushProvider
import stapel.push.ProviderAvailability
import stapel.push.testing.PushProviderContract
import stapel.push.testing.PushProviderHarness
import stapel.result.StapelResult
import stapel.testing.TestDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * What the Swift bridge does, transliterated: the OS permission, an FCM token that changes after
 * delete, notifications buffered until a listener exists. Records every call.
 */
internal class RecordingBridge(private val configured: Boolean = true) : FirebasePushBridge {
    val calls = mutableListOf<String>()
    var os = "notDetermined"
    var answer = "granted"
    var tokenError: String? = null
    var answerToken = true
    private var serial = 0
    private var current: String? = null
    private var listener: FirebasePushListener? = null
    private val pending = mutableListOf<(FirebasePushListener) -> Unit>()

    override fun configured(): Boolean = configured.also { calls += "configured" }

    override fun permission(completion: (status: String) -> Unit) {
        calls += "permission"
        completion(os)
    }

    override fun requestPermission(provisional: Boolean, completion: (status: String) -> Unit) {
        calls += "request:$provisional"
        if (os == "notDetermined") os = if (provisional) "provisional" else answer
        completion(os)
    }

    override fun token(completion: (token: String?, error: String?) -> Unit) {
        calls += "token"
        if (!answerToken) return
        tokenError?.let { return completion(null, it) }
        completion(current ?: "fcm-${++serial}".also { current = it }, null)
    }

    override fun deleteToken(completion: (error: String?) -> Unit) {
        calls += "delete"
        current = null
        completion(null)
    }

    override fun setListener(listener: FirebasePushListener?) {
        calls += "listener"
        this.listener = listener
        if (listener != null) {
            pending.forEach { it(listener) }
            pending.clear()
        }
    }

    override fun setForegroundPresentation(mode: String) {
        calls += "foreground:$mode"
    }

    override fun ensureChannel(id: String, name: String, importance: String) {
        calls += "channel:$id:$importance"
    }

    fun rotate() {
        val token = "fcm-${++serial}".also { current = it }
        deliver { it.onToken(token) }
    }

    fun message(m: PushMessage, foreground: Boolean) = deliver { it.onMessage(m.id, m.title, m.body, m.data, foreground) }

    fun tap(m: PushMessage) = deliver { it.onOpened(m.id, m.title, m.body, m.data, null) }

    private fun deliver(event: (FirebasePushListener) -> Unit) {
        listener?.let(event) ?: pending.add(event)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FirebasePushProviderTest {
    private fun TestScope.provider(bridge: FirebasePushBridge?, platform: DevicePlatform = DevicePlatform.Ios): PushProvider =
        firebasePushProvider(bridge, platform, TestDispatchers(StandardTestDispatcher(testScheduler)))

    private inner class Harness(scope: TestScope, val bridge: RecordingBridge = RecordingBridge()) : PushProviderHarness {
        override val provider: PushProvider = scope.provider(bridge)

        override suspend fun grant() {
            bridge.os = "granted"
        }

        override suspend fun rotate() = bridge.rotate()

        override suspend fun deliver(message: PushMessage, foreground: Boolean) = bridge.message(message, foreground)

        override suspend fun tap(message: PushMessage) = bridge.tap(message)
    }

    @Test
    fun passesTheProviderContractOverTheBridge() = runTest {
        PushProviderContract.verify { Harness(this) }
    }

    @Test
    fun withoutAConfigurationFileItIsAnHonestNoOp() = runTest {
        val bridge = RecordingBridge(configured = false)
        val provider = provider(bridge)
        assertEquals(ProviderAvailability.Unconfigured("GoogleService-Info.plist"), provider.availability)
        PushProviderContract.verifyUnavailable(provider)
        assertEquals(listOf("configured"), bridge.calls, "an unconfigured bridge was called beyond configured()")
        assertEquals(
            ProviderAvailability.Unconfigured("google-services.json"),
            provider(RecordingBridge(configured = false), DevicePlatform.Android).availability,
        )
    }

    @Test
    fun withoutABridgeItIsUnsupported() = runTest {
        val provider = provider(null)
        assertIs<ProviderAvailability.Unsupported>(provider.availability)
        PushProviderContract.verifyUnavailable(provider)
    }

    @Test
    fun theLaunchNotificationTappedBeforeKotlinStartedIsDeliveredOnce() = runTest {
        val bridge = RecordingBridge()
        bridge.tap(PushMessage("m1", "T", "B", mapOf("url" to "app://x"), "firebase"))
        val provider = provider(bridge)
        val first = provider.events.first()
        assertIs<PushEvent.Opened>(first)
        assertEquals("app://x", first.message.data["url"])
        assertEquals("firebase", first.message.provider)
    }

    @Test
    fun permissionWordsAndProvisional() = runTest {
        val bridge = RecordingBridge()
        val provider = provider(bridge)
        assertEquals(PushPermission.NotDetermined, provider.permission())
        assertEquals(PushPermission.Provisional, provider.requestPermission(provisional = true))
        assertTrue("request:true" in bridge.calls)
        bridge.os = "something-new"
        assertEquals(PushPermission.Denied, provider.permission(), "an unknown word must never read as granted")
        bridge.os = "granted"
        assertEquals(PushPermission.Granted, provider.permission())
    }

    @Test
    fun aTokenThatNeverComesIsTransientNotAHang() = runTest {
        val bridge = RecordingBridge().apply { answerToken = false }
        val provider = provider(bridge)
        val result = async { provider.token() }
        runCurrent()
        advanceTimeBy(TOKEN_BUDGET + 1.seconds)
        val err = assertIs<StapelResult.Err<PushError>>(result.await())
        assertIs<PushError.Transient>(err.error)
        assertTrue(currentTime <= (TOKEN_BUDGET + 1.seconds).inWholeMilliseconds, "answered only at virtual ${currentTime} ms: no budget")
    }

    @Test
    fun aTokenErrorIsTransientAndNeverCarriesAToken() = runTest {
        val bridge = RecordingBridge().apply { tokenError = "no APNs token specified" }
        val err = assertIs<StapelResult.Err<PushError>>(provider(bridge).token())
        assertEquals(PushError.Transient("no APNs token specified"), err.error)
    }

    @Test
    fun presentationAndChannelsReachTheBridgeInItsWords() = runTest {
        val bridge = RecordingBridge()
        val provider = provider(bridge, DevicePlatform.Android)
        provider.setForegroundPresentation(stapel.push.ForegroundPresentation.InApp)
        provider.ensureChannels(listOf(stapel.push.PushChannelSpec("messages", "Messages", stapel.push.Importance.High)))
        assertTrue("foreground:inApp" in bridge.calls)
        assertTrue("channel:messages:high" in bridge.calls)
    }
}
