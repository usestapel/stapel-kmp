# stapel-cache — module map

- **Provides (storage SPI):** `CacheStore` (get / put / delete / prefix invalidate / prefix delete /
  count / clear), `StoredEntry`, `CacheStoreFactory` (open / destroy / listPartitions, `durable`),
  `PartitionStores`, `PartitionId` (`App`, `account(id)` = `u-` + 16 hex of SHA-256),
  `MemoryCacheStoreFactory` (the default).
- **Seams:** a durable store implements `CacheStoreFactory` (see `stapel-cache-room`) and must pass
  `CacheStoreContract` from `stapel-cache-testing`.
- **Invariants:**
  - stores are write-through storage with no observation; the memory store and a durable store
    are interchangeable (one contract suite);
  - storage keys are segments each followed by U+001F, so a segment prefix is a string prefix and
    `chat/conv` never matches `chat/conversations`;
  - one partition per account plus `app`; isolation is by partition, never by filtering;
  - the memory store forgets a partition on close and reports `durable = false`; it never claims
    durability;
  - wiring is explicit: no classpath or service-loader discovery of storage.
- **Do not:** put credentials or sensitive responses into a store; depend on Room here.
