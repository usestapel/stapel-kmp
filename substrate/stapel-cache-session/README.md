# stapel-cache-session

One binding between `stapel-session` and `stapel-cache`: the signed-in account's partition opens
(at cold start from the stored tokens, before any request), and every session end wipes it.

Coordinates: `io.github.usestapel:stapel-cache-session` (group provisional until the first publish).

```kotlin
// Composition root, once, after building the cache and the session:
val binding = CacheSessionBinding(cache, sessionManager, appScope)
sessionManager.restore()
```
