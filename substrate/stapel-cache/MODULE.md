# stapel-cache — module map

- **Provides:** `StapelCache` (observe / get / peek / write / update / invalidate / remove),
  `ManagedStapelCache` (setAccount / wipe / addWipeHook / onForeground / close), the builder
  `StapelCache(StapelCacheConfig)`, `QuerySpec`, `ReadPolicy` (NetworkOnly, NetworkFirst,
  CacheFirst, StaleWhileRevalidate), `Persistence`, `ReadOptions` (policy override, polling),
  `Refetch`, `CacheEvent`/`CacheObserver`, `firstSettled`; revision-synced collections
  (`revisioned`, `RevisionSpec`, `RevisionRequest`, `RevisionPage`, `RevisionCollection`, `SyncReport`);
  the storage SPI `CacheStore`, `ListStore` (list heads, rows in window order, `Long` cursors),
  `CacheStoreFactory`, `PartitionId`, `PartitionStores`, `StoredEntry`, `StoredListHead`,
  `StoredListItem`; `MemoryCacheStoreFactory`.
- **Consumes:** `Resource`/`Freshness`/`StapelResult`/`StapelError` (`stapel-result`), `CacheKey`/
  `KeyPattern`/`CacheScope` (`stapel-wire`), an injected `kotlin.time.Clock`.
- **Seams:** a durable store implements `CacheStoreFactory` and passes `CacheStoreContract` and
  `ListStoreContract` (`stapel-cache-testing`); `stapel-cache-session` drives `setAccount`/`wipe` from the session.
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
    is `false` until the outbox exists;
  - revision collections: rows + the last synced revision (a cursor) per key in the key's partition;
    a sync publishes on completion only, pins later pages to the first page's `global_max`, keeps rows
    and revision on a failed walk or one truncated by `maxPages`, updates in place / appends / drops
    deleted ids on a delta; a schema-hash change or an undecodable row drops the list and its revision;
    `invalidate` marks stale (observed → delta sync), `remove` drops rows and revision (next sync full).
    With the memory store the first sync of a process is full, later ones deltas (same contract suite).
- **Not here yet (later slices):** mutations with optimistic overlays and the outbox (offline
  queue), the cache-level paged window (`PagedList`; its storage, `ListStore`, exists — the UI paging
  state machine is `stapel-mvi`'s `PagingMachine`), realtime bridge (cursors are stored by `ListStore`), typed stores, budgets/eviction
  and stats, payload cipher.
- **Do not:** add a retry loop around fetches (the transport owns retries); put credentials in a
  value; discover storage from the classpath.
