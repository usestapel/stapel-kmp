# stapel-testing — module map

Rank 6 substrate. A test-only artifact: consumers depend on it from test source sets, never from
main code.

## What is here now

| Fake | Replaces |
|---|---|
| `TestClock`, `virtualTimeClock()` | `kotlin.time.Clock` |
| `TestDispatchers` | `StapelDispatchers` (all three on one `TestDispatcher`) |
| `SequentialIdGenerator` | `IdGenerator` (UUID-shaped, deterministic) |
| `RecordingLogger` | `StapelLogger` |
| `FakeConnectivity`, `FixedLocaleProvider`, `FixedTimeZoneProvider`, `FakeAppLifecycle` | the runtime observers |
| `testEnvironment()`, `testClientInfo()` | `StapelEnvironment`, `ClientInfo` |
| `testOverridesModule(...)` | every binding of `runtimeModule`, as a Koin override module |

## Still to come (blocked on neighbours)

The network and session fakes of ARCH-CORE §6.4 — `FakeTransport` (with `enqueueEnvelope`),
`FakeApiTransport` for presenter tests, `InMemoryTokenVault` re-export, `wireFixture(name)` — land
here once `stapel-network` / `stapel-session` / `stapel-wire` are on `main`.

## Rule

"Mock the wire, not the module": substrate and brick tests feed real envelope bodies through the
real pipeline; whole-API fakes are for UI and presenter tests only.
