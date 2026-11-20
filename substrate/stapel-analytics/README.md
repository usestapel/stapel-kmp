# stapel-analytics

The analytics hub of a Stapel app. Bricks and presenters keep emitting through
`stapel.runtime.Analytics` (`track` / `page` / `identify`, and `FlowMachine`'s `flow.<id>.<step>`);
this artifact implements it with a consent gate, a PII guard, a durable queue and fan-out to
`AnalyticsSink`s that provider artifacts contribute (`stapel-analytics-firebase`,
`stapel-analytics-appmetrica`, `stapel-analytics-collector`, or the built-in `LogSink`).

A project without analytics does not depend on this artifact: nothing binds `Analytics` and every
consumer gets `Analytics.Noop`. This artifact itself names no vendor and pulls in no vendor SDK.

Coordinates: `io.github.usestapel:stapel-analytics` (group provisional until the first publish).

```kotlin
val app = stapelApp(platform) {
    environment(AppEnvironment)
    modules(
        analyticsModule(AnalyticsOptions(consentDefault = ConsentDefault.OptIn, registry = APP_EVENTS)),
        firebaseAnalyticsModule(/* platform arguments */),   // contributes an AnalyticsSink
    )
}
val control = app.koin.get<AnalyticsControl>()
control.setConsent(Consent(ConsentState.Granted))          // from the app's consent sheet
app.koin.get<Analytics>().identify(sha256Hex(accountId))   // on sign-in; identify(null) on sign-out
```

Defaults: opt-in consent (nothing leaves the device and vendor collection is switched off until
`setConsent(Granted)`), `PiiGuard.Strip`, batches of 20 every 10 s, 5 attempts with backoff from
500 ms, at most 500 undelivered events, a new session after 30 minutes in the background.
