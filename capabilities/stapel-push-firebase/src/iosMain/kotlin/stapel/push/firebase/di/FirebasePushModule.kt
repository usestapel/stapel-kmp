package stapel.push.firebase.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.push.DevicePlatform
import stapel.push.PushProvider
import stapel.push.firebase.FirebasePushBridge
import stapel.push.firebase.firebasePushProvider

/**
 * Binds the Firebase [PushProvider] over the app's Swift [bridge] (`StapelFirebasePush.shared`,
 * handed over by the app shell before the first screen). A null bridge reports unsupported.
 */
public fun firebasePushModule(bridge: FirebasePushBridge?): Module = module {
    single<PushProvider> { firebasePushProvider(bridge, DevicePlatform.Ios, get(), get()) }
}
