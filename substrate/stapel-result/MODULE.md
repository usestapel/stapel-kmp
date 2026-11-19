# stapel-result — module map

Rank 0 substrate, no dependencies. Everything here is pure data and pure functions.

## Seams

| Seam | Who implements / consumes |
|---|---|
| `StapelResult<T, E : StapelError>` | every operation; generated clients return `StapelResult<T, <Op>Error>` |
| `DomainError` | generated per-brick error classes (`code`/`status`/`params` default from `envelope`) |
| `TransportCarrier` | the one `Transport(failure)` variant each generated brick error type declares; it implements every `<Op>Error` of the brick |
| `TransportFailure` | produced only by the network layer (`stapel-network`, `stapel-wire` transports) |
| `classifyFailure(envelope, challenge, retryAfterHeader, isDomainCode)` | the one rule deciding domain vs transport for a failed response; transports call it, nobody re-implements it |
| `ErrorCatalog` | generated per brick and for backend core codes; `ClientErrorCatalog` is the floor for `stapel.*` codes |
| `ErrorTextResolver` | UI (`errorText()`), i18n engines |
| `Resource<T>` / `Freshness` | `stapel-cache` produces, presenters and UI consume |
| `ErrorSnapshot` | storage of a failure (offline queue), logs |

## Invariants

- Cancellation propagates as `CancellationException`; it is never an `Err`.
- `TransportFailure.NoResponse` has `status == null` and code `stapel.transport.failed`.
- A response without an error envelope carries the synthesized `stapel.http.<status>` code
  (`ErrorEnvelope.synthesized`) and is never classified as domain.
- Error text never shows the server's `error` string unless its language matches the display
  language; sentences never carry a status number — it goes to `ErrorText.detail`.
- `Resource` keeps data only in `Ready`; a failed revalidation is `Freshness.refreshError`.

## What not to do

- Do not add a dependency (coroutines, serialization): `Flow` helpers live in the modules that
  own flows (`stapel-cache`'s `firstSettled`).
- Do not catch `Throwable` to build a `StapelResult`; let cancellation through.
- Do not render `envelope.serverText` directly; go through `ErrorTextResolver`.
