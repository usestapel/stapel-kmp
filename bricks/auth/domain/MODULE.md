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

## Hand-written (`src/commonMain/kotlin`)

| Package | What |
|---|---|
| `stapel.auth` | `StapelAuth` façade (api, session, capabilities, account, devices, stepUp), `AuthConfig`, `GuestPolicy` |
| `stapel.auth.model` | domain types (`AuthUser`, `SignInOutcome`, `AuthCapabilities`, `SessionDevice`, …); DTOs never reach screens |
| `stapel.auth.error` | `AuthError` (a `StapelError`) with the typed `AuthErrorReason` flows branch on |
| `stapel.auth.session` | `AuthSession` (identity state, guest, promotion/merge, sign-out reasons), `AuthTokenRefresher` (the `stapel-session` refresh seam), `StepUpController` (the `stapel-network` `VerificationHandler`) |
| `stapel.auth.repo` | `CapabilitiesRepository` (app-scoped, cache-first), `AccountRepository` (`/me/`, user partition, SWR), `SessionsRepository` (user partition, memory only, online-only revocations) |
| `stapel.auth.presentation` | presenters: method chooser, OTP (e-mail/phone), password sign-in, password registration, guest, sign-out (+ everywhere), sessions/devices, login challenge; the shared `ChallengeStep` tail (TOTP, forced password change) |
| `stapel.auth.platform` | seams only in v0: `ExternalUserAgent`, `PasskeyAuthenticator`, `NativeIdentityProvider`; `AuthPlatform.completable` |
| `stapel.auth.di` | `authModule()` (the only Koin import) |
| `stapel.auth.internal` | DTO → domain mapping (one file) |

## Invariants (each has a test that fails when broken)

- The refresher uses the BARE transport and maps `refresh_revoked`/`token_revoked` → Revoked,
  `refresh_invalid`/`refresh_not_provided`/`user_not_found` → Expired, `account_disabled` →
  AccountDisabled; anything that is not a 401 is Transient (never a sign-out).
- A method the client cannot complete (passkey, OAuth, magic link, SSO, QR in v0) is never offered,
  even when the server enables it and an adapter is present.
- Resend is ignored while the cooldown (from capabilities) runs; one request after it.
- A refused sign-in or a pending challenge writes no tokens; an expired challenge is refused locally.
- `logout()` refreshes first when the access token is past expiry, then runs the session's farewell
  hooks (`SessionManager.farewell()`: push unregister, …) while the bearer is valid — the server
  logout blacklists the access token — bounded by `SessionConfig.farewellTimeout`, a failing or hanging
  hook never blocking; then sends the current refresh token, and clears locally whatever the server
  answers (`SessionTest`; on the wire: stapel-notifications-e2e E-1/E-4).
- No auth mutation is offline-queued; the profile and the sessions list live in the account's cache
  partition, which `CacheSessionBinding` wipes on every session end (e2e D-6).

## Not here (v0) — stated, not hidden

- Multi-account slots / account switch, `ElevationSource`, a non-secret key-value store (not in the
  substrate): single active session; the user snapshot is re-read from `/me/` after a cold start
  (`AuthState.SignedIn(user = null)` until then).
- OAuth, magic link, SSO, passkeys, QR, biometrics: seams only (ruling R14).
- Password reset, password change, TOTP setup, MFA enrolment, contact change, account closure,
  security log: later slices. `ChallengeStep.MfaEnrollRequired` is a dead-end state ("finish on the web").
- Step-up covers code factors only; there is no UI sheet yet.

## Backend facts the contract does not state (found by the e2e suite)

See `bricks/auth/e2e/README.md`.
