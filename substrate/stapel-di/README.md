# stapel-di

Koin conventions shared by every stapel-kmp library (ruling R6).

```kotlin
implementation("io.github.usestapel:stapel-di")   // version from stapel-bom
```

```kotlin
fun startApp(platform: PlatformContext) = stapelKoin(
    modules = listOf(runtimeModule(platform), networkModule(env), sessionModule(), authModule()),
)
```

- `stapelKoin(modules, overrides, global)` — duplicate bindings among `modules` fail at start;
  `overrides` is the only place a redefinition is legal.
- `Module.contribute<T>(key) { … }` — one element of a `getAll<T>()` multi-binding.
- `Module.sessionScoped { scoped<…> { … } }` + `Koin.bindSessionScope(generations, scope)` — objects
  that live exactly as long as one signed-in session.
- `StapelQualifiers` — `AppScope`, `SessionScope`, `module(key)`, `contribution(key)`.

See [MODULE.md](MODULE.md).
