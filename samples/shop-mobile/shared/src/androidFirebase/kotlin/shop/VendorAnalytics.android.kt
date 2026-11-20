package shop

import org.koin.core.module.Module
import stapel.analytics.firebase.di.firebaseAnalyticsModule

/**
 * `-Pshop.firebase=true`: Google Analytics for Firebase next to the log sink. Without
 * `androidApp/google-services.json` it logs `stapel.analytics unavailable firebase unconfigured
 * google-services.json` and stays a no-op; a release build refuses to assemble without the file.
 */
internal actual fun vendorAnalyticsModules(): List<Module> = listOf(firebaseAnalyticsModule())
