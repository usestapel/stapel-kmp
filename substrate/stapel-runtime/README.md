# stapel-runtime

The platform seams every stapel-kmp artifact injects instead of reading ambient state.

```kotlin
implementation("io.github.usestapel:stapel-runtime")   // version from stapel-bom
```

```kotlin
// Android: PlatformContext(application); iOS / desktop: PlatformContext()
stapelKoin(modules = listOf(runtimeModule(PlatformContext(application)), /* … */))
```

- `StapelDispatchers` / `defaultDispatchers()`, `systemClock()`, `IdGenerator` / `randomIdGenerator()`.
- `LocaleProvider` (device locale + in-app override), `TimeZoneProvider`, `AppLifecycle`,
  `Connectivity` (OS observer where one exists, transport reports everywhere).
- `StapelLogger` / `platformLogger(minLevel)` / `NoopLogger`, `StapelTelemetry`.
- `Analytics` type seam (`Analytics.Noop`, `trackFlowStep`); the implementation is `stapel-analytics`.
- iOS: `Flow.watch(scope) { }` for Swift callers.
- `runtimeModule(platform, logger)` in `stapel.runtime.di`.

See [MODULE.md](MODULE.md).
