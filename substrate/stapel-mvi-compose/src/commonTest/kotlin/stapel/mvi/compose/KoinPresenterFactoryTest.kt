package stapel.mvi.compose

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import stapel.mvi.Presenter
import stapel.mvi.PresenterContext
import stapel.mvi.compose.di.KoinPresenterFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class Greeter(val greeting: String)

class ThreadPresenter(val greeter: Greeter, val context: PresenterContext, val threadId: String) :
    Presenter<String, Unit, Unit>("", context) {
    override suspend fun handle(intent: Unit) = Unit
}

class KoinPresenterFactoryTest {
    @Test
    fun koinBuildsThePresenterWithTheContextAndTheScreenArguments() {
        val app = koinApplication {
            modules(
                module {
                    single { Greeter("hi") }
                    factory { (ctx: PresenterContext, id: String) -> ThreadPresenter(get(), ctx, id) }
                },
            )
        }
        val context = PresenterContext(CoroutineScope(Job()))
        val p = KoinPresenterFactory(app.koin).create(ThreadPresenter::class, context, listOf("t-42"))
        assertEquals("t-42", p.threadId)
        assertSame(context, p.context)
        assertEquals("hi", p.greeter.greeting)
        app.close()
    }
}
