# stapel-analytics-collector

The first-party analytics sink: delivers the hub's batches to `POST /analytics/api/v1/events` of
the project's own `stapel-analytics` brick (pinned contract 0.8.3, `contracts/analytics`), over the
bare transport. The only sink that also works on desktop; the one that feeds the brick's funnels
(`flow.<id>.<step>` events).

Coordinates: `io.github.usestapel:stapel-analytics-collector` (group provisional until the first publish).

```kotlin
stapelApp(platform) {
    environment(AppEnvironment)
    modules(
        analyticsModule(),
        analyticsCollectorModule(CollectorOptions(source = "android", writeKey = AppEnvironment.analyticsWriteKey)),
    )
}
```

The brick's whole client API is generated into `stapel.analytics.collector.api` from the pinned
contract (`./gradlew :stapel-analytics-collector:stapelGenerate`, drift-gated by
`stapelGenerateCheck`).
