package stapel.profiles.internal

import kotlinx.serialization.KSerializer
import stapel.cache.Persistence
import stapel.cache.QuerySpec
import stapel.cache.ReadPolicy
import stapel.profiles.api.ProfilesContract
import stapel.profiles.api.ProfilesKeys
import stapel.wire.CacheKey
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The read spec of [operationId] at [key], its policy and persistence taken from the cache plan
 * (`contracts/profiles/cache.json`, generated into [ProfilesKeys.hints]); the durations are this
 * library's: catalogues an hour, the own profile and lists 30 s fresh, relationships network-first.
 */
internal fun <T> planned(operationId: String, key: CacheKey, serializer: KSerializer<T>): QuerySpec<T> {
    val hint = ProfilesKeys.hints[operationId]
    val policy = when (hint?.policy) {
        "network_only" -> ReadPolicy.NetworkOnly
        "network_first" -> ReadPolicy.NetworkFirst(retainFor = 24.hours)
        "cache_first" -> ReadPolicy.CacheFirst(freshFor = 1.hours, retainFor = 24.hours)
        "stale_while_revalidate" -> ReadPolicy.StaleWhileRevalidate(freshFor = 30.seconds, retainFor = 24.hours)
        else -> ReadPolicy.StaleWhileRevalidate(freshFor = 30.seconds, retainFor = 5.minutes)
    }
    val persistence = when {
        hint?.sensitive == true || hint?.persistence == "memory_only" -> Persistence.MemoryOnly
        hint?.persistence == "never" -> Persistence.Never
        else -> Persistence.Durable
    }
    return QuerySpec(key, serializer, ProfilesContract.SCHEMA_SHA256, policy, persistence)
}
