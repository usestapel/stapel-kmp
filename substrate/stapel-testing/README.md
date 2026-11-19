# stapel-testing

Deterministic fakes of the substrate seams, for test source sets of substrate modules, bricks and apps.

```kotlin
commonTest.dependencies { implementation("io.github.usestapel:stapel-testing") }
```

```kotlin
@Test fun graph() = runTest {
    val app = stapelKoin(
        modules = listOf(runtimeModule(platform), myModule()),
        overrides = listOf(testOverridesModule(backgroundScope, TestDispatchers(StandardTestDispatcher(testScheduler)))),
        global = false,
    )
}
```

- `TestClock`, `TestScope.virtualTimeClock()`, `TestDispatchers`, `SequentialIdGenerator`.
- `RecordingLogger` (with `contains(secret)` to prove a secret never reached the log).
- `FakeConnectivity`, `FixedLocaleProvider`, `FixedTimeZoneProvider`, `FakeAppLifecycle`.
- `testEnvironment()`, `testClientInfo()`; `stapel.testing.di.testOverridesModule(...)`.

See [MODULE.md](MODULE.md).
