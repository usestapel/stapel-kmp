# stapel-cache-session — module map

- **Provides:** `CacheSessionBinding` (an `AutoCloseable`), `AccountIdResolver` with
  `AccountIdResolver.jwtClaim(...)` (default: `user_id`, then `sub`, read locally from the access JWT).
- **Rules, each with a test that fails when broken:**
  - `Initializing` → nothing opens; user-scoped reads wait in `Loading` (the session-ready gate);
  - a session → the account's partition opens; cold start paints the last known value first;
  - another account signs in → its partition opens, the previous one is not wiped;
  - any session end (logout, expired, revoked, storage lost, …) → the partition is wiped from a
    `LogoutHook`, i.e. before the session publishes `Unauthenticated` (no opt-out, web parity);
  - first settled state → partitions of other accounts are wiped (a process killed mid-logout);
    no session at all → every account partition is wiped.
- **Seam note:** `stapel-session` publishes no account id (`SessionState` carries a per-process
  `generation` only), so the account is read from the token's claims. Which claim the backend's
  access token carries is not verified here; the resolver is replaceable.
- **Later:** a Koin `di` factory; a retention window for the `Expired` case (fork F4, default wipe).
