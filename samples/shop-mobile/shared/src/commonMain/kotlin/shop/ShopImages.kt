package shop

import coil3.ImageLoader
import io.ktor.client.HttpClient
import org.koin.core.module.Module
import org.koin.dsl.module
import stapel.config.StapelEnvironment
import stapel.image.compose.ImageAuth
import stapel.image.compose.clearCachesOnSessionEnd
import stapel.image.compose.stapelImageLoader
import stapel.runtime.PlatformContext
import stapel.session.SessionManager

/** Coil's platform handle for [platform] (the `Application` on Android, a singleton elsewhere). */
internal expect fun coilContext(platform: PlatformContext): coil3.PlatformContext

/**
 * The app's one image loader (stapel-image-compose over Coil 3), bound in Koin: avatars and every other
 * backend picture load through it. The session's bearer goes only to the app's own host (the
 * stapel-cdn endpoint of the environment; stapel-cdn renders host-relative URLs, resolved against it); memory and disk
 * caches are cleared on every session end, so the next account never sees the previous one's pictures.
 * Fetched through a Ktor client on the platform's engine (OkHttp on Android and desktop, Darwin on iOS).
 */
internal fun shopImagesModule(platform: PlatformContext): Module = module {
    single<ImageLoader> {
        val environment = get<StapelEnvironment>()
        val sessions = get<SessionManager>()
        // Pictures are stapel-cdn's: its endpoint is the media host (the default one when cdn has none of its own).
        val media = environment.endpoint("cdn")
        stapelImageLoader(
            context = coilContext(platform),
            httpClient = HttpClient(),
            auth = ImageAuth(sessions.authenticator, setOfNotNull(media.host, environment.defaultEndpoint?.host)),
            baseUrl = media.baseUrl,
        ).also { it.clearCachesOnSessionEnd(sessions) }
    }
}
