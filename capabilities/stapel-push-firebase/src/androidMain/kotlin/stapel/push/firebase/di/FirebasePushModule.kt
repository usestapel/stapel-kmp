package stapel.push.firebase.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.push.PushProvider
import stapel.push.firebase.androidFirebasePushProvider

/** Binds the Firebase [PushProvider] over FCM. Needs `runtimeModule()` (platform, dispatchers, logger). */
public fun firebasePushModule(): Module = module {
    single<PushProvider> { androidFirebasePushProvider(get(), get(), get()) }
}
