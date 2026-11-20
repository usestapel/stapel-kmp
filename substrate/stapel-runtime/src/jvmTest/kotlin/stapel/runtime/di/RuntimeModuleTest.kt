package stapel.runtime.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import stapel.di.StapelQualifiers
import stapel.di.stapelKoin
import stapel.runtime.AppLifecycle
import stapel.runtime.Connectivity
import stapel.runtime.IdGenerator
import stapel.runtime.InMemoryLocalePreferenceStorage
import stapel.runtime.LocalePreference
import stapel.runtime.LocalePreferenceStorage
import stapel.runtime.LocaleProvider
import stapel.runtime.NoopLogger
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import stapel.runtime.StapelLogger
import stapel.runtime.TimeZoneProvider
import org.koin.dsl.module
import stapel.result.text.LocaleTag
import java.util.Locale
import kotlin.test.assertEquals
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

    @Test
    fun aLanguageChosenSignedOutIsTheAppLocaleFromTheStartAndEveryChoiceIsStored() {
        val device = LocaleTag(Locale.getDefault().toLanguageTag())
        val storage = InMemoryLocalePreferenceStorage(initial = "ru")
        val app = stapelKoin(
            listOf(runtimeModule(PlatformContext(), NoopLogger)),
            overrides = listOf(module { single<LocalePreferenceStorage> { storage } }),
            global = false,
        )
        val koin = app.koin
        assertEquals("ru", koin.get<LocaleProvider>().locale.value.value, "the stored choice applies before anything else reads the locale")
        val preference = koin.get<LocalePreference>()
        assertEquals(LocaleTag("ru"), preference.chosen.value)

        preference.choose(LocaleTag("de"))
        assertEquals("de", koin.get<LocaleProvider>().locale.value.value)
        assertEquals("de", storage.read(), "stored for the next launch")

        preference.choose(null)
        assertEquals(device, koin.get<LocaleProvider>().locale.value, "cleared: the device's language again")
        assertEquals(null, storage.read())
        app.close()

        val fresh = stapelKoin(listOf(runtimeModule(PlatformContext(), NoopLogger)), global = false)
        assertEquals(device, fresh.koin.get<LocaleProvider>().locale.value, "nothing stored: the device's language")
        fresh.close()
    }
}
