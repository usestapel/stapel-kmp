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
the call; explicit sign-out runs the farewell hooks first (once per session, concurrently, one shared budget;
a refresh rejected meanwhile does not change the cause from `Logout`); teardown order vault → hooks → state → event, `sessionLost` a no-op during logout;
proactive refresh before `exp` on the request path (none offline); unreadable vault (a record that
exists but cannot be read) = `StorageLost`, never plaintext; a wiped or never-written vault, and an
iOS process with no Keychain at all (`errSecMissingEntitlement` / `errSecNotAvailable`: nothing can
have been written) = `Empty`, no session and no "session lost" (a write there still fails and is logged).

## Platform vaults — what is measured
| Platform | Mechanism | Proven by | Not proven |
|---|---|---|---|
| Desktop | owner-only file (`0600`, dir `0700`), atomic replace | jvm test (permissions, corrupt file → `Unreadable`) | Windows ACL path |
| Android | AES-256-GCM key in `AndroidKeyStore`, ciphertext under `noBackupFilesDir` | host test of the envelope and file with a JVM key (no plaintext on disk, wrong key / tampered → `Unreadable`) | **the Keystore itself** (needs a device; no emulator image) |
| iOS | Keychain item `AfterFirstUnlockThisDeviceOnly`; `NSUserDefaults` marker wipes it after a reinstall | simulator test of the vault logic over the Keychain seam and the reinstall wipe with real `NSUserDefaults`; **the real Keychain in a host app on the simulator**: the reference app's XCUITest (`samples/shop-mobile`, `ios-smoke.sh --uitest`) signs in, relaunches into the stored session, signs out and relaunches signed out | the Keychain on a device (accessibility class, Secure Enclave-backed storage, restore from backup); a bare simulator test process has no keychain (`errSecNotAvailable`) |

## What not to do
- Do not refresh from a second process (app extension, widget): one process owns rotation.
- Do not call the refresh endpoint through the authenticated transport (re-entry deadlock).
- Do not read tokens for display or logging; `TokenPair.toString()` is redacted on purpose.
- Multi-account slots (AUTH's account switch) are not here yet: one active session.
