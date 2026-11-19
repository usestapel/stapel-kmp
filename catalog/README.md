# stapel-catalog

A Gradle version catalog for apps built on stapel-kmp: every published `stapel-*` artifact and the
`io.github.usestapel.codegen` / `.tokens` plugins at the train version, plus the toolchain pins an
app's own build names (Kotlin, AGP, Compose Multiplatform, Koin, coroutines, serialization, activity).

```kotlin
// settings.gradle.kts of the app
dependencyResolutionManagement {
    versionCatalogs { create("libs") { from("io.github.usestapel:stapel-catalog:0.0.1") } }
}
// build.gradle.kts
plugins { alias(libs.plugins.kotlin.multiplatform); alias(libs.plugins.stapel.tokens) }
dependencies { implementation(project.dependencies.platform(libs.stapel.bom)); implementation(libs.stapel.app) }
```

Aliases equal artifact ids (`stapel-auth-ui` → `libs.stapel.auth.ui`; an alias that is also a prefix,
like `stapel-auth`, is `libs.stapel.auth.asProvider()`).
