# stapel-profiles-testing

Test kit for apps using `stapel-profiles-ui`: the screens' test tags (`ProfilesTags`), walkers that drive
the real settings screens in a Compose UI test (`openSettingsSection`, `renameInSettings`,
`chooseLanguage`), and sample domain values for previews (`ProfilesSamples`).

Coordinates: `io.github.usestapel:stapel-profiles-testing` (test dependency only).

```kotlin
runComposeUiTest {
    setContent { StapelAppContent(app) }
    signInWithMockOtp("ada@example.com")                       // stapel-auth-testing
    openSettingsSection("profiles.language", ProfilesTags.LANGUAGE)
    chooseLanguage("ru")
}
```
