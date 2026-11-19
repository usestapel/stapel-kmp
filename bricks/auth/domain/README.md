# stapel-auth

Auth domain library: the generated client of the `stapel-auth` backend (`AuthApi`, models, typed
errors, pagers, cache keys) and the hand-written session binding, repositories and headless sign-in
presenters (`stapel.auth.presentation`). No Compose, no platform SDK.

Coordinates: `io.github.usestapel:stapel-auth` (provisional group).

```kotlin
// Koin: runtimeModule(platform), networkModule(env), sessionModule(), authModule() + a ManagedStapelCache binding.
val auth: StapelAuth = koin.get()
auth.session.restore()

val signIn = OtpSignInPresenter(auth, PresenterContext(scope))
signIn.dispatch(OtpSignInIntent.RequestCode(OtpChannel.Email, "a@example.com"))
signIn.dispatch(OtpSignInIntent.SubmitCode("0000"))           // state: CodeSent → Done | Challenge | CodeSent(error)
auth.session.state.collect { /* Restoring | SignedOut(reason) | Guest(user) | SignedIn(user) */ }
```

End-to-end proof against a real backend: `bricks/auth/e2e` (stand script + JVM suite).
