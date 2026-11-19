@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package stapel.cache.session

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.serialization.builtins.serializer
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.cache.CacheStoreFactory
import stapel.cache.ManagedStapelCache
import stapel.cache.MemoryCacheStoreFactory
import stapel.cache.PartitionId
import stapel.cache.StapelCache
import stapel.cache.di.cacheModule
import stapel.cache.session.di.cacheSessionModule
import stapel.config.StapelEnvironment
import stapel.di.stapelKoin
import stapel.network.di.networkModule
import stapel.runtime.PlatformContext
import stapel.runtime.di.runtimeModule
import stapel.session.RefreshOutcome
import stapel.session.SessionKind
import stapel.session.SessionManager
import stapel.session.TokenPair
import stapel.session.TokenRefresher
import stapel.session.di.sessionModule
import stapel.session.vault.InMemoryTokenVault
import stapel.testing.FakeTransport
import stapel.testing.TestDispatchers
import stapel.testing.di.testOverridesModule
import stapel.testing.testEnvironment
import stapel.wire.ApiTransport
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** runtime + config + network + session + cache + cache-session composed through `stapelKoin`. */
class GraphTest {
    private val refresher: Module = module { single<TokenRefresher> { TokenRefresher { RefreshOutcome.Transient("tests") } } }

    private fun token(userId: String): String {
        val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        return "eyJhbGciOiJub25lIn0." + b64.encode("""{"user_id":"$userId","exp":4102444800}""".encodeToByteArray()) + ".sig"
    }

    @OptIn(KoinInternalApi::class)
    @Test
    fun everyBindingResolvesAndTheCacheFollowsTheSession() = runTest {
        val platform = object : PlatformContext {}
        val storage = MemoryCacheStoreFactory()
        val app = stapelKoin(
            modules = listOf(
                runtimeModule(platform),
                networkModule(testEnvironment()),
                sessionModule(),
                cacheModule(),
                cacheSessionModule(),
                refresher,
                module { single<CacheStoreFactory> { storage } },
            ),
            overrides = listOf(
                testOverridesModule(
                    appScope = backgroundScope,
                    dispatchers = TestDispatchers(StandardTestDispatcher(testScheduler)),
                    transport = FakeTransport(),
                    vault = InMemoryTokenVault(),
                ),
            ),
            global = false,
        )
        val koin = app.koin
        try {
            val definitions = koin.instanceRegistry.instances.values.map { it.beanDefinition }.distinct()
                .filter { it.scopeQualifier == koin.scopeRegistry.rootScope.scopeQualifier }
            assertTrue(definitions.size >= 15, "graph too small: ${definitions.size}")
            for (d in definitions) {
                koin.get<Any>(d.primaryType, d.qualifier)
                d.secondaryTypes.forEach { koin.get<Any>(it, d.qualifier) }
            }
            assertSame(koin.get<ManagedStapelCache>(), koin.get<StapelCache>())
            koin.get<ApiTransport>()
            koin.get<StapelEnvironment>()
            koin.get<CacheSessionBinding>()

            val session = koin.get<SessionManager>()
            session.restore()
            session.adopt(TokenPair(token("42"), "r"), SessionKind.Authenticated)
            runCurrent()
            val me = stapel.cache.QuerySpec(
                stapel.wire.CacheKey.of("auth", "me"), String.serializer(), "v1", stapel.cache.ReadPolicy.Default,
            )
            koin.get<ManagedStapelCache>().write(me, "me")
            runCurrent()
            // The bound store is the cache's, and the binding put user data in the session's account partition.
            assertEquals(listOf(PartitionId.account("42")), storage.listPartitions())
        } finally {
            app.close()
        }
    }
}
