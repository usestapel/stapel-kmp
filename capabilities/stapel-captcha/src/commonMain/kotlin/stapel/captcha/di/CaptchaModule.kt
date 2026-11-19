package stapel.captcha.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.captcha.CaptchaPlugin
import stapel.captcha.CaptchaSolver
import stapel.di.contribute
import stapel.network.NetworkPlugin

/**
 * Contributes the [CaptchaPlugin] to `networkModule()`'s plug-in set. [solver] is the app's (a web
 * view with the provider widget, a test double); the library ships none.
 */
public fun captchaModule(solver: CaptchaSolver, proactive: Boolean = false): Module = module {
    contribute<NetworkPlugin>("captcha") { CaptchaPlugin(solver, proactive) }
}
