# stapel-analytics-appmetrica — module map

- **Provides:** `AppMetricaAnalyticsBridge` (Obj-C-exportable vendor half),
  `APPMETRICA_ANALYTICS_BRIDGE_ABI = 1`, `appMetricaSink(bridge, whenMissing)`; Android
  `appMetricaSink(platform, apiKey)`; `di.appMetricaAnalyticsModule(apiKey)` (Android, desktop) /
  `di.appMetricaAnalyticsModule(bridge)` (iOS). Contribution key `analytics.appmetrica`.
- **Behaviour:** activated with data sending, advertising identifiers and location tracking off;
  the hub's kill switch is `setDataSendingEnabled`; a page is the event `screen_view` with
  `screen_name` (AppMetrica has no screen API); the user profile id follows each event's
  `userHash`; user properties are custom string profile attributes (≤ 200 characters).
- **Gaps (named):** AppMetrica has no "reset analytics data": `reset()` clears the user profile id,
  the device's AppMetrica id stays. With data sending off the SDK still records its own session
  events locally; they leave the device only after consent turns data sending on.
- **Tests:** `AppMetricaSinkConformanceTest` (recording bridge, every target) and the desktop /
  unconfigured availability test. The Android wrapper is compiled, not unit-tested.
