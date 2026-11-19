# stapel-cache — module map

- **Provides:** `StapelCache` (observe / get / peek / write / update / invalidate / remove),
  `ManagedStapelCache` (setAccount / wipe / addWipeHook / onForeground / close), the builder
  `StapelCache(StapelCacheConfig)`, `QuerySpec`, `ReadPolicy` (NetworkOnly, NetworkFirst,
  CacheFirst, StaleWhileRevalidate), `Persistence`, `ReadOptions` (policy override, polling),
  `Refetch`, `CacheEvent`/`CacheObserver`, `firstSettled`; the storage SPI `CacheStore`,
  `CacheStoreFactory`, `PartitionId`, `PartitionStores`, `StoredEntry`; `MemoryCacheStoreFactory`.
- **Consumes:** `Resource`/`Freshness`/`StapelResult`/`StapelError` (`stapel-result`), `CacheKey`/
  `KeyPattern`/`CacheScope` (`stapel-wire`), an injected `kotlin.time.Clock`.
- **Seams:** a durable store implements `CacheStoreFactory` and passes `CacheStoreContract`
  (`stapel-cache-testing`); `stapel-cache-session` drives `setAccount`/`wipe` from the session.
- **Invariants:**
  - data only behind `Resource.Ready`; a failed revalidation over held data stays `Ready` with
    `refreshError`; `CacheFirst`/`NetworkFirst` never show stale data while loading;
  - one in-flight fetch per key per partition (single-flight), run in the partition's scope so a
    wipe cancels it and a late answer is never written;
  - storage keys are escaped segments each followed by U+001F (arguments marked as their own
    segment): a segment prefix is a string prefix, and a storage key decodes back to its key;
  - an entry with another response-schema hash, or one that does not decode, is a miss and is
    deleted, never an exception;
  - user-scoped reads wait (`Loading`) until `setAccount` is called; with no account they go to the
    network and are not kept; app-scoped reads never wait;
  - `Persistence.Never` values never reach a store and are dropped with the last collector;
  - storage is explicit wiring (memory default, `Degraded` event once); `capabilities.offlineQueue`
    is `false` until the outbox exists.
- **Not here yet (later slices):** mutations with optimistic overlays and the outbox (offline
  queue), paged lists, revision sync, realtime bridge and cursors, typed stores, budgets/eviction
  and stats, payload cipher.
- **Do not:** add a retry loop around fetches (the transport owns retries); put credentials in a
  value; discover storage from the classpath.
