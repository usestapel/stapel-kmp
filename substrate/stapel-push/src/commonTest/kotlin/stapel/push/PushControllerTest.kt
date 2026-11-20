package stapel.push

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import stapel.push.testing.FakeDeviceRegistry
import stapel.push.testing.FakePushProvider
import stapel.push.testing.pushMessage
import stapel.runtime.AppPhase
import stapel.session.InstallationIdentity
import stapel.session.RefreshOutcome
import stapel.session.SessionDependencies
import stapel.session.SessionEnd
import stapel.session.SessionKind
import stapel.session.SessionManager
import stapel.session.SessionState
import stapel.session.TokenPair
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.StoredSession
import stapel.testing.FakeAppLifecycle
import stapel.testing.RecordingLogger
import stapel.testing.TestClock
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The token lifecycle of [PushController] (G-P2) and tap routing (G-P3), against the fakes. */
@OptIn(ExperimentalCoroutinesApi::class)
class PushControllerTest {
    private class Harness(
        val scope: TestScope,
        signedInAs: String? = "alice",
        guest: Boolean = false,
        record: PushRecord = PushRecord(),
        platform: DevicePlatform = DevicePlatform.Android,
        osPermission: PushPermission = PushPermission.NotDetermined,
        val options: PushOptions = PushOptions(),
        var rationaleAnswer: Boolean = true,
        val provider: FakePushProvider = FakePushProvider(platform = platform),
    ) {
        val journal: MutableList<String> = provider.log
        val sessions: SessionManager = SessionManager(
            SessionDependencies(
                InMemoryTokenVault(
                    signedInAs?.let { StoredSession(tokensOf(it), if (guest) SessionKind.Anonymous else SessionKind.Authenticated) },
                ),
                scope.backgroundScope,
                TestClock(),
            ),
            { RefreshOutcome.Transient("offline") },
        )
        val registry = FakeDeviceRegistry(
            account = { (sessions.state.value as? SessionState.Authenticated)?.accountId },
            journal = journal,
        )
        val store = InMemoryPushStore(record)
        val lifecycle = FakeAppLifecycle(AppPhase.Foreground)
        val logger = RecordingLogger()
        var rationaleShown = 0
        val opened = mutableListOf<PushMessage>()
        lateinit var controller: PushController

        init {
            provider.osPermission = osPermission
        }

        suspend fun start(): Harness {
            sessions.restore()
            controller = PushController(
                PushDependencies(
                    provider = provider,
                    registry = registry,
                    sessions = sessions,
                    identity = object : InstallationIdentity {
                        override suspend fun deviceId() = INSTALLATION
                    },
                    store = store,
                    scope = scope.backgroundScope,
                    lifecycle = lifecycle,
                    rationale = PushRationale {
                        rationaleShown++
                        rationaleAnswer
                    },
                    openListeners = listOf(PushOpenListener { opened += it }),
                    logger = logger,
                ),
                options,
            )
            scope.runCurrent()
            return this
        }

        suspend fun signIn(account: String) {
            sessions.adopt(tokensOf(account), SessionKind.Authenticated)
            scope.runCurrent()
        }

        fun rowsOf(account: String) = registry.rows.filter { it.account == account }

        val registrations get() = journal.filter { it.startsWith("register:") }
    }

    // --- permission: never at launch, rationale first -------------------------------------------

    @Test
    fun neverAsksAtLaunch() = runTest {
        val h = Harness(this).start()
        assertEquals(0, h.provider.prompts)
        assertFalse(h.journal.any { it.startsWith("request:") }, "asked at launch: ${h.journal}")
        assertEquals(0, h.rationaleShown)
        assertEquals(Registration.Off, h.controller.state.value.registration)
        assertEquals(PushPermission.NotDetermined, h.controller.state.value.permission)
    }

