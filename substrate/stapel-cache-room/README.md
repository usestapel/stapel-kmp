# stapel-cache-room

Durable storage for `stapel-cache` on Room 3 and the bundled SQLite driver. Optional: without it
the cache keeps entries in memory only. Targets: Android, iOS (arm64, simulator arm64), JVM.

Coordinates: `io.github.usestapel:stapel-cache-room` (group provisional until the first publish).

```kotlin
// Composition root — the only place that knows storage exists.
val options = RoomCacheOptions(ioContext = dispatchers.io)          // StapelDispatchers from stapel-runtime
val storeFactory = RoomCacheStoreFactory(context, options)          // Android: noBackupFilesDir
// val storeFactory = RoomCacheStoreFactory(options)                // iOS: Application Support, no backup
// val storeFactory = RoomCacheStoreFactory(desktopDataDirectory("com.example.shop"), options)  // JVM desktop
val cache = StapelCache(StapelCacheConfig(storeFactory = storeFactory, /* … */))
```

With Koin, `cacheRoomModule()` picks the same directories by itself. On desktop the directory is
per app, named by the bound `StapelEnvironment`'s `client.appId` (`stapelApp { environment(...) }`
binds it): `desktopDataDirectory(appId)` is

| OS | Directory |
|---|---|
| macOS | `~/Library/Application Support/<appId>` |
| Windows | `%LOCALAPPDATA%\<appId>` (machine-local, not roaming) |
| Linux, others | `$XDG_DATA_HOME/<appId>` when absolute, else `~/.local/share/<appId>` |

`cacheRoomModule(directory = …)` overrides it on any platform. On the JVM, `stapel-cache/` and every
partition directory are owner-only (`rwx------`; a wider existing directory is tightened) on POSIX
file systems; on Windows they inherit the user profile's ACL, which already excludes other users —
nothing is encrypted at rest (no SQLCipher in v0; plug an encrypting `SQLiteDriver` through
`RoomCacheOptions.driver`). Not the OS cache directory: the cache is the app's offline copy, removed
by the app's logout wipe, not by a cleaner.

Files: `<root>/stapel-cache/app/cache.db` and `<root>/stapel-cache/u-<16 hex>/cache.db` (one
directory per account, named by a hash of the account id). A wipe closes the database and deletes
the partition directory with every file in it. `cache.db` is disposable: an unreadable file, a
foreign schema or another store format is deleted and recreated empty (`RoomCacheOptions.onDiscarded`).
Consumers apply no KSP and no Room plugin; the generated Room code ships inside this artifact.
