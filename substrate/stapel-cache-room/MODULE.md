# stapel-cache-room — module map

- **Provides:** `RoomCacheStoreFactory` (a `stapel.cache.CacheStoreFactory` whose partitions carry the
  entry store and the list store: tables `entry`, `meta`, `list_head`, `list_item`, `cursor`), `RoomCacheOptions`;
  platform entry points `RoomCacheStoreFactory(context, options)` (Android) and `RoomCacheStoreFactory(options)` (iOS);
  `desktopDataDirectory(appId)` (JVM: macOS Application Support, Windows `%LOCALAPPDATA%`, XDG data home),
  the directory `cacheRoomModule()` uses on desktop, named by the bound `StapelEnvironment.client.appId`;
  `RoomCacheOptions.ioContext` is required (the app's IO dispatcher; no ambient default, gate G-R1).
- **Seams:** `RoomCacheOptions.driver` (plug an encrypting/system `SQLiteDriver`; default bundled),
  `ioContext`, `onDiscarded` (metric hook for destructive recreation).
- **Invariants:**
  - every Room type (`CacheDatabase`, `EntryDao`, rows) is `internal`; the public surface is the factory;
  - one database file set per partition; no `WHERE account =` filtering anywhere;
  - JVM: `stapel-cache/` and partition directories are `rwx------` on POSIX (tightened when found
    wider), asserted in `jvmTest`; Windows relies on the profile ACL; mobile on the app sandbox;
  - wipe = close + delete the partition directory (`.db`, `-wal`, `-shm`, `-journal`), asserted on the
    file system in tests;
  - `cache.db` version 2 adds the list tables by `MIGRATION_1_2` (entries survive the upgrade,
    `RoomMigrationTest` builds a version-1 file and opens it through the factory); any other version
    change without a migration, or a format bump = drop; a foreign identity hash (same version, other
    schema) is caught and the file recreated — Room alone would throw. A migration's statements are
    the exported schema's (Room validates the migrated file; a drift shows as a discard);
  - every `ListStore` write call is one write transaction (`withWriteTransaction`); rows carry a
    `position` per list (`replace` renumbers, `merge` appends after the maximum);
  - prefix operations use `key >= :p AND substr(key, 1, length(:p)) = :p` (exact for any key; a
    range with a sentinel upper bound misses non-ASCII keys — the contract suite catches it).
- **Schemas:** `schemas/` is committed (Room 3 requires it); it documents `cache.db` versions.
- **Tests:** `src/sqliteTest` (jvm + iOS simulator) runs `CacheStoreContract` and `ListStoreContract`
  plus file-level proofs (migration, revision collection across processes); Android host tests cannot load the bundled SQLite JNI library, so only `commonTest` runs
  there. Android device behaviour (real `noBackupFilesDir`, file-based encryption, process kill by
  the OS) is not covered by host tests.
- **Later (not in this artifact yet):** `outbox.db` with real migrations, `RoomPartitionHost` for typed brick stores,
  budgets/eviction.
- **Do not:** expose a Room type publicly; add a `@Database` an app must declare; read the account
  id back from a directory name.
