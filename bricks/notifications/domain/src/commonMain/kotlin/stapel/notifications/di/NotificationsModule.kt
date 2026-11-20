package stapel.notifications.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.notifications.api.NotificationsApi
import stapel.notifications.api.NotificationsApiClient
import stapel.wire.ApiTransport

/**
 * The notifications library's Koin module: binds [NotificationsApi] on the authenticated
 * [ApiTransport]. Needs `networkModule()` and `sessionModule()` (every operation is the caller's).
 */
public fun notificationsModule(): Module = module {
    single<NotificationsApi> { NotificationsApiClient(get<ApiTransport>()) }
}
