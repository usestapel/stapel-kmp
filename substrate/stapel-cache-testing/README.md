# stapel-cache-testing

Test kit for `stapel-cache` storage. `CacheStoreContract` is the behaviour every
`CacheStoreFactory` must show; the memory store and the Room store both pass it, which is what
makes the durable store an opt-in with no behaviour change.

Coordinates: `io.github.usestapel:stapel-cache-testing` (test scope).

```kotlin
class MyStoreTest {
    @Test fun contract() = runTest {
        CacheStoreContract.verify { MyStoreFactory(tempDir()) }   // throws naming every failed case
    }
}
```
