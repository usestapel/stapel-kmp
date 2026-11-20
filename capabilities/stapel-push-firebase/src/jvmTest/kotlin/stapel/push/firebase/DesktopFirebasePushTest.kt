package stapel.push.firebase

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import stapel.push.ProviderAvailability
import stapel.push.testing.PushProviderContract
import stapel.testing.TestDispatchers
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopFirebasePushTest {
    @Test
    fun desktopIsUnsupportedAndNeverThrows() = runTest {
        val provider = desktopFirebasePushProvider(TestDispatchers(StandardTestDispatcher(testScheduler)))
        assertEquals(ProviderAvailability.Unsupported("desktop"), provider.availability)
        assertEquals(null, provider.platform)
        PushProviderContract.verifyUnavailable(provider)
    }
}
