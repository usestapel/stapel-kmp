package stapel.auth.repo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import stapel.auth.api.AuthApi
import stapel.auth.api.AuthContract
import stapel.auth.api.AuthInvalidation
import stapel.auth.api.AuthKeys
import stapel.auth.api.AuthOperations
import stapel.auth.api.model.SessionResponse
import stapel.auth.api.model.User
import stapel.auth.error.AuthError
import stapel.auth.internal.toDomain
import stapel.auth.model.AuthCapabilities
import stapel.auth.model.AuthUser
import stapel.auth.model.SessionDevice
import stapel.cache.Persistence
import stapel.cache.QuerySpec
import stapel.cache.ReadOptions
import stapel.cache.ReadPolicy
import stapel.cache.StapelCache
import stapel.result.StapelResult
import stapel.result.state.Resource
import stapel.result.state.map
import stapel.wire.KeyPattern
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import stapel.auth.api.model.AuthCapabilities as AuthCapabilitiesDto

/**
 * `GET /capabilities/`: public, app-scoped, cache-first (one hour), revalidated by [refresh]. Drives
 * the method chooser.
 *
 * @param api the generated client.
 * @param cache the app's cache.
 */
public class CapabilitiesRepository(private val api: AuthApi, private val cache: StapelCache) {
    private val spec: QuerySpec<AuthCapabilitiesDto> = QuerySpec(
        key = AuthKeys.getCapabilities(),
        serializer = AuthCapabilitiesDto.serializer(),
        schemaHash = AuthContract.SCHEMA_SHA256,
        policy = ReadPolicy.CacheFirst(freshFor = 1.hours, retainFor = 24.hours),
    )

    /** The capabilities as a load state. */
    public fun capabilities(): Flow<Resource<AuthCapabilities>> =
        cache.observe(spec) { api.getCapabilities() }.map { r -> r.map { it.toDomain() } }

    /** One settled read honouring the cache policy. */
    public suspend fun get(): StapelResult<AuthCapabilities, AuthError> = settle(cache.get(spec) { api.getCapabilities() }) { it.toDomain() }

    /** Reads from the network and replaces the cached value. */
    public suspend fun refresh(): StapelResult<AuthCapabilities, AuthError> {
        cache.invalidate(KeyPattern.exact(spec.key))
        return get()
    }
}

/**
 * The signed-in user's profile (`GET /me/`): user-scoped (the cache partition of the account, wiped on
 * every session end), stale-while-revalidate.
 *
 * @param api the generated client.
 * @param cache the app's cache (its partitions follow the session).
 */
public class AccountRepository(private val api: AuthApi, private val cache: StapelCache) {
    private val spec: QuerySpec<User> = QuerySpec(
        key = AuthKeys.getMe(),
        serializer = User.serializer(),
        schemaHash = AuthContract.SCHEMA_SHA256,
        policy = ReadPolicy.StaleWhileRevalidate(freshFor = 30.seconds, retainFor = 24.hours),
    )

    /** The profile as a load state. Waits in `Loading` until the session's partition is open. */
    public fun me(): Flow<Resource<AuthUser>> = cache.observe(spec) { api.getMe() }.map { r -> r.map { it.toDomain() } }

    /** One settled read. */
    public suspend fun get(): StapelResult<AuthUser, AuthError> = settle(cache.get(spec) { api.getMe() }) { it.toDomain() }
}

/**
 * The signed-in user's sessions and devices: user-scoped, kept in memory only (never written to
 * disk), stale-while-revalidate for 30 s. Revocations are online-only (no auth mutation is ever
 * queued offline) and invalidate the list per the cache plan.
 *
 * @param api the generated client.
 * @param cache the app's cache (its partitions follow the session).
 */
public class SessionsRepository(private val api: AuthApi, private val cache: StapelCache) {
    private val spec: QuerySpec<List<SessionResponse>> = QuerySpec(
        key = AuthKeys.listSessions(),
        serializer = ListSerializer(SessionResponse.serializer()),
        schemaHash = AuthContract.SCHEMA_SHA256,
        policy = ReadPolicy.StaleWhileRevalidate(freshFor = 30.seconds, retainFor = 5.minutes),
        persistence = Persistence.MemoryOnly,
    )

    /** The list as a load state; the current session is marked [SessionDevice.isCurrent]. */
    public fun sessions(): Flow<Resource<List<SessionDevice>>> =
        cache.observe(spec) { api.listSessions() }.map { r -> r.map { list -> list.map { it.toDomain() } } }

    /** One read from the network. */
    public suspend fun load(): StapelResult<List<SessionDevice>, AuthError> =
        settle(cache.get(spec, ReadOptions(policy = ReadPolicy.NetworkOnly)) { api.listSessions() }) { list -> list.map { it.toDomain() } }

    /** Revokes one session (`DELETE /sessions/{id}/`). */
    public suspend fun revoke(sessionId: String): StapelResult<Unit, AuthError> =
        when (val r = api.deleteSessionsBySessionId(sessionId)) {
            is StapelResult.Ok -> {
                AuthInvalidation.after(AuthOperations.deleteSessionsBySessionId.id).forEach { cache.invalidate(it) }
                StapelResult.Ok(Unit)
            }
            is StapelResult.Err -> StapelResult.Err(AuthError.of(r.error))
        }

    /** Revokes every session except this one (`DELETE /sessions/`). */
    public suspend fun revokeOthers(): StapelResult<Unit, AuthError> =
        when (val r = api.deleteSessions()) {
            is StapelResult.Ok -> {
                AuthInvalidation.after(AuthOperations.deleteSessions.id).forEach { cache.invalidate(it) }
                StapelResult.Ok(Unit)
            }
            is StapelResult.Err -> StapelResult.Err(AuthError.of(r.error))
        }
}

private inline fun <V, R> settle(result: StapelResult<V, stapel.result.StapelError>, map: (V) -> R): StapelResult<R, AuthError> =
    when (result) {
        is StapelResult.Ok -> StapelResult.Ok(map(result.value))
        is StapelResult.Err -> StapelResult.Err(AuthError.of(result.error))
    }
