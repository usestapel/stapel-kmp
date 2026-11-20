# stapel-mvi — module map

- **Provides:** `Store`, `Presenter` (ordered intent queue, `reduce`, `emit`, keyed `launch`,
  `saved`), `PresenterContext`, `StateSaver`, `FlowMachine` + `FlowAnalytics`, `record()` for tests;
  `stapel.mvi.paging`: `PagingMachine` (first load, append, retry, end, refresh, stale-while-revalidate
  over `stapel-wire`'s `Page`/`PageCursor` — any of the four pagination styles), `PagingState`
  (`content: Resource<List<T>>`, `append: AppendStatus`, `total`), `AppendStatus`.
- **Seams:** `StateSaver` (the owner supplies process-death storage; `stapel-mvi-compose` backs it with
  the holder `ViewModel`'s `SavedStateHandle`), `FlowAnalytics` (the app forwards flow steps to its
  analytics), `PresenterContext.scope` (the owner decides the lifetime).
- **Paging rules** (each a test in `PagingMachineTest`, run per style): an append only with rows held,
  nothing in flight, not refreshing and before the end; a failed append keeps the rows and retries the
  same cursor; a row already held is replaced in place; refresh keeps rows on screen (`refreshing`),
  replaces them with the head page or keeps them with `refreshError`; a refresh cancels an append in flight
  (the request, its late answer is never applied, the list is not left `Loading` if the refresh fails);
  `start(held)` shows cached rows stale + refreshing while the head revalidates. A brick presenter owns
  one machine per list and forwards its intents (`loadMore`, `retry`, `refresh`).
- **Invariants:** intents are handled one at a time in dispatch order; an effect is delivered to
  exactly one collector exactly once and is buffered while nobody collects; a newer `FlowMachine`
  transition cancels in-flight work and its late result is never applied; nothing here depends on
  Compose, lifecycle, Koin or navigation (dependency-direction gate); it depends on `stapel-result` and
  `stapel-wire` only.
- **Do not:** hold a navigator in a presenter (emit an effect instead); put secrets into
  `StateSaver`; collect `effects` from two places (the second collector steals effects).
