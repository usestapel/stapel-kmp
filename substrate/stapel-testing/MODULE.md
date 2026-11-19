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
| `FakeTransport` | `stapel-network`'s `HttpTransport`: scripted responses, envelopes, no-response failures, under the real pipeline |
| `FakeApiTransport` + `FakeAnswer` | `stapel-wire`'s `ApiTransport`, by operation id, errors built through each operation's own `ErrorDecoder` (presenter / UI tests only) |
| `testOverridesModule(...)` | every binding of `runtimeModule`, plus the `HttpTransport` of `networkModule` and the `TokenVault` of `sessionModule` when given |

`InMemoryTokenVault` is public in `stapel-session` itself and is not duplicated here.

## Not here

- `wireFixture(name)` (real response bodies captured from a scaffolded backend): the capture is
  CODEGEN/AUTH's; common code has no portable resource loading, so fixtures stay in the consuming
  module's test sources until a capture format exists.
- The network and session modules still carry private copies of a fake transport / clock in their
  own tests; moving them onto this module is their owners' change.

## Rule

"Mock the wire, not the module": substrate and brick tests feed real envelope bodies through the
real pipeline; whole-API fakes are for UI and presenter tests only.
