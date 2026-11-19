# stapel-i18n

UI strings for Stapel clients, pure Kotlin (no Compose): flat dictionaries, `{param}` placeholders,
CLDR plural families as flat keys (`key.one`, `key.few`, …), layered bundles where the floor, then
each brick, then the app's own copy win in that order, and the locale as observable state.

Coordinates: `io.github.usestapel:stapel-i18n` (all targets).

```kotlin
val strings = StapelStrings {
    register(CoreUiBundle)                                   // the stapel.ui.* floor
    register(AuthUiBundle)                                   // a brick's bundle
    override("ru", mapOf("auth.ui.login_title" to "Вход"))   // the app's copy, always last
}
strings.t(CoreUiStrings.Retry)                               // "Try again"
strings.tPlural(CoreUiStrings.CharactersLeft, 3)             // "3 characters left"
strings.setLocale("ru")                                      // runs the LocaleLoader, then switches
```

Error-code text (`error.400.code_expired`, `stapel.http.503`) is not here: it is
`stapel-result`'s `ErrorTextResolver`, fed with the same locale.
