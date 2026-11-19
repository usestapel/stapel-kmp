# stapel-session — module map

## Provides
- `SessionManager` (+ `SessionDependencies`, `SessionConfig`, factory `SessionManager(...)`),
  `SessionState`, `SessionEnd`, `SessionKind`, `SessionEvent`, `TokenPair`, `jwtExpiry`.
- Seams: `TokenRefresher` → `RefreshOutcome.Rotated | Rejected | Transient` (implemented by the
  authenticating brick over the bare transport); `LogoutHook`.
- Vault: `TokenVault`, `StoredSession`, `VaultRead`, `TokenVaultConfig`, `InMemoryTokenVault`,
  `platformTokenVault(platform, dispatchers, config)`.
- `di.sessionModule(config, vault)`: `SessionManager`, `RequestAuthenticator`, `TokenVault`,
  `SessionScopeHandle` (needs a `TokenRefresher` binding).

## Invariants (each has a test; see the KDoc of `SessionManager` for the list)
Born `Initializing`; single-flight refresh; compare-before-rotate; three outcomes (a transient
refresh never signs out); rotated pair persisted before any waiter gets it, rotation marker before
the call; teardown order vault → hooks → state → event, `sessionLost` a no-op during logout;
proactive refresh before `exp` on the request path (none offline); unreadable vault =
`StorageLost`, never plaintext.

## Platform vaults — what is measured
| Platform | Mechanism | Proven by | Not proven |
|---|---|---|---|
| Desktop | owner-only file (`0600`, dir `0700`), atomic replace | jvm test (permissions, corrupt file → `Unreadable`) | Windows ACL path |
| Android | AES-256-GCM key in `AndroidKeyStore`, ciphertext under `noBackupFilesDir` | host test of the envelope and file with a JVM key (no plaintext on disk, wrong key / tampered → `Unreadable`) | **the Keystore itself** (needs a device; no emulator image) |
| iOS | Keychain item `AfterFirstUnlockThisDeviceOnly`; `NSUserDefaults` marker wipes it after a reinstall | simulator test of the vault logic over the Keychain seam and the reinstall wipe with real `NSUserDefaults` | **the real Keychain round trip**: a bare simulator test process has no keychain (`errSecNotAvailable`); ad-hoc entitlement signing is killed by the simulator. A host-app test is needed. |

## What not to do
- Do not refresh from a second process (app extension, widget): one process owns rotation.
- Do not call the refresh endpoint through the authenticated transport (re-entry deadlock).
- Do not read tokens for display or logging; `TokenPair.toString()` is redacted on purpose.
- Multi-account slots (AUTH's account switch) are not here yet: one active session.
