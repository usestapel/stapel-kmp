# stapel-analytics-appmetrica

AppMetrica as a sink of the `stapel-analytics` hub, instead of or alongside Firebase. Only an app
that chose AppMetrica depends on this artifact (gate G-X2).

Coordinates: `io.github.usestapel:stapel-analytics-appmetrica` (group provisional until the first publish).

| Platform | How | Without config |
|---|---|---|
| Android | links `io.appmetrica.analytics:analytics` 8.6.0, activated with data sending off | no API key → `Unconfigured("AppMetrica API key")` |
| iOS | the app shell's Swift bridge `StapelAppMetricaAnalytics` (`ios-bridges/appmetrica`, `appmetrica-sdk-ios` 7.0.0, product `AppMetricaCore`) | empty key → `Unconfigured("AppMetrica API key")` |
| Desktop | — | `Unsupported("desktop")` |

```kotlin
modules(analyticsModule(), appMetricaAnalyticsModule(AppSecrets.APPMETRICA_API_KEY))      // Android, desktop
modules(analyticsModule(), appMetricaAnalyticsModule(StapelAppMetricaAnalytics.shared))   // iOS
```
