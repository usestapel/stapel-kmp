package stapel.image.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.Uri
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.network.HttpException
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.serviceLoaderEnabled
import coil3.size.Size
import io.ktor.client.HttpClient
import stapel.image.ImageError
import stapel.image.ImageRequest
import stapel.ui.LocalStapelImages
import stapel.ui.StapelImageState
import stapel.ui.StapelImages

/**
 * The app's image loader, built once (a library never sets Coil's singleton): network fetches go
 * through [httpClient] (the app's Ktor client — no second HTTP stack); with [auth] the requests for
 * its hosts carry the session's bearer token through a copy of the client that does not follow
 * redirects, and no other request ever carries one. Coil's own memory and disk caches keep images
 * available offline (independent of `stapel-cache`; clear them on logout with
 * [ImageLoader.clearStapelCaches]). Service-loaded Coil components are off, so no default client
 * is created behind the app's back.
 *
 * @param configure further Coil settings (cache sizes, a logger, a decoder).
 */
@OptIn(coil3.annotation.ExperimentalCoilApi::class)
public fun stapelImageLoader(
    context: PlatformContext,
    httpClient: HttpClient,
    auth: ImageAuth? = null,
    configure: ImageLoader.Builder.() -> Unit = {},
): ImageLoader {
    val bare = KtorNetworkFetcherFactory(httpClient)
    return ImageLoader.Builder(context)
        .serviceLoaderEnabled(false)
        .crossfade(true)
        .components {
            if (auth != null) {
                val authenticated = KtorNetworkFetcherFactory(httpClient.config { followRedirects = false })
                add(BearerInterceptor(auth))
                add(HostRoutedFetcherFactory(auth, authenticated, bare), Uri::class)
            } else {
                add(bare, Uri::class)
            }
        }
        .apply(configure)
        .build()
}

/** Clears the loader's memory and disk caches (after a logout or an account wipe). */
public fun ImageLoader.clearStapelCaches() {
    memoryCache?.clear()
    diskCache?.clear()
}

/** [StapelImages] over a Coil [loader]: what `LocalStapelImages` holds in an app that wired images. */
public class CoilStapelImages(private val loader: ImageLoader) : StapelImages {
    /** The loaded painter for [source], or null while it loads or after it failed. */
    @Composable
    override fun painter(source: String): Painter? = (load(ImageRequest(source)) as? StapelImageState.Ready)?.painter

    /** Coil's painter state for [request] (sized to the request's pixels) as a [StapelImageState]. */
    @Composable
    override fun load(request: ImageRequest): StapelImageState {
        val platform = coil3.compose.LocalPlatformContext.current
        val model = remember(request.url, request.widthPx, request.heightPx, platform) {
            coil3.request.ImageRequest.Builder(platform)
                .data(request.url)
                .size(if (request.widthPx != null && request.heightPx != null) Size(request.widthPx!!, request.heightPx!!) else Size.ORIGINAL)
                .build()
        }
        val painter = rememberAsyncImagePainter(model, loader)
        val state by painter.state.collectAsState()
        return when (val s = state) {
            is AsyncImagePainter.State.Success -> StapelImageState.Ready(painter)
            is AsyncImagePainter.State.Error -> StapelImageState.Failed(s.result.throwable.toImageError())
            else -> StapelImageState.Loading
        }
    }
}

/** Provides [CoilStapelImages] over [loader] to [content] (the app's root, once). */
@Composable
public fun ProvideStapelImages(loader: ImageLoader, content: @Composable () -> Unit) {
    val images = remember(loader) { CoilStapelImages(loader) }
    CompositionLocalProvider(LocalStapelImages provides images, content = content)
}

internal fun Throwable.toImageError(): ImageError = when {
    this is HttpException -> ImageError.Http(response.code)
    message.orEmpty().contains("decode", ignoreCase = true) -> ImageError.Decode
    else -> ImageError.Network(message ?: this::class.simpleName ?: "image fetch failed")
}
