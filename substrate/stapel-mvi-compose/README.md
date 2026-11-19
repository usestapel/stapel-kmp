# stapel-mvi-compose

Keeps `stapel-mvi` presenters alive in Compose: each presenter lives in a holder `ViewModel` of the
current `ViewModelStoreOwner` (a navigation entry, a flow shared by several entries, or the app), so
it survives recomposition and configuration change and is cleared — its scope cancelled — with that
owner. Saved values (`Presenter.saved`) go through the owner's `SavedStateHandle` and survive process
death.

Coordinates: `io.github.usestapel:stapel-mvi-compose` (group provisional until the first publish).

```kotlin
CompositionLocalProvider(LocalPresenterFactory provides KoinPresenterFactory(koin)) { App() }

@Composable fun ThreadDestination(id: String) {
    val presenter = rememberPresenter<ThreadPresenter>(id)
    val state by presenter.collectState()
    presenter.CollectEffects { effect -> /* navigate, show a message */ }
    ThreadScreen(state, presenter::dispatch)
}
```
