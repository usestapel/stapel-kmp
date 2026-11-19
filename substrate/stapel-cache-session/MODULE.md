# stapel-cache-session — module map

- **Provides:** `CacheSessionBinding` (an `AutoCloseable`); `stapel.cache.session.di.cacheSessionModule()`.
- **Rules, each with a test that fails when broken:**
  - `Initializing` → nothing opens; user-scoped reads wait in `Loading` (the session-ready gate);
  - a session → the account's partition opens; cold start paints the last known value first;
  - another account signs in → its partition opens, the previous one is not wiped;
  - any session end (logout, expired, revoked, storage lost, …) → the partition is wiped from a
    `LogoutHook`, i.e. before the session publishes `Unauthenticated` (no opt-out, web parity);
  - first settled state → partitions of other accounts are wiped (a process killed mid-logout);
    no session at all → every account partition is wiped.
- **Account:** `SessionState.Authenticated.accountId` / `Anonymous.accountId`, published by
  `stapel-session` from the access token's `user_id` claim (no guessing here); a session without
  one gets no account partition.
- **Later:** a retention window for the `Expired` case (fork F4, default wipe).
