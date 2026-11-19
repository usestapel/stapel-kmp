package stapel.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosPlatformTest {
    private val platform = PlatformContext()

    @Test
    fun localeAndTimeZoneAreRead() {
        val tag = platformLocaleProvider(platform).locale.value
        assertTrue(tag.value.isNotBlank() && '_' !in tag.value, tag.value)
        assertTrue(platformTimeZoneProvider(platform).timeZoneId.value.isNotBlank())
    }

    @Test
    fun connectivityIsOsObserved() = runTest {
        val c = platformConnectivity(platform, backgroundScope)
        val before = c.status.value
        c.reportUnreachable()
        assertEquals(before, c.status.value, "with NWPathMonitor a failed request does not mark offline")
        assertEquals(true, platformLogger(LogLevel.Debug).isEnabled(LogLevel.Info, "t"))
    }

    @Test
    fun watchStopsOnCancel() = runTest {
        val flow = MutableStateFlow(1)
        val handle = flow.watch(backgroundScope) {}
        handle.cancel()
    }
}
