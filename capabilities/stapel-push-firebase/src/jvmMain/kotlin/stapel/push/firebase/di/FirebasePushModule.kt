package stapel.push.firebase.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.push.PushProvider
import stapel.push.firebase.desktopFirebasePushProvider

/** Binds the desktop Firebase [PushProvider]: it compiles and answers unsupported ("desktop"). */
public fun firebasePushModule(): Module = module {
    single<PushProvider> { desktopFirebasePushProvider(get(), get()) }
}
