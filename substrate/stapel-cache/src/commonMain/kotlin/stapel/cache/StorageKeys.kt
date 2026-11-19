package stapel.cache

import kotlinx.serialization.json.Json
import stapel.wire.CacheKey
import stapel.wire.CacheScope
import stapel.wire.KeyPattern

/*
 * Storage form of keys: every segment is escaped and followed by U+001F, so a segment-prefix of a
 * key is exactly a string prefix of its storage key ("chat/conv" never reaches "chat/conversations").
 * Escape: U+001E -> U+001E '0', U+001F -> U+001E '1'; a raw U+001F therefore only ever terminates.
 * The arguments segment is marked (U+001E 'a') so that it can never be mistaken for a path segment.
 */

private const val ESC = '\u001E'

private fun StringBuilder.segment(value: String, marker: Char? = null): StringBuilder {
    if (marker != null) append(ESC).append(marker)
    for (c in value) {
        when (c) {
            ESC -> append(ESC).append('0')
            KEY_SEPARATOR -> append(ESC).append('1')
            else -> append(c)
        }
    }
    return append(KEY_SEPARATOR)
}

/** Storage key of [key] (scope is the partition, so it is not part of the key). */
internal fun storageKeyOf(key: CacheKey): String = buildString {
    segment(key.namespace)
    key.path.forEach { segment(it) }
    key.args?.let { segment(it, marker = 'a') }
}

/** Storage-key prefix every key matched by the prefix part of [pattern] starts with. */
internal fun storagePrefixOf(pattern: KeyPattern): String = buildString {
    segment(pattern.namespace)
    pattern.pathPrefix.forEach { segment(it) }
}

/**
 * Whether [pattern] names exactly one key ([KeyPattern.exact]) rather than a prefix. The wire type
 * keeps that private; an exact pattern is the only kind that does not match a key one segment
 * longer than its own path.
 */
internal fun isExact(pattern: KeyPattern): Boolean =
    !pattern.matches(CacheKey.of(pattern.namespace, *(pattern.pathPrefix + PROBE_SEGMENT).toTypedArray()))

private const val PROBE_SEGMENT = "\u0000exactness-probe"

/** The key a storage key was made from (inverse of [storageKeyOf]); null for a foreign form. */
internal fun cacheKeyOf(storageKey: String, scope: CacheScope): CacheKey? {
    val segments = mutableListOf<String>()
    var args: String? = null
    val current = StringBuilder()
    var isArgs = false
    var i = 0
    while (i < storageKey.length) {
        val c = storageKey[i]
        when {
            c == ESC && i + 1 < storageKey.length -> {
                when (storageKey[i + 1]) {
                    '0' -> current.append(ESC)
                    '1' -> current.append(KEY_SEPARATOR)
                    'a' -> if (current.isEmpty()) isArgs = true else return null
                    else -> return null
                }
                i += 2
                continue
            }
            c == KEY_SEPARATOR -> {
                if (isArgs) {
                    if (args != null) return null
                    args = current.toString()
                } else {
                    if (args != null) return null
                    segments += current.toString()
                }
                current.clear()
                isArgs = false
            }
            else -> current.append(c)
        }
        i++
    }
    if (current.isNotEmpty() || segments.isEmpty()) return null
    return try {
        CacheKey.of(
            segments.first(),
            *segments.drop(1).toTypedArray(),
            args = args?.let { Json.parseToJsonElement(it) },
            scope = scope,
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}
