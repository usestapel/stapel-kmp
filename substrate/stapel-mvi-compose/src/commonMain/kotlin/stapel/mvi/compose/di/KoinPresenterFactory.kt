package stapel.mvi.compose.di

import org.koin.core.Koin
import org.koin.core.parameter.parametersOf
import stapel.mvi.Presenter
import stapel.mvi.PresenterContext
import stapel.mvi.compose.PresenterFactory
import kotlin.reflect.KClass

/**
 * [PresenterFactory] over Koin. A domain library registers its presenters in its own module factory
 * as `factory { (ctx: PresenterContext) -> LoginPresenter(get(), ctx) }`; screen arguments follow the
 * context: `factory { (ctx: PresenterContext, id: String) -> ThreadPresenter(get(), ctx, id) }`.
 * The app provides this once through `LocalPresenterFactory`.
 */
public class KoinPresenterFactory(private val koin: Koin) : PresenterFactory {
    override fun <P : Presenter<*, *, *>> create(type: KClass<P>, context: PresenterContext, args: List<Any?>): P =
        koin.get(type, qualifier = null) { parametersOf(context, *args.toTypedArray()) }
}