    @Test
    fun enableShowsTheRationaleThenThePromptThenRegisters() = runTest {
        val h = Harness(this).start()
        val state = h.controller.enable()
        assertEquals(1, h.rationaleShown)
        assertEquals(1, h.provider.prompts)
        assertEquals(Registration.Registered, state.registration)
        assertTrue(state.enabled)
        assertEquals(listOf(INSTALLATION), h.rowsOf("alice").map { it.installationId })
        assertTrue(h.store.record.enabled)
        assertTrue(h.logger.contains("stapel.push state registered"))
    }

    @Test
    fun aRationaleDeclinedKeepsTheOsPromptUnspent() = runTest {
        val h = Harness(this, rationaleAnswer = false).start()
        val state = h.controller.enable()
        assertEquals(1, h.rationaleShown)
        assertEquals(0, h.provider.prompts, "the one-shot OS prompt was spent on a person who said no")
        assertEquals(PushPermission.NotDetermined, state.permission)
        assertTrue(h.registrations.isEmpty())
    }

    @Test
    fun deniedByTheOsIsBlockedAndNothingIsRegistered() = runTest {
        val h = Harness(this).start()
        h.provider.osAnswer = PushPermission.Denied
        val state = h.controller.enable()
        assertEquals(PushPermission.Denied, state.permission)
        assertEquals(Registration.Blocked, state.registration)
        assertTrue(h.registrations.isEmpty())
        // Already denied: no rationale, no prompt on the next attempt.
        h.controller.enable()
        assertEquals(1, h.rationaleShown)
        assertEquals(1, h.provider.prompts)
    }

    @Test
    fun afterSignInOnIosIsProvisionalWithoutAPrompt() = runTest {
        val h = Harness(this, signedInAs = null, platform = DevicePlatform.Ios, options = PushOptions(ask = AskPolicy.AfterSignIn)).start()
        assertTrue(h.journal.none { it.startsWith("request:") }, "asked before sign-in")
        h.signIn("alice")
        assertEquals(0, h.provider.prompts)
        assertEquals(0, h.rationaleShown)
        assertEquals(PushPermission.Provisional, h.controller.state.value.permission)
        assertEquals(Registration.Registered, h.controller.state.value.registration)
    }

    @Test
    fun afterSignInOnAndroidShowsTheRationaleAndThePromptOnce() = runTest {
        val h = Harness(this, signedInAs = null, options = PushOptions(ask = AskPolicy.AfterSignIn)).start()
        h.signIn("alice")
        assertEquals(1, h.rationaleShown)
        assertEquals(1, h.provider.prompts)
        assertEquals(Registration.Registered, h.controller.state.value.registration)
    }

    @Test
    fun afterSignInDoesNotAskAPersonWhoTurnedPushOff() = runTest {
        val h = Harness(this, signedInAs = null, options = PushOptions(ask = AskPolicy.AfterSignIn), record = PushRecord(optedOut = true)).start()
        h.signIn("alice")
        assertEquals(0, h.rationaleShown)
        assertEquals(0, h.provider.prompts)
        assertTrue(h.registrations.isEmpty())
    }

    // --- registration follows the session ---------------------------------------------------------

    @Test
    fun registersOnEveryStartWhileSignedIn() = runTest {
        val record = PushRecord(enabled = true)
        val first = Harness(this, record = record, osPermission = PushPermission.Granted).start()
        assertEquals(1, first.registrations.size)
        assertEquals(Registration.Registered, first.controller.state.value.registration)
        // A second process start on the same installation registers again (the backend's last_seen).
        val second = Harness(this, record = first.store.record, osPermission = PushPermission.Granted).start()
        assertEquals(1, second.registrations.size)
    }

    @Test
    fun registersOnSignInAfterIntentWasRecordedSignedOut() = runTest {
        val h = Harness(this, signedInAs = null).start()
        assertEquals(Registration.Off, h.controller.enable().registration)
        assertTrue(h.controller.state.value.enabled)
        assertTrue(h.registrations.isEmpty())
        h.signIn("alice")
        assertEquals(Registration.Registered, h.controller.state.value.registration)
        assertEquals(1, h.rowsOf("alice").size)
    }

