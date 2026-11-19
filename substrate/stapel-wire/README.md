# stapel-wire

The runtime that generated Stapel API clients compile against. Ktor-free: it depends only on
`stapel-result`, kotlinx.serialization and kotlinx-io, so a brick's domain library never sees an
HTTP client (ruling R5). `stapel-network` implements `ApiTransport`; tests use a fake.

Coordinates: `io.github.usestapel:stapel-wire` (provisional group).

```kotlin
val api: AuthApi = AuthApiClient(transport)          // transport: ApiTransport from stapel-network
when (val r = api.getMe()) {
    is StapelResult.Ok -> show(r.value)
    is StapelResult.Err -> when (val e = r.error) {
        is AuthApiError.Transport -> retryLater(e.failure)
        else -> showError(e.code)
    }
}
val sessions = api.listSessionsPager()                // Pager over any of the four page styles
val key = AuthKeys.getMe()                           // CacheKey for stapel-cache
```

What is here: `OperationSpec` / `OperationCall` / `ApiTransport` / `ErrorDecoder` (the operation
seam), `Maybe` (PATCH tri-state), `Decimal` (lossless decimals), `WireEnum` + serializers (enums
that round-trip unknown values), `UnionSerializer` (`oneOf`), `Page` / `PageCursor` / `Pager`
(the four pagination styles), `FilePart` / `BinaryBody`, `CacheKey` / `KeyPattern` / `CacheHint`,
`WireJson` and `CODEGEN_RUNTIME_ABI`.
