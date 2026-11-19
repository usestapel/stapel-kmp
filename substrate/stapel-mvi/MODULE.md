# stapel-mvi — module map

- **Provides:** `Store`, `Presenter` (ordered intent queue, `reduce`, `emit`, keyed `launch`,
  `saved`), `PresenterContext`, `StateSaver`, `FlowMachine` + `FlowAnalytics`, `record()` for tests.
- **Seams:** `StateSaver` (the owner supplies process-death storage; `stapel-mvi-compose` backs it with
  the holder `ViewModel`'s `SavedStateHandle`), `FlowAnalytics` (the app forwards flow steps to its
  analytics), `PresenterContext.scope` (the owner decides the lifetime).
- **Invariants:** intents are handled one at a time in dispatch order; an effect is delivered to
  exactly one collector exactly once and is buffered while nobody collects; a newer `FlowMachine`
  transition cancels in-flight work and its late result is never applied; nothing here depends on
  Compose, lifecycle, Koin or navigation (dependency-direction gate).
- **Do not:** hold a navigator in a presenter (emit an effect instead); put secrets into
  `StateSaver`; collect `effects` from two places (the second collector steals effects).
