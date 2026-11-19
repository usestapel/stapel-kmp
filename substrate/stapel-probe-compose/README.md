# stapel-probe-compose

**Build probe, not a product module.** One composable and one Compose UI test so the
`stapel.substrate-ui` convention (Compose Multiplatform on every target, UI tests on
desktop headless and on the iOS simulator) has a subject before real UI modules exist.

```kotlin
ProbeLabel("Stapel", "Compose") // renders "stapel-compose"
```
