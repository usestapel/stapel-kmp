# stapel-wire — module map

- **Provides:** the types generated code compiles against (ARCH-CODEGEN §5.3, ruling R5).
- **Seams:**
  - `ApiTransport` — implemented by `stapel-network` (recover-once pipeline) and by test fakes.
    `execute` never throws for an HTTP/transport/decoding outcome; it builds `StapelResult.Err`
    through `OperationSpec.errors` (`ErrorDecoder.domain` for an envelope, `.transport` for a
    `TransportFailure`), resolves 401 refresh and step-up before returning, and lets
    `CancellationException` propagate.
  - `CacheKey` / `KeyPattern` / `CacheHint` — consumed by `stapel-cache`; produced only by generated
    `<Brick>Keys` / `<Brick>Invalidation`.
- **Invariants:**
  - Models are encoded and decoded with `WireJson` only (unknown keys ignored, defaults not encoded:
    that is what makes an omitted optional field and `Maybe.Absent` stay off the wire).
  - `Decimal` is never converted to a binary floating-point number here.
  - Enums and response unions always have an `Unknown` arm; decoding a new server value never fails.
  - `CODEGEN_RUNTIME_ABI` changes whenever a generated file would need to change to keep compiling;
    each generated `<Brick>Contract.RUNTIME_ABI` is compared with it in the brick's tests.
- **Do not:** add Ktor, Koin or Compose here; construct a `Json` in generated code; add behaviour
  that belongs to the transport (retries, auth headers, host rewriting).
