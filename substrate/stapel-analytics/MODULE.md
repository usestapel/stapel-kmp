# stapel-analytics — module map

- **Provides:** `AnalyticsHub` (implements `stapel.runtime.Analytics` + `AnalyticsControl`), the sink
  seam `AnalyticsSink` (`SinkAvailability`, `SinkReceipt`, `ConsentPurpose`), `Consent` /
  `ConsentDefault`, `AnalyticsOptions` / `BatchOptions`, `PiiGuard` + `looksLikePii`, `sha256Hex`,
  `AnalyticsStorage` (`FileAnalyticsStorage`, `InMemoryAnalyticsStorage`, `platformAnalyticsStorage`),
  `AnalyticsAnonymousId`, `LogSink`, and `di.analyticsModule(options)`.
- **Seams:** a provider contributes `contribute<AnalyticsSink>("analytics.<id>") { … }`; the app may
  bind `AnalyticsStorage` and `AnalyticsAnonymousId`. The hub never sees a raw account id: the app
  calls `identify(sha256Hex(accountId))` on sign-in and `identify(null)` on sign-out (which resets
  every sink). `stapel-app` calls `Analytics.page(<destination id>)` when the top destination
  changes, when an `Analytics` is bound.
- **Invariants (each has a test that fails when broken):** `setCollectionEnabled(false)` reaches
  every sink before anything else; nothing reaches a sink before consent covers its purposes;
  pending consent buffers at most `pendingBufferLimit` events (oldest dropped); denied drops memory
  and the stored queue and ignores later emits; a batch a sink took is never re-sent to it; events a
  sink rejected (`Partial`) are never re-sent; a throwing sink is `Retry`, dropped after
  `maxAttempts`, and never stops another sink; an unavailable sink is skipped and logged as
  `stapel.analytics unavailable <id> <unconfigured|unsupported> <reason>`; consent and undelivered
  events survive a restart; `looksLikePii` gives the web's verdict on every case of
  `pii-vectors.json` (shared with `@stapel/analytics`).
- **Markers** (tag `stapel.analytics`): `consent <state>`, `unavailable …`, `unregistered <name> …`,
  `pii redacted|kept in <name>`, `pii rejected <name>`, `buffer full …`, `dropped a batch …`.
  Property values are never logged.
- **What not to do:** no vendor name or vendor SDK in this artifact (the vendor-leak gate); do not
  call a sink from app or brick code; do not log property values; do not store consent anywhere else
  (one answer per installation, like the web's `stapel-analytics:consent`).
- **Later:** a server-side consent record (backend gap G1) behind a `ConsentStore` seam; batch-level
  client context once the collector contract carries it (it does from stapel-analytics 0.8.3, used
  by `stapel-analytics-collector`).
