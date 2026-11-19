# stapel-cache

The cache seam of Stapel clients: storage partitions, the storage SPI and the default memory
store. Pure Kotlin (`commonMain` only), every target.

Coordinates: `io.github.usestapel:stapel-cache` (group provisional until the first publish).

```kotlin
// Default wiring: nothing survives process death.
val storage: CacheStoreFactory = MemoryCacheStoreFactory()
// Opt-in durability (stapel-cache-room), one line in the composition root:
// val storage: CacheStoreFactory = RoomCacheStoreFactory(context)

val stores = storage.open(PartitionId.account(accountId))   // "u-<16 hex>", never the id itself
stores.entries.put(entry)
storage.destroy(PartitionId.account(accountId))             // logout: every trace gone
```
