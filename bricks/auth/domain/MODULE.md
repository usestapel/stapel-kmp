# stapel-auth — module map

- **Generated (do not edit):** `src/commonMain/generated/` — packages `stapel.auth.api` (`AuthApi`,
  `AuthApiClient`, `AuthUrls`, `AuthKeys`, `AuthInvalidation`, `AuthContract`, pagers),
  `stapel.auth.api.model`, `stapel.auth.api.error`; `api-names.lock.json` (name lock) and
  `codegen-report.json` (diagnostics behind the ratchet). Inputs: `contracts/auth/` (pin, vendored
  contract, overlay, cache plan, ratchet). Regenerate with `./gradlew :stapel-auth:stapelGenerate`;
  `stapelGenerateCheck` (part of `check`) fails on any drift.
- **Seam:** the wire is reached only through `AuthApi`; transports implement `stapel.wire.ApiTransport`.
- **Hand-written code** (repositories, presenters, mapping to domain types) goes to `src/commonMain/kotlin`
  and never edits generated files; a contract gap is fixed at the source or, interim, in
  `contracts/auth/overlay.json` (budgeted to zero).
