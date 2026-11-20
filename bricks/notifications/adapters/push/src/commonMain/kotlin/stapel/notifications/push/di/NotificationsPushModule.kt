package stapel.notifications.push.di

import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.notifications.api.NotificationsApi
import stapel.notifications.di.notificationsModule
import stapel.notifications.push.MarkReadOnOpen
import stapel.notifications.push.NotificationsPushRegistry
import stapel.push.PushDeviceRegistry
import stapel.push.PushOpenListener
import stapel.runtime.StapelLogger

/**
 * Binds stapel-push's [PushDeviceRegistry] over the notifications API and [MarkReadOnOpen] as a
 * [PushOpenListener]; includes `notificationsModule()`. Install next to `pushModule(PushOptions(routing =
 * notificationsPushRouting(...)))`.
 */
public fun notificationsPushModule(): Module = module {
    includes(notificationsModule())
    single<PushDeviceRegistry> { NotificationsPushRegistry(get<NotificationsApi>()) }
    single<PushOpenListener> { MarkReadOnOpen(get<NotificationsApi>(), get<StapelLogger>()) }
}
