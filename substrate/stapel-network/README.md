# stapel-network

The `ApiTransport` generated Stapel clients call: one recover-once pipeline above a
Ktor engine (OkHttp on Android and desktop, Darwin on iOS). Bearer auth, never a
cookie; refresh, step-up verification and capability plug-ins each turn a failure
into at most one retry; one transport retry policy; the Stapel error envelope
decoded into `stapel-result` types.

Coordinates: `io.github.usestapel:stapel-network` (provisional group).

```kotlin
val deps = NetworkDependencies(environment, clock = systemClock(), ids = randomIdGenerator())
val api: ApiTransport = StapelTransport(deps, authenticator = session.authenticator)
val auth = AuthApiClient(api)            // generated
when (val me = auth.getMe()) {
    is StapelResult.Ok -> show(me.value)
    is StapelResult.Err -> show(me.error)
}
// With Koin: networkModule(environment) — binds ApiTransport and the bare transport.
```
