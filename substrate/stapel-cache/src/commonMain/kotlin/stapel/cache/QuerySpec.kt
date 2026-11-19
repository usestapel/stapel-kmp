package stapel.cache

import kotlinx.serialization.KSerializer
import stapel.wire.CacheKey

/**
 * Everything the cache needs to know about one read: generated per operation by the codegen.
 *
 * @property key the entry's key; its `scope` picks the partition (user or app).
 * @property serializer encodes the value for storage and decodes it back.
 * @property schemaHash hash of the operation's response schema; an entry stored under another
 *   hash is dropped on read (a miss), so a contract change never decodes old bytes as new types.
 * @property policy default read policy (overridable per call through [ReadOptions]).
 * @property persistence where the value may be kept.
 */
public class QuerySpec<T>(
    public val key: CacheKey,
    public val serializer: KSerializer<T>,
    public val schemaHash: String,
    public val policy: ReadPolicy = ReadPolicy.Default,
    public val persistence: Persistence = Persistence.Durable,
) {
    /** Debug form. */
    override fun toString(): String = "QuerySpec($key, policy=$policy, persistence=$persistence)"
}
