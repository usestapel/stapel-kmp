package stapel.runtime

/** Desktop: in memory for the process (an app that wants it across launches binds its own storage). */
public actual fun platformLocalePreferenceStorage(platform: PlatformContext): LocalePreferenceStorage =
    InMemoryLocalePreferenceStorage()
