package stapel.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import stapel.result.text.LocaleTag

/**
 * The app language a person chose on this device without an account (a language picker shown signed
 * out), kept across launches. It applies from the first frame, before any sign-in: `runtimeModule()`
 * hands the stored choice to the [LocaleProvider] when it creates it.
 *
 * Merge rule with an account's own language (the profiles brick): signed in, a language set in the
 * profile wins and is shown; a profile without one is seeded from this choice once; signed out again,
 * this choice (or the device's language) applies. This preference is never written from the profile.
 */
public interface LocalePreference {
    /** The stored choice, or null (follow the device). */
    public val chosen: StateFlow<LocaleTag?>

    /** Stores [tag] (null clears it) and applies it to the app's [LocaleProvider] at once. */
    public fun choose(tag: LocaleTag?)
}

/** Where a [LocalePreference] keeps its one value: small, synchronous, read once at start. */
public interface LocalePreferenceStorage {
    /** The stored language tag, or null. */
    public fun read(): String?

    /** Stores [tag]; null removes it. */
    public fun write(tag: String?)
}

/** A [LocalePreferenceStorage] that forgets on restart: tests, and desktop (see [platformLocalePreferenceStorage]). */
public class InMemoryLocalePreferenceStorage(initial: String? = null) : LocalePreferenceStorage {
    private var value: String? = initial

    override fun read(): String? = value

    override fun write(tag: String?) {
        value = tag
    }
}

/**
 * The platform's storage: Android `SharedPreferences` (`stapel.locale`), iOS `NSUserDefaults`
 * (`stapel.locale.app`); desktop keeps it in memory for the process (a JVM app has no per-app storage
 * location the runtime knows of — an app binds its own [LocalePreferenceStorage] to persist it).
 */
public expect fun platformLocalePreferenceStorage(platform: PlatformContext): LocalePreferenceStorage

/** A [LocalePreference] over [storage] that applies every choice to [locale]. */
public class StoredLocalePreference(
    private val storage: LocalePreferenceStorage,
    private val locale: LocaleProvider,
) : LocalePreference {
    private val state = MutableStateFlow(storedLocaleTag(storage))

    override val chosen: StateFlow<LocaleTag?> = state.asStateFlow()

    override fun choose(tag: LocaleTag?) {
        val clean = tag?.takeIf { it.value.isNotBlank() }
        storage.write(clean?.value)
        state.value = clean
        locale.setAppLocale(clean)
    }
}

/** The choice in [storage], or null when there is none (or it is blank). */
public fun storedLocaleTag(storage: LocalePreferenceStorage): LocaleTag? =
    storage.read()?.takeIf { it.isNotBlank() }?.let(::LocaleTag)
