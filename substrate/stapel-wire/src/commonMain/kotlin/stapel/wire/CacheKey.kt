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
     * Stable, reversible storage form of namespace, path and arguments (not [scope], which names the
     * partition the entry lives in): every segment is escaped and terminated, so the storage key of
     * every key a [KeyPattern] matches starts with [KeyPattern.storagePrefix] and no other key's does
     * (`chat/conv` is not a prefix of `chat/conversations`). Decoded by [fromStorageKey].
     */
    public val storageKey: String = buildString {
        appendSegment(namespace)
        path.forEach { appendSegment(it) }
        args?.let { append(ARGS_MARK); appendSegment(it) }
    }

    /** Keys are equal when their [scope] and [storageKey] are. */
    override fun equals(other: Any?): Boolean = other is CacheKey && other.scope == scope && other.storageKey == storageKey

    /** Hash of [scope] and [storageKey]. */
    override fun hashCode(): Int = scope.hashCode() * 31 + storageKey.hashCode()

    /** Debug form with segments separated by `/`. */
    override fun toString(): String =
        "CacheKey(" + scope.name + ":" + (listOf(namespace) + path + listOfNotNull(args)).joinToString("/") + ")"

    /** Holder of [of]. */
    public companion object {
        internal const val TERMINATOR: Char = '\u001F'
        internal const val ESCAPE: Char = '\u001E'
        internal const val ARGS_MARK: Char = '\u001D'

        internal fun StringBuilder.appendSegment(segment: String) {
            for (c in segment) {
                when (c) {
                    ESCAPE -> append(ESCAPE).append('0')
                    TERMINATOR -> append(ESCAPE).append('1')
                    ARGS_MARK -> append(ESCAPE).append('2')
                    else -> append(c)
                }
            }
            append(TERMINATOR)
        }

        /**
         * The key [storageKey] was made from, in [scope]; `null` when [storageKey] is not in the
         * [CacheKey.storageKey] form.
         */
        public fun fromStorageKey(storageKey: String, scope: CacheScope = CacheScope.User): CacheKey? {
            val segments = mutableListOf<String>()
            var args: String? = null
            var argsNext = false
            val current = StringBuilder()
            var i = 0
            while (i < storageKey.length) {
                when (val c = storageKey[i]) {
                    ESCAPE -> {
                        if (i + 1 >= storageKey.length) return null
                        current.append(
                            when (storageKey[i + 1]) {
                                '0' -> ESCAPE
                                '1' -> TERMINATOR
                                '2' -> ARGS_MARK
                                else -> return null
                            },
                        )
                        i++
                    }
                    ARGS_MARK -> {
                        if (current.isNotEmpty() || argsNext) return null
                        argsNext = true
                    }
                    TERMINATOR -> {
                        if (args != null) return null
                        if (argsNext) args = current.toString() else segments += current.toString()
                        current.clear()
                    }
                    else -> {
                        if (args != null) return null
                        current.append(c)
                    }
                }
                i++
            }
            if (current.isNotEmpty() || segments.isEmpty() || segments[0].isEmpty()) return null
            if (argsNext && args == null) return null
            return CacheKey(scope, segments[0], segments.drop(1), args)
        }

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
            require(namespace.isNotEmpty()) { "Empty namespace" }
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
    /** The one key this pattern matches, for a pattern made by [exact]; `null` for a prefix pattern. */
    public val exactKey: CacheKey?,
) {
    /** Whether this pattern names exactly one key ([exactKey]) rather than a prefix. */
    public val isExact: Boolean get() = exactKey != null

    /**
     * The storage-key prefix of the matched keys: every [CacheKey.storageKey] this pattern matches starts
     * with it and no other does (segments are terminated). For an exact pattern it is the key's whole
     * [CacheKey.storageKey]; scope is not part of it (it selects the partition).
     */
    public val storagePrefix: String = exactKey?.storageKey ?: buildString {
        with(CacheKey) {
            appendSegment(namespace)
            pathPrefix.forEach { appendSegment(it) }
        }
    }

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
