# stapel-profiles

Profiles domain library: the generated client of the `stapel-profiles` backend (`ProfilesApi`, models,
typed errors, cache keys) and the hand-written repositories (own profile, avatar, languages, people,
connections, phone numbers) and headless presenters (`stapel.profiles.presentation`). No Compose, no
platform SDK.

Coordinates: `io.github.usestapel:stapel-profiles` (provisional group).

```kotlin
// Koin: runtimeModule(platform), networkModule(env), sessionModule(), profilesModule() + a ManagedStapelCache binding.
val profiles: StapelProfiles = koin.get()

profiles.me.me().collect { /* Resource<MyProfile> */ }
profiles.me.update(ProfileUpdate(displayName = "Ada", theme = ThemePreference.Dark))
profiles.me.setAvatar(FilePart("me.jpg", "image/jpeg", size) { source })   // upload + one PATCH

val settings = ProfileSettingsPresenter(profiles, PresenterContext(scope))
settings.dispatch(ProfileSettingsIntent.SetLanguage("ru"))
```

End-to-end proof against a real backend: `bricks/profiles/e2e` (stand script + JVM suite).
