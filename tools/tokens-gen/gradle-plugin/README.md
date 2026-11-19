# stapel-tokens-gradle-plugin

Turns an app's `stapel.theme.json` (the same file the web app uses) into a `StapelTokens` value at
build time with the Kotlin port of the §68 resolver. No Node.

Plugin id: `io.github.usestapel.tokens` (group provisional until the first publish; version = the train).

```kotlin
plugins { id("io.github.usestapel.tokens") }

stapelTokens {
    theme.set(layout.projectDirectory.file("../stapel.theme.json"))   // default: ./stapel.theme.json
    packageName.set("shop.theme")
    objectName.set("AppTokens")
}
// StapelMaterialTheme(tokens = AppTokens) { … }
```

`generateStapelTokens` writes `build/generated/stapelTokens` (part of `commonMain`, not committed);
`checkStapelTokens` (part of `check`) fails on a theme with contrast or reference errors.
