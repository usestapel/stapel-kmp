# stapel-push — module map

## Provides
- `PushController` (+ `PushDependencies`, `PushOptions`, `PushRouting`, `AskPolicy`, factory
  `PushController(...)`), `PushState`, `Registration`, `PushLinkOpener`.
- Seams: `PushProvider` (provider artifacts), `PushDeviceRegistry` (+ `PushDevice`, `DeviceStatus`,
  `RegistryError`; the brick adapter), `PushRationale` (the app's explanation before the OS prompt),
  `PushOpenListener` (after a tap: the adapter marks the feed row read), `PushStore`
  (`platformPushStore`, `InMemoryPushStore`).
- Model: `PushToken` (redacted `toString`, `fingerprint` = SHA-256 hex, the backend's
  `token_fingerprint`), `PushMessage`, `PushEvent`, `PushPermission`, `ProviderAvailability`,
  `PushError`, `ForegroundPresentation`, `PushChannelSpec`.
- `di.pushModule(options)`: `PushController` (created at start) and `PushStore`.

## Invariants (each has a test in `PushControllerTest`; the mutations that turn them red are listed in the PR)
- The OS prompt is never shown at launch; `enable()` runs the rationale first, a declined rationale
  leaves the one-shot prompt unspent; `AskPolicy.AfterSignIn` is provisional on iOS (no prompt).
- Registered on sign-in and on every start while signed in (enabled, permission allows), and on
  every provider rotation. A guest session never registers.
- Explicit sign-out: `unregister(installationId)` while the bearer is valid (a `stapel-session`
  `FarewellHook`), then `deleteToken()`. Every other session end (expired, revoked, storage lost):
  `deleteToken()` in the `LogoutHook`. The next account on the installation gets a fresh token.
- A 409 for the token: one new token, one retry, then `Failed` (no loop). A token the registry
  reports inactive is replaced. A registration recorded for another account is never reused.
- Permission revoked in the system settings (seen on return to the foreground): unregistered,
  `Blocked`, the choice kept; a re-grant registers again.
- Taps: link keys in order, a refused or absent link opens `fallbackUri`; a tap before `attach`
  (the launch notification) is kept and opened exactly once.
- Markers through the app logger (tag `stapel.push`): `stapel.push received <type> fg=<bool>`,
  `stapel.push opened <type> link=<opened|refused|none>`, `stapel.push state <registration>`,
  `stapel.push unavailable <unconfigured|unsupported> (<reason>)`.

## What not to do
- No vendor coordinate or vendor name in this module (Firebase, AppMetrica live in provider artifacts).
- Do not log or persist a `PushToken.value`; compare `fingerprint`.
- Do not ask the OS permission from app start; call `enable()` from a deliberate act.
- Two transactional providers are a wiring error (`pushModule` refuses at graph start).

## Not covered here
- The OS permission prompt itself and delivery are platform behaviour: provider artifacts and the
  owner's device checklist.
- "Open the system settings" for a denied permission is the app's (no API in v0).
