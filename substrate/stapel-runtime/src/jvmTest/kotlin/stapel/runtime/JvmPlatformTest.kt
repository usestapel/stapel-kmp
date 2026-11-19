package stapel.runtime

import kotlinx.coroutines.test.runTest
import java.util.Locale
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class JvmPlatformTest {
    private val platform = PlatformContext()

    @Test
    fun localeAndTimeZoneComeFromTheJvmDefaults() {
        assertEquals(Locale.getDefault().toLanguageTag(), platformLocaleProvider(platform).locale.value.value)
        assertEquals(TimeZone.getDefault().id, platformTimeZoneProvider(platform).timeZoneId.value)
    }

    @Test
    fun desktopIsAlwaysForegroundAndConnectivityStartsUnknown() = runTest {
        assertEquals(AppPhase.Foreground, platformAppLifecycle(platform, backgroundScope).phase.value)
        val c = platformConnectivity(platform, backgroundScope)
        assertEquals(NetworkStatus.Unknown, c.status.value)
        c.reportUnreachable()
        assertEquals(NetworkStatus.Offline, c.status.value, "desktop has no observer: reports drive it")
    }

    @Test
    fun loggerFiltersByLevel() {
        val logger = platformLogger(LogLevel.Warn)
        assertEquals(false, logger.isEnabled(LogLevel.Info, "t"))
        assertEquals(true, logger.isEnabled(LogLevel.Error, "t"))
    }
}
