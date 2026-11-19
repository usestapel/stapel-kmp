package stapel.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.koin.core.error.DefinitionOverrideException
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private interface Greeter { fun greet(): String }
private class Plain(val text: String) : Greeter { override fun greet() = text }
private class SessionCache(val generation: String) { var closed = false }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StapelKoinTest {
    @Test
    fun aDuplicateUnqualifiedBindingAcrossLibrariesFailsAtStart() {
        val a = module { single<Greeter> { Plain("a") } }
        val b = module { single<Greeter> { Plain("b") } }
        assertFailsWith<DefinitionOverrideException> { stapelKoin(listOf(a, b), global = false) }
    }

    @Test
    fun overridesAreTheOnePlaceARedefinitionIsLegal() {
        val lib = module { single<Greeter> { Plain("library") } }
        val test = module { single<Greeter> { Plain("test") } }
        val app = stapelKoin(listOf(lib), overrides = listOf(test), global = false)
        assertEquals("test", app.koin.get<Greeter>().greet())
        app.close()
    }

    @Test
    fun withoutOverridesTheLibraryBindingStands() {
        val app = stapelKoin(listOf(module { single<Greeter> { Plain("library") } }), global = false)
        assertEquals("library", app.koin.get<Greeter>().greet())
        app.close()
    }

    @Test
    fun contributionsAreCollectedAndDuplicateKeysFail() {
        val a = module { contribute<Greeter>("a") { Plain("a") } }
        val b = module { contribute<Greeter>("b") { Plain("b") } }
        val app = stapelKoin(listOf(a, b), global = false)
        assertEquals(setOf("a", "b"), app.koin.getAll<Greeter>().map { it.greet() }.toSet())
        app.close()
        val dup = module { contribute<Greeter>("a") { Plain("again") } }
        assertFailsWith<DefinitionOverrideException> { stapelKoin(listOf(a, dup), global = false) }
    }

    @Test
    fun isolatedApplicationsDoNotShareStateWhenTheFactoryIsCalledPerApplication() {
        fun plainModule() = module { single { Plain("x") } }
        val one = stapelKoin(listOf(plainModule()), global = false)
        val two = stapelKoin(listOf(plainModule()), global = false)
        assertNotSame(one.koin.get<Plain>(), two.koin.get<Plain>())
        one.close()
        two.close()
    }

    @Test
    fun aSharedModuleValueSharesItsSingletons() {
        // Koin keeps a `single`'s instance in the Module's definition: this is why every library
        // exposes a factory function and never a `val` module.
        val shared = module { single { Plain("x") } }
        val one = stapelKoin(listOf(shared), global = false)
        val two = stapelKoin(listOf(shared), global = false)
        assertSame(one.koin.get<Plain>(), two.koin.get<Plain>())
        one.close()
        two.close()
    }

    @Test
    fun globalStartRegistersTheContext() {
        val app = stapelKoin(listOf(module { single { Plain("g") } }))
        try {
            assertSame(app.koin, org.koin.mp.KoinPlatformTools.defaultContext().get())
        } finally {
            org.koin.core.context.stopKoin()
        }
    }

    @Test
    fun sessionScopeFollowsTheGenerationAndClosesOnSignOut() = runTest {
        val closed = mutableListOf<String>()
        val m = module {
            sessionScoped {
                scoped { SessionCache(id) } onClose { it?.let { c -> c.closed = true; closed += c.generation } }
            }
        }
        val app = stapelKoin(listOf(m), global = false)
        val generations = MutableStateFlow<Long?>(null)
        val handle = app.koin.bindSessionScope(generations, backgroundScope)
        runCurrent()
        assertNull(handle.current.value)

        generations.value = 1
        runCurrent()
        val first = handle.current.value!!.get<SessionCache>()
        assertEquals(sessionScopeId(1), first.generation)

        generations.value = 2
        runCurrent()
        val second = handle.current.value!!.get<SessionCache>()
        assertTrue(first.closed, "the previous user's objects are closed before the next session opens")
        assertNotSame(first, second)

        generations.value = null
        runCurrent()
        assertNull(handle.current.value)
        assertEquals(listOf(sessionScopeId(1), sessionScopeId(2)), closed)
        app.close()
    }

    @Test
    fun qualifiersAreStable() {
        assertEquals(named("stapel.auth"), StapelQualifiers.module("auth"))
        assertEquals(named("stapel.contribution.errors.auth"), StapelQualifiers.contribution("errors.auth"))
    }
}
