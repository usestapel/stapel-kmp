# stapel-auth-ui

The auth screens of Stapel clients (Compose Multiplatform, Material-free): sign-in with the server's
methods (e-mail / phone one-time code, password), the code step with resend cooldown, the TOTP
challenge, the forced password change, lockouts, registration, the guest wall, sessions and devices,
the sign-out confirmation and the account control. Every screen is a stateless composable of a state
class and an event callback; the screens draw only through `stapel-ui`'s `StapelSkin` slots, so the
app's skin (`Material3Skin`, `FoundationSkin`, its own) styles them.

Coordinates: `io.github.usestapel:stapel-auth-ui` (depends on `stapel-auth`).

```kotlin
val strings = StapelStrings { register(CoreUiBundle); register(AuthUiBundle); override("ru", myCopy) }
CompositionLocalProvider(LocalStapelErrorText provides ErrorTextResolver(myCatalogs + authErrorCatalogs())) {
    StapelMaterialTheme(strings = strings) {
        SignInScreen(state, onEvent = { event -> /* hand to the sign-in presenter */ })
    }
}
// Bound to the domain (stapel-auth) and to navigation: ids and paths of the web pair.
val bridge = AuthNavBridge(stapelAuth, appScope)            // NavAuthSource + return after an involuntary sign-out
val auth = authNavContribution(version,
    signIn = { AuthSignIn(stapelAuth, onSignedIn = {}) },
    security = { AuthSessions(stapelAuth) },
    accountMenu = { AuthAccountMenu(stapelAuth, onOpen = { open() }) })
// rememberStapelNavigator(listOf(auth, …), NavPolicy(signIn = AuthNavIds.Login), bridge).also(bridge::attach)
```

Replace one screen: `object MyScreens : AuthScreens by DefaultAuthScreens { override fun OtpCode(…) }`,
provided through `LocalAuthScreens`. Replace auth's own widgets (session row, method button): return
your `AuthSkin` from `StapelSkin.extension(AuthSkinKey)`.

A runnable example (stories of every state, and the live app against a local backend) is
`bricks/auth/demo`: `./gradlew :stapel-auth-ui-demo:run [--args="--base-url http://127.0.0.1:18731/"]`.