    @Test
    fun aGuestSessionNeverRegisters() = runTest {
        val h = Harness(this, guest = true, record = PushRecord(enabled = true), osPermission = PushPermission.Granted).start()
        h.controller.enable()
        assertTrue(h.registrations.isEmpty(), "a guest registered: ${h.journal}")
        assertEquals(Registration.Off, h.controller.state.value.registration)
        h.signIn("alice")
        assertEquals(1, h.rowsOf("alice").size)
    }

    @Test
    fun aRotatedTokenIsRegistered() = runTest {
        val h = Harness(this, record = PushRecord(enabled = true), osPermission = PushPermission.Granted).start()
        val before = h.registry.rows.single().fingerprint
        val rotated = h.provider.rotate()
        runCurrent()
        assertEquals(rotated.fingerprint, h.registry.rows.single().fingerprint)
        assertNotEquals(before, rotated.fingerprint)
        assertEquals(rotated.fingerprint, h.store.record.registeredFingerprint)
    }

    // --- session end ------------------------------------------------------------------------------

    @Test
    fun explicitSignOutUnregistersWithTheBearerThenDeletesTheToken() = runTest {
        val h = Harness(this, record = PushRecord(enabled = true), osPermission = PushPermission.Granted).start()
        assertEquals(1, h.rowsOf("alice").size)
        h.journal.clear()
        h.sessions.logout()
        runCurrent()
        assertEquals(listOf("unregister:$INSTALLATION", "delete"), h.journal.filter { it == "delete" || it.startsWith("unregister") })
        assertTrue(h.registry.rows.isEmpty(), "the row survived the sign-out: ${h.registry.rows}")
        assertEquals(1, h.journal.count { it == "delete" }, "the token is deleted once per session end")
        assertEquals(null, h.store.record.registeredFingerprint)
        assertTrue(h.store.record.enabled, "the person's choice survives sign-out")
        assertEquals(Registration.Off, h.controller.state.value.registration)
    }

    @Test
    fun aSessionEndingWithoutASignOutStillDeletesTheToken() = runTest {
        for (cause in listOf(SessionEnd.Revoked, SessionEnd.Expired, SessionEnd.StorageLost)) {
            val h = Harness(this, record = PushRecord(enabled = true), osPermission = PushPermission.Granted).start()
            val token = h.provider.currentToken
            h.journal.clear()
            h.sessions.sessionLost(cause)
            runCurrent()
            assertEquals(listOf("delete"), h.journal.filter { it == "delete" || it.startsWith("unregister") }, "$cause: ${h.journal}")
            assertEquals(null, h.provider.currentToken, "$cause kept $token")
        }
    }

    @Test
    fun anotherAccountOnTheSameInstallationGetsAFreshTokenAndNoConflict() = runTest {
        val h = Harness(this, record = PushRecord(enabled = true), osPermission = PushPermission.Granted).start()
        val alice = h.registry.rows.single().fingerprint
        h.sessions.logout()
        h.signIn("bob")
        val bob = h.rowsOf("bob").single().fingerprint
        assertNotEquals(alice, bob, "the next account must get a new token")
        assertTrue(h.rowsOf("alice").isEmpty())
        assertEquals(Registration.Registered, h.controller.state.value.registration)
    }

    @Test
    fun aRegistrationLeftByADeadProcessForAnotherAccountIsNotReused() = runTest {
        // The process died mid-sign-out: the record still names alice; bob's session starts.
        val h = Harness(
            this,
            signedInAs = "bob",
            record = PushRecord(enabled = true, registeredFingerprint = PushToken("fake-token-1", TokenKind.Fcm, "fake").fingerprint, registeredAccount = "alice"),
            osPermission = PushPermission.Granted,
        ).start()
        val first = h.journal.indexOf("delete")
        val minted = h.journal.indexOf("token")
        assertTrue(first in 0 until minted, "no delete before minting for bob: ${h.journal}")
        assertEquals("bob", h.registry.rows.single().account)
    }

