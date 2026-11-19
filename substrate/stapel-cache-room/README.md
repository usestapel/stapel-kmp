# stapel-cache-room

Durable storage for `stapel-cache` on Room 3 and the bundled SQLite driver. Optional: without it
the cache keeps entries in memory only. Targets: Android, iOS (arm64, simulator arm64), JVM.

Coordinates: `io.github.usestapel:stapel-cache-room` (group provisional until the first publish).

```kotlin
// Composition root — the only place that knows storage exists.
val options = RoomCacheOptions(ioContext = dispatchers.io)          // StapelDispatchers from stapel-runtime
val storeFactory = RoomCacheStoreFactory(context, options)          // Android: noBackupFilesDir
// val storeFactory = RoomCacheStoreFactory(options)                // iOS: Application Support, no backup
// val storeFactory = RoomCacheStoreFactory("/path/data", options)  // JVM desktop: the app's data directory
val cache = StapelCache(StapelCacheConfig(storeFactory = storeFactory, /* … */))
```

Files: `<root>/stapel-cache/app/cache.db` and `<root>/stapel-cache/u-<16 hex>/cache.db` (one
directory per account, named by a hash of the account id). A wipe closes the database and deletes
the partition directory with every file in it. `cache.db` is disposable: an unreadable file, a
foreign schema or another store format is deleted and recreated empty (`RoomCacheOptions.onDiscarded`).
Consumers apply no KSP and no Room plugin; the generated Room code ships inside this artifact.
