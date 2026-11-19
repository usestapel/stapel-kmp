# stapel-runtime — module map

Rank 1 substrate: `stapel-result`, `stapel-di`, coroutines.

## Seams

| Seam | Implemented by | Consumed by |
|---|---|---|
| `PlatformContext` | the app (`PlatformContext(application)` on Android, `PlatformContext()` elsewhere); Android code reads `PlatformContext.application` | every platform factory |
| `StapelDispatchers`, `Clock`, `IdGenerator` | `defaultDispatchers()`, `systemClock()`, `randomIdGenerator()`; tests: `stapel-testing` | network, session, cache |
| `LocaleProvider` | `platformLocaleProvider` | `Accept-Language`, error text, UI |
| `TimeZoneProvider` | `platformTimeZoneProvider` | formatters |
| `AppLifecycle` | `platformAppLifecycle` | cache polling, realtime |
| `Connectivity` | `platformConnectivity` | network (feeds `reportReachable/Unreachable`, short-circuits on `Offline`), cache |
| `StapelLogger`, `StapelTelemetry` | `platformLogger`, apps | everything |
| `Analytics` | `stapel-analytics` (not bound here) | flow machines |

## Platform actuals

| Seam | Android | iOS | Desktop |
|---|---|---|---|
| lifecycle | started-activity counter, starts `Background` | `UIApplication` notifications (create on main thread) | always `Foreground` |
| connectivity | default-network callback, online only when validated | `NWPathMonitor`, expensive = metered | no observer: reports drive it; an `Offline` mark decays to `Unknown` after 5 s so a sender that refuses to send while offline can recover |
| locale | `Configuration.locales[0]`, `ACTION_LOCALE_CHANGED` | `NSLocale.preferredLanguages`, change notification | `Locale.getDefault()` at start |
| logger | Logcat | `NSLog` | standard error |

The library manifest declares `ACCESS_NETWORK_STATE`.

## Invariants

- Outside this module, substrate code does not read `Dispatchers.*`, `Clock.System`, `Random.Default`
  or the platform locale (G-R1 — **not yet a build gate**: needs a source-scan task in `build-logic`).
- With an OS observer, one failed request never marks the device offline.
- `runtimeModule` does not bind `Analytics` or `StapelTelemetry` (binding a no-op would make the real
  implementation a duplicate binding).

## Not measured

- Android actuals run only on a device (no emulator image on the workstation): compiled, not executed.
- iOS lifecycle needs a running `UIApplication`; not executed in simulator unit tests.
