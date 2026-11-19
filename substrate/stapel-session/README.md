# stapel-session

The owner of a Stapel client's session: a platform token vault (Android Keystore,
iOS Keychain, desktop owner-only file), single-flight refresh with three outcomes,
the session state flow, and logout hooks for local erasure.

Coordinates: `io.github.usestapel:stapel-session` (provisional group).

```kotlin
val session = SessionManager(
    SessionDependencies(platformTokenVault(platform, dispatchers), appScope, systemClock(), connectivity),
    refresher = authTokenRefresher,      // the auth brick's TokenRefresher over the bare transport
)
session.restore()                        // settles SessionState.Initializing
val api = StapelTransport(deps, authenticator = session.authenticator)
session.registerLogoutHook { cause -> cache.wipe() }
// With Koin: sessionModule() — binds SessionManager, RequestAuthenticator, TokenVault, SessionScopeHandle.
```
