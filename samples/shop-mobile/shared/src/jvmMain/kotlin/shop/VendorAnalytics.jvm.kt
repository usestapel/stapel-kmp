package shop

import org.koin.core.module.Module

/** No vendor analytics provider in this build: the hub delivers to the log sink only. */
internal actual fun vendorAnalyticsModules(): List<Module> = emptyList()