    // --- registry refusals ------------------------------------------------------------------------

    @Test
    fun aConflictIsAnsweredWithOneNewTokenAndOneRetry() = runTest {
        val h = Harness(this, osPermission = PushPermission.Granted)
        h.registry.rows += FakeDeviceRegistry.Row("mallory", "other-install", PushToken("fake-token-1", TokenKind.Fcm, "fake").fingerprint)
        h.start()
        assertEquals(Registration.Registered, h.controller.enable().registration)
        assertEquals(2, h.registrations.size)
        assertEquals("alice", h.registry.rows.last().account)
    }

    @Test
    fun aSecondConflictFailsInsteadOfLooping() = runTest {
        val h = Harness(this, osPermission = PushPermission.Granted)
        for (n in 1..3) h.registry.rows += FakeDeviceRegistry.Row("mallory", "other-install", PushToken("fake-token-$n", TokenKind.Fcm, "fake").fingerprint)
        h.start()
        assertEquals(Registration.Failed, h.controller.enable().registration)
        assertEquals(2, h.registrations.size)
    }

    @Test
    fun anInactiveTokenIsReplacedOnStart() = runTest {
        val first = Harness(this, record = PushRecord(enabled = true), osPermission = PushPermission.Granted).start()
        val dead = first.provider.currentToken!!
        first.registry.deactivate(dead)
        // Same installation, same provider state, next process start.
        val second = Harness(this, record = first.store.record, osPermission = PushPermission.Granted, provider = first.provider)
        second.registry.rows += first.registry.rows
        second.start()
        assertNotEquals(dead.fingerprint, second.registry.rows.single().fingerprint)
        assertTrue(second.registry.rows.single().active)
    }

    // --- the person's choice and the OS ---------------------------------------------------------------

    @Test
    fun disableUnregistersDeletesAndRemembers() = runTest {
        val h = Harness(this, record = PushRecord(enabled = true), osPermission = PushPermission.Granted).start()
        h.journal.clear()
        val state = h.controller.disable()
        assertFalse(state.enabled)
        assertEquals(Registration.Off, state.registration)
        assertEquals(listOf("unregister:$INSTALLATION", "delete"), h.journal.filter { it == "delete" || it.startsWith("unregister") })
        assertTrue(h.registry.rows.isEmpty())
        assertTrue(h.store.record.optedOut)
        // Next start while signed in registers nothing.
        val next = Harness(this, record = h.store.record, osPermission = PushPermission.Granted).start()
        assertTrue(next.registrations.isEmpty())
    }

    @Test
    fun permissionRevokedInTheSettingsUnregistersAndAReGrantRestores() = runTest {
        val h = Harness(this, record = PushRecord(enabled = true), osPermission = PushPermission.Granted).start()
        h.lifecycle.set(AppPhase.Background)
        runCurrent()
        h.provider.osPermission = PushPermission.Denied
        h.lifecycle.set(AppPhase.Foreground)
        runCurrent()
        assertTrue(h.registry.rows.isEmpty(), "a device that cannot show notifications stays registered")
        assertEquals(Registration.Blocked, h.controller.state.value.registration)
        assertTrue(h.controller.state.value.enabled)
        h.lifecycle.set(AppPhase.Background)
        runCurrent()
        h.provider.osPermission = PushPermission.Granted
        h.lifecycle.set(AppPhase.Foreground)
        runCurrent()
        assertEquals(1, h.registry.rows.size)
        assertEquals(Registration.Registered, h.controller.state.value.registration)
    }

    // --- taps and presentation (G-P3) ------------------------------------------------------------------

