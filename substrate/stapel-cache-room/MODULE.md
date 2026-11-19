# stapel-cache-room — module map

- **Provides:** `RoomCacheStoreFactory` (a `stapel.cache.CacheStoreFactory`), `RoomCacheOptions`;
  platform entry points `RoomCacheStoreFactory(context)` (Android) and `RoomCacheStoreFactory()` (iOS).
- **Seams:** `RoomCacheOptions.driver` (plug an encrypting/system `SQLiteDriver`; default bundled),
  `ioContext`, `onDiscarded` (metric hook for destructive recreation).
- **Invariants:**
  - every Room type (`CacheDatabase`, `EntryDao`, rows) is `internal`; the public surface is the factory;
  - one database file set per partition; no `WHERE account =` filtering anywhere;
  - wipe = close + delete the partition directory (`.db`, `-wal`, `-shm`, `-journal`), asserted on the
    file system in tests;
  - `cache.db` has no migrations: version bump or format bump = drop; a foreign identity hash (same
    version, other schema) is caught and the file recreated — Room alone would throw;
  - prefix operations use `key >= :p AND substr(key, 1, length(:p)) = :p` (exact for any key; a
    range with a sentinel upper bound misses non-ASCII keys — the contract suite catches it).
- **Schemas:** `schemas/` is committed (Room 3 requires it); it documents `cache.db` versions.
- **Tests:** `src/sqliteTest` (jvm + iOS simulator) runs `CacheStoreContract` plus file-level
  proofs; Android host tests cannot load the bundled SQLite JNI library, so only `commonTest` runs
  there. Android device behaviour (real `noBackupFilesDir`, file-based encryption, process kill by
  the OS) is not covered by host tests.
- **Later (not in this artifact yet):** `list_head`/`list_item`/`cursor` tables (paged lists,
  revision sync), `outbox.db` with real migrations, `RoomPartitionHost` for typed brick stores,
  budgets/eviction.
- **Do not:** expose a Room type publicly; add a `@Database` an app must declare; read the account
  id back from a directory name.
