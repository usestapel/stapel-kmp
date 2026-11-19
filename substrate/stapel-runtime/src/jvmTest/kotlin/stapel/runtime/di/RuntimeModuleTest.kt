package stapel.runtime.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import stapel.di.StapelQualifiers
import stapel.di.stapelKoin
import stapel.runtime.AppLifecycle
import stapel.runtime.Connectivity
import stapel.runtime.IdGenerator
import stapel.runtime.LocaleProvider
import stapel.runtime.NoopLogger
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import stapel.runtime.StapelLogger
import stapel.runtime.TimeZoneProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Clock

class RuntimeModuleTest {
    @Test
    fun everyPublicBindingResolvesAndTheAppScopeDiesWithKoin() {
        val app = stapelKoin(listOf(runtimeModule(PlatformContext(), NoopLogger)), global = false)
        val koin = app.koin
        koin.get<StapelDispatchers>()
        koin.get<Clock>()
        koin.get<IdGenerator>()
        koin.get<LocaleProvider>()
        koin.get<TimeZoneProvider>()
        koin.get<AppLifecycle>()
        koin.get<Connectivity>()
        assertSame(NoopLogger, koin.get<StapelLogger>())
        val scope = koin.get<CoroutineScope>(StapelQualifiers.AppScope)
        assertTrue(scope.isActive)
        app.close()
        assertFalse(scope.isActive, "closing Koin cancels the app scope")
    }
}
