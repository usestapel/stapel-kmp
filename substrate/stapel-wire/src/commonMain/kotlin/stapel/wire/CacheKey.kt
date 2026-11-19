package stapel.wire

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Which cache partition an entry lives in: the signed-in account's, or the app's (public catalogues). */
public enum class CacheScope { User, App }

/**
 * Hierarchical cache key: [namespace] (the brick, e.g. `"auth"`), [path] segments (`"sessions"`), and
 * the canonical JSON of the call's arguments (object keys sorted, no whitespace). It mirrors the web's
 * query-key tuple `["auth", "sessions", {params}]`; invalidation is by prefix ([KeyPattern]).
 *
 * Generated `<Brick>Keys` objects are the only producers of keys in brick code.
 */
public class CacheKey private constructor(
    /** Partition of the entry. */
    public val scope: CacheScope,
    /** Brick key, the first tuple element. */
    public val namespace: String,
    /** Path segments after the namespace. */
    public val path: List<String>,
    /** Canonical JSON of the arguments, or `null` when the key has none. */
    public val args: String?,
) {
    /**
     * Stable storage form: segments joined by U+001F, so every [KeyPattern] prefix is a contiguous
     * range in a sorted store.
     */
    public val storageKey: String =
        (listOf(scope.name, namespace) + path + listOfNotNull(args)).joinToString(SEPARATOR)

    /** Keys are equal when their [storageKey]s are. */
    override fun equals(other: Any?): Boolean = other is CacheKey && other.storageKey == storageKey

    /** Hash of [storageKey]. */
    override fun hashCode(): Int = storageKey.hashCode()

    /** Debug form with segments separated by `/`. */
    override fun toString(): String = "CacheKey(" + storageKey.replace(SEPARATOR, "/") + ")"

    /** Holder of [of]. */
    public companion object {
        internal const val SEPARATOR: String = "\u001F"

        /**
         * A key in [namespace] with [path] segments and optional [args]; arguments whose value is JSON
         * `null` are dropped, so "not passed" and "passed as null" share a key.
         */
        public fun of(
            namespace: String,
            vararg path: String,
            args: JsonElement? = null,
            scope: CacheScope = CacheScope.User,
        ): CacheKey {
            require(namespace.isNotEmpty() && SEPARATOR !in namespace) { "Invalid namespace \"$namespace\"" }
            path.forEach { require(SEPARATOR !in it) { "Invalid path segment \"$it\"" } }
            val canonical = args?.let(::canonical)?.takeUnless { it is JsonObject && it.isEmpty() }
            return CacheKey(scope, namespace, path.toList(), canonical?.toString())
        }

        private fun canonical(element: JsonElement): JsonElement = when (element) {
            is JsonObject -> JsonObject(
                element.entries
                    .filter { it.value !is kotlinx.serialization.json.JsonNull }
                    .sortedBy { it.key }
                    .associate { it.key to canonical(it.value) },
            )
            is JsonArray -> JsonArray(element.map(::canonical))
            else -> element
        }
    }
}

/**
 * A set of keys to invalidate: every key of [namespace] whose path starts with [pathPrefix]
 * (`KeyPattern.of("auth", "sessions")` reaches every argument variant), or exactly one key.
 */
public class KeyPattern private constructor(
    /** Brick key the pattern is confined to. */
    public val namespace: String,
    /** Leading path segments a matching key must start with. */
    public val pathPrefix: List<String>,
    private val exactKey: CacheKey?,
) {
    /** Whether [key] is covered by this pattern. */
    public fun matches(key: CacheKey): Boolean {
        exactKey?.let { return it == key }
        return key.namespace == namespace &&
            key.path.size >= pathPrefix.size &&
            key.path.subList(0, pathPrefix.size) == pathPrefix
    }

    /** Patterns are equal when they match the same keys by construction. */
    override fun equals(other: Any?): Boolean =
        other is KeyPattern && other.namespace == namespace && other.pathPrefix == pathPrefix && other.exactKey == exactKey

    /** Hash over namespace, prefix and exact key. */
    override fun hashCode(): Int = (namespace.hashCode() * 31 + pathPrefix.hashCode()) * 31 + exactKey.hashCode()

    /** Debug form. */
    override fun toString(): String =
        exactKey?.let { "KeyPattern(exact $it)" } ?: "KeyPattern(" + (listOf(namespace) + pathPrefix).joinToString("/") + "/*)"

    /** Holder of [of] and [exact]. */
    public companion object {
        /** Every key of [namespace] whose path starts with [pathPrefix]. */
        public fun of(namespace: String, vararg pathPrefix: String): KeyPattern =
            KeyPattern(namespace, pathPrefix.toList(), null)

        /** Exactly [key]. */
        public fun exact(key: CacheKey): KeyPattern = KeyPattern(key.namespace, key.path, key)
    }
}

/**
 * Per-operation cache hints from the brick's cache plan (`contracts/<brick>/cache.json`), generated
 * into `<Brick>Keys.hints`. Policies and their durations are `stapel-cache`'s; these are the plan's
 * declared choices, or `null` where the plan leaves the default.
 */
public class CacheHint(
    /** `network_only`, `network_first`, `cache_first` or `stale_while_revalidate`; `null` = cache default. */
    public val policy: String?,
    /** `durable`, `memory_only` or `never`; `null` = cache default. */
    public val persistence: String?,
    /** Partition of the operation's keys. */
    public val scope: CacheScope,
    /** Whether the payload must never be persisted (tokens, secrets). */
    public val sensitive: Boolean,
    /** Offline-queue idempotency class of a mutation (`natural` or `keyed`); `null` = never queued. */
    public val offline: String?,
) {
    /** Hints are equal when every field is. */
    override fun equals(other: Any?): Boolean =
        other is CacheHint && other.policy == policy && other.persistence == persistence && other.scope == scope &&
            other.sensitive == sensitive && other.offline == offline

    /** Hash over every field. */
    override fun hashCode(): Int = listOf(policy, persistence, scope, sensitive, offline).hashCode()

    /** Debug form. */
    override fun toString(): String =
        "CacheHint(policy=$policy, persistence=$persistence, scope=$scope, sensitive=$sensitive, offline=$offline)"
}
