# stapel-cache

The cache every Stapel repository reads through: four read policies, `Flow<Resource<T>>` results,
single-flight fetches, invalidation by key prefix, account partitions and physical wipe. Pure
Kotlin (`commonMain` only), every target. Storage is pluggable and chosen explicitly: memory by
default, durable with `stapel-cache-room`.

Coordinates: `io.github.usestapel:stapel-cache` (group provisional until the first publish).

```kotlin
// Composition root
val cache = StapelCache(StapelCacheConfig(json = sharedJson, scope = appScope, clock = clock))
// opt-in durability: storeFactory = RoomCacheStoreFactory(context, RoomCacheOptions(dispatchers.io))

// Repository (spec and key come from generated code)
val me = QuerySpec(AuthKeys.me(), Me.serializer(), schemaHash = "…", policy = ReadPolicy.StaleWhileRevalidate(ZERO, INFINITE))
fun me(): Flow<Resource<Me>> = cache.observe(me) { api.me() }

// After a mutation
cache.invalidate(KeyPattern.of("auth", "sessions"))
```
