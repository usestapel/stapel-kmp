# stapel-cache-testing — module map

- **Provides:** `CacheStoreContract` and `ListStoreContract` (`verify`, `caseNames`).
- **Invariants:** no test-framework dependency in main code (failures are `AssertionError`s, so the
  kit works under any runner); a fresh factory per case; each case cleans its partitions.
- **Inversions:** `CacheStoreContractTest` runs the suite against deliberately broken stores
  (prefix without segment boundary, invalidation that marks nothing, truncated payloads, shared
  partitions, a memory store claiming durability, a destroy that keeps data) and asserts the
  named case fails; `ListStoreContractTest` does the same for lists (a merge that moves updated rows
  to the end, a replace that keeps old rows, a prefix delete that keeps cursors, prefix matching
  without a segment boundary, shared partitions).
- **Later:** `OutboxStoreContract` (with the outbox), fakes for clock/connectivity if
  `stapel-testing` does not already cover them.
