# stapel-mvi

Headless state holders for Stapel clients, pure Kotlin (no Compose, no lifecycle, no Koin), so a
brick's domain artifact can ship its presenters and any UI — the default Compose screens, a custom
design, or Swift — can drive them.

Coordinates: `io.github.usestapel:stapel-mvi` (group provisional until the first publish).

```kotlin
class LoginPresenter(private val api: AuthApi, context: PresenterContext) :
    Presenter<LoginState, LoginIntent, LoginEffect>(LoginState(), context) {
    override suspend fun handle(intent: LoginIntent) = when (intent) {
        is LoginIntent.Submit -> launch(key = "submit") {
            reduce { copy(busy = true) }
            api.signIn(intent.login).fold({ emit(LoginEffect.SignedIn) }, { e -> reduce { copy(busy = false, error = e) } })
        }
    }
}
```

`FlowMachine<S>` is the multi-step journey primitive (pending / resolve / reject, last write wins,
`flow.<id>.<step>` analytics). Compose retention lives in `stapel-mvi-compose`.