    @Test
    fun aTapBeforeAttachIsKeptAndOpenedOnce() = runTest {
        val h = Harness(this).start()
        h.provider.open(pushMessage(mapOf("url" to "app://orders/7")))
        runCurrent()
        val links = mutableListOf<String>()
        val handle = h.controller.attach { links += it; true }
        runCurrent()
        assertEquals(listOf("app://orders/7"), links)
        handle.close()
        h.controller.attach { links += it; true }
        runCurrent()
        assertEquals(1, links.size, "the launch notification was opened twice")
        assertTrue(h.logger.contains("stapel.push opened test_message link=opened"))
        assertEquals(1, h.opened.size, "open listeners (mark read) run once per tap")
    }

    @Test
    fun aRefusedLinkFallsBackAndAnAbsentLinkToo() = runTest {
        val h = Harness(this, options = PushOptions(routing = PushRouting(fallbackUri = "app://notifications"))).start()
        val links = mutableListOf<String>()
        h.controller.attach { uri -> links += uri; uri.startsWith("app://") }
        h.provider.open(pushMessage(mapOf("url" to "https://evil.example/phish")))
        h.provider.open(pushMessage())
        runCurrent()
        assertEquals(listOf("https://evil.example/phish", "app://notifications", "app://notifications"), links)
        assertTrue(h.logger.contains("link=refused"))
        assertTrue(h.logger.contains("link=none"))
    }

    @Test
    fun linkKeysAreReadInOrder() = runTest {
        val h = Harness(this, options = PushOptions(routing = PushRouting(linkKeys = listOf("url", "chat_url")))).start()
        val links = mutableListOf<String>()
        h.controller.attach { links += it; true }
        h.provider.open(pushMessage(mapOf("chat_url" to "app://chat/1", "url" to "app://chat/canonical")))
        h.provider.open(pushMessage(mapOf("chat_url" to "app://chat/2")))
        runCurrent()
        assertEquals(listOf("app://chat/canonical", "app://chat/2"), links)
    }

    @Test
    fun receivedIsLoggedAndForwardedAndPresentationAndChannelsReachTheProvider() = runTest {
        val channels = listOf(PushChannelSpec("messages", "Messages", Importance.High), PushChannelSpec("system", "System"))
        val h = Harness(this, options = PushOptions(foreground = ForegroundPresentation.InApp, routing = PushRouting(channels = channels))).start()
        assertEquals(ForegroundPresentation.InApp, h.provider.foreground)
        assertEquals(channels, h.provider.channels)
        val seen = mutableListOf<PushEvent>()
        backgroundScope.launchCollect(h.controller) { seen += it }
        runCurrent()
        h.provider.receive(pushMessage(mapOf("notification_type" to "new_message")), foreground = true)
        runCurrent()
        assertTrue(h.logger.contains("stapel.push received new_message fg=true"))
        assertEquals(1, seen.size)
    }

    @Test
    fun anUnconfiguredProviderIsAWorkingNoOpWithANamedMarker() = runTest {
        val h = Harness(this, provider = FakePushProvider(ProviderAvailability.Unconfigured("google-services.json"))).start()
        assertTrue(h.logger.contains("stapel.push unavailable unconfigured (google-services.json)"))
        val state = h.controller.enable()
        assertEquals(PushPermission.Unsupported, state.permission)
        assertEquals(Registration.Off, state.registration)
        h.sessions.logout()
        assertTrue(h.registrations.isEmpty())
    }

    private companion object {
        const val INSTALLATION = "install-0123456789"

        fun tokensOf(account: String): TokenPair {
            val payload = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
                .encode("{\"user_id\":\"$account\"}".encodeToByteArray())
            return TokenPair("eyJhbGciOiJIUzI1NiJ9.$payload.sig-$account", "refresh-$account")
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchCollect(controller: PushController, block: (PushEvent) -> Unit) {
    launch { controller.events.collect { block(it) } }
}
