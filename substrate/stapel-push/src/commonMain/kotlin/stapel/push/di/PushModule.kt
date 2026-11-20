package stapel.push.di

import kotlinx.coroutines.CoroutineScope
import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.di.StapelQualifiers
import stapel.push.PushController
import stapel.push.PushDependencies
import stapel.push.PushDeviceRegistry
import stapel.push.PushOpenListener
import stapel.push.PushOptions
import stapel.push.PushProvider
import stapel.push.PushRationale
import stapel.push.PushStore
import stapel.push.platformPushStore
import stapel.runtime.AppLifecycle
import stapel.runtime.IdGenerator
import stapel.runtime.PlatformContext
import stapel.runtime.StapelDispatchers
import stapel.runtime.StapelLogger
import stapel.session.InstallationIdentity
import stapel.session.SessionManager
import stapel.session.platformInstallationIdentity

/**
 * Binds [PushController] (created at start, so registration follows the session from the first
 * frame) and the platform [PushStore].
 *
 * Reads from the graph: the [PushProvider]s (a provider artifact's module binds one; none = push
 * reports unsupported), [PushDeviceRegistry] (the notifications brick's adapter), [PushRationale],
 * every [PushOpenListener], [SessionManager], [InstallationIdentity] (the auth brick's, so the
 * registry sees the same `device_id` as sign-in; a push-only one when absent), and the runtime.
 * Two transactional providers are a wiring error, reported when the graph starts.
 */
public fun pushModule(options: PushOptions = PushOptions()): Module = module {
    single<PushStore> { platformPushStore(get<PlatformContext>(), get<StapelDispatchers>()) }
    single<PushController>(createdAtStart = true) {
        val providers = getAll<PushProvider>()
        val transactional = providers.filter { it.transactional }
        check(transactional.size <= 1) {
            "pushModule: more than one transactional push provider (${transactional.joinToString { it.id }}); install one"
        }
        PushController(
            PushDependencies(
                provider = transactional.singleOrNull() ?: providers.firstOrNull(),
                registry = getOrNull<PushDeviceRegistry>(),
                sessions = get<SessionManager>(),
                identity = getOrNull<InstallationIdentity>() ?: platformInstallationIdentity(
                    get<PlatformContext>(), get<StapelDispatchers>(), get<IdGenerator>(), namespace = "stapel.push",
                ),
                store = get<PushStore>(),
                scope = get<CoroutineScope>(StapelQualifiers.AppScope),
                lifecycle = getOrNull<AppLifecycle>(),
                rationale = getOrNull<PushRationale>(),
                openListeners = getAll<PushOpenListener>(),
                logger = get<StapelLogger>(),
            ),
            options,
        )
    }
}
