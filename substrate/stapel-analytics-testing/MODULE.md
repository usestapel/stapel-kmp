# stapel-analytics-testing — module map

- **Provides:** `AnalyticsSinkConformance` (cases `availability-honest`, `collection-idempotent`,
  `events-in-order`, `page-is-a-screen`, `identity-follows-and-resets`, `user-property`,
  `failure-is-retry`), `SinkUnderTest`, `VendorProbe` / `VendorCall`, `FakeAnalyticsSink`,
  `RecordingAnalytics`.
- **Invariants:** no test-framework dependency in main code (failures are `AssertionError`s, so the
  kit runs under any runner); fresh subjects per case; a provider's probe is the vendor side it
  talks to (a recording bridge on iOS, a fake of the thin vendor wrapper on Android).
- **Inversions:** `AnalyticsSinkConformanceTest` runs the suite against deliberately broken sinks (an
  unconfigured sink that throws or claims delivery, an ignored kill switch, reordered events, a
  reset that keeps the user id, a sink that throws on vendor failure) and asserts the named case
  fails.
- **Rule:** every analytics provider artifact has a test class extending `AnalyticsSinkConformance`
  for each target it ships.
