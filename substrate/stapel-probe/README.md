# stapel-probe

**Build probe, not a product module.** It holds one function so that the
`stapel.substrate` convention (targets, explicit API, tests on jvm / android host /
iOS simulator, ABI dump, Dokka gate, dependency gates, publishing) has a subject
before real substrate modules exist. It is removed once they do.

```kotlin
probeSlug(" Stapel ", "KMP") // "stapel-kmp"
```
