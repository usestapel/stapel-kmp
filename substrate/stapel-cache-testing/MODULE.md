# stapel-cache-testing — module map

- **Provides:** `CacheStoreContract` (`verify`, `caseNames`).
- **Invariants:** no test-framework dependency in main code (failures are `AssertionError`s, so the
  kit works under any runner); a fresh factory per case; each case cleans its partitions.
- **Inversions:** `CacheStoreContractTest` runs the suite against deliberately broken stores
  (prefix without segment boundary, invalidation that marks nothing, truncated payloads, shared
  partitions, a memory store claiming durability, a destroy that keeps data) and asserts the
  named case fails.
- **Later:** `OutboxStoreContract` (with the outbox), fakes for clock/connectivity if
  `stapel-testing` does not already cover them.
