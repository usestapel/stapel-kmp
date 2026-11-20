package stapel.push.testing

import kotlinx.coroutines.test.runTest
import stapel.push.DevicePlatform
import stapel.push.PushMessage
import stapel.push.PushProvider
import stapel.push.ProviderAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FakesContractTest {
    private class FakeHarness(override val provider: FakePushProvider = FakePushProvider()) : PushProviderHarness {
        override suspend fun grant() {
            provider.osPermission = stapel.push.PushPermission.Granted
        }

        override suspend fun rotate() {
            provider.rotate()
        }

        override suspend fun deliver(message: PushMessage, foreground: Boolean) = provider.receive(message, foreground)

        override suspend fun tap(message: PushMessage) = provider.open(message)
    }

    @Test
    fun theFakeProviderPassesTheContract() = runTest {
        assertEquals(PushProviderContract.caseNames.size, PushProviderContract.verify { FakeHarness() })
    }

    @Test
    fun anUnconfiguredFakePassesTheUnavailableContract() = runTest {
        PushProviderContract.verifyUnavailable(FakePushProvider(ProviderAvailability.Unconfigured("no config"), platform = null))
    }

    @Test
    fun aProviderThatKeepsItsTokenAfterDeleteFailsTheContract() = runTest {
        // Inversion: the suite sees its subject.
        class Sticky(val inner: FakePushProvider = FakePushProvider()) : PushProvider by inner {
            override suspend fun deleteToken() = stapel.result.StapelResult.Ok(Unit)
        }
        val error = assertFailsWith<AssertionError> {
            PushProviderContract.verify {
                val sticky = Sticky()
                object : PushProviderHarness {
                    override val provider: PushProvider = sticky
                    override suspend fun grant() { sticky.inner.osPermission = stapel.push.PushPermission.Granted }
                    override suspend fun rotate() { sticky.inner.rotate() }
                    override suspend fun deliver(message: PushMessage, foreground: Boolean) = sticky.inner.receive(message, foreground)
                    override suspend fun tap(message: PushMessage) = sticky.inner.open(message)
                }
            }
        }
        assertTrue("deleteToken: the next token is a new one" in error.message.orEmpty(), error.message)
    }

    @Test
    fun aProviderThatThrowsWhenUnconfiguredFailsTheContract() = runTest {
        class Throwing(val inner: FakePushProvider = FakePushProvider(ProviderAvailability.Unconfigured("x"), DevicePlatform.Ios)) :
            PushProvider by inner {
            override suspend fun token() = throw IllegalStateException("FirebaseApp is not configured")
        }
        val error = assertFailsWith<AssertionError> { PushProviderContract.verifyUnavailable(Throwing()) }
        assertTrue("token is Unavailable" in error.message.orEmpty(), error.message)
    }
}
