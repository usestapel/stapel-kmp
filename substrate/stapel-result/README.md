# stapel-result

Result, error, load-state and error-text types that every stapel-kmp artifact speaks. Kotlin
stdlib only.

```kotlin
implementation("io.github.usestapel:stapel-result")   // version from stapel-bom
```

```kotlin
when (val r = authApi.emailVerify(body)) {          // StapelResult<LoginResponse, EmailVerifyError>
    is StapelResult.Ok -> session.adopt(r.value.tokens)
    is StapelResult.Err -> when (val e = r.error) {
        is AuthApiError.InvalidCodeAttempts -> showFieldError(e.attemptsRemaining)
        is AuthApiError.Transport -> showBanner(resolver.describe(e, locale))   // offline, 5xx, …
        else -> showBanner(resolver.describe(e, locale))
    }
}
```

- `StapelResult<T, E>` — `Ok(value)` / `Err(error)`; cancellation is never a value.
- `StapelError` — `DomainError` (generated per brick, `ServerRefusal` for code-only handling) and the
  shared `TransportFailure` family; `classifyFailure` decides which one a response is.
- `state.Resource<T>` — `Loading` / `Ready(data, freshness)` / `Failed(error)`; action gates; mandate.
- `text.ErrorTextResolver` — the web's error-text ladder over `ErrorCatalog`s, with the
  `ClientErrorCatalog` floor (en/ru/es).
- `@InternalStapelApi` — opt-in marker for cross-module internals.

See [MODULE.md](MODULE.md) for the seams and invariants.
