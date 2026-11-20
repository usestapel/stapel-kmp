package stapel.image.compose

import coil3.Uri
import coil3.fetch.Fetcher
import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.Options
import stapel.network.RefreshVerdict
import stapel.network.RequestAuthenticator

/**
 * Which image hosts receive the session's bearer token: exactly [hosts] (the app's API host and its
 * media host, from the environment), compared case-insensitively, over `http`/`https` only. Every
 * other host — a CDN, an external link — gets no `Authorization` header, whatever the request carried.
 *
 * @property authenticator the session (`stapel-session` implements it): the token, and one refresh on a 401.
 */
public class ImageAuth(
    public val authenticator: RequestAuthenticator,
    hosts: Set<String>,
) {
    /** The authenticated host names, lower-cased. */
    public val hosts: Set<String> = hosts.map { it.lowercase() }.toSet()

    init {
        require(this.hosts.none { it.isBlank() || '/' in it || ':' in it }) { "ImageAuth: hosts are bare host names, got $hosts" }
    }

    /** True when a request to [url] may carry the bearer token. */
    public fun authorizes(url: String): Boolean {
        val scheme = url.substringBefore("://", missingDelimiterValue = "").lowercase()
        if (scheme != "http" && scheme != "https") return false
        return hostOf(url) in hosts
    }

    /** Debug form (no token). */
    override fun toString(): String = "ImageAuth(hosts=$hosts)"
}

/** The host of an absolute URL, lower-cased: userinfo, port, path, query and IPv6 brackets stripped. */
internal fun hostOf(url: String): String {
    val authority = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#')
    val hostPort = authority.substringAfterLast('@')
    val host = if (hostPort.startsWith("[")) hostPort.substringBefore(']').removePrefix("[") else hostPort.substringBefore(':')
    return host.lowercase()
}

internal const val AUTHORIZATION: String = "Authorization"

/** Adds the bearer token to requests for [auth]'s hosts, strips any `Authorization` from every other request, refreshes once on a 401. */
internal class BearerInterceptor(private val auth: ImageAuth) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val url = request.data.toString()
        if (!auth.authorizes(url)) {
            val stripped = request.withoutAuthorization() ?: return chain.proceed()
            return chain.withRequest(stripped).proceed()
        }
        val token = auth.authenticator.accessToken() ?: return chain.withRequest(request.withoutAuthorization() ?: request).proceed()
        val result = chain.withRequest(request.withBearer(token)).proceed()
        val status = ((result as? ErrorResult)?.throwable as? HttpException)?.response?.code
        if (status != 401) return result
        return when (val verdict = auth.authenticator.refresh(token)) {
            is RefreshVerdict.Refreshed -> chain.withRequest(request.withBearer(verdict.accessToken)).proceed()
            else -> result
        }
    }

    private fun ImageRequest.withBearer(token: String): ImageRequest =
        newBuilder().httpHeaders(httpHeaders.newBuilder().set(AUTHORIZATION, "Bearer $token").build()).build()

    private fun ImageRequest.withoutAuthorization(): ImageRequest? {
        val headers = httpHeaders.asMap()
        if (headers.keys.none { it.equals(AUTHORIZATION, ignoreCase = true) }) return null
        val builder = NetworkHeaders.Builder()
        headers.filterKeys { !it.equals(AUTHORIZATION, ignoreCase = true) }.forEach { (k, v) -> builder.set(k, v) }
        return newBuilder().httpHeaders(builder.build()).build()
    }
}

/**
 * Picks the HTTP client by host: requests for [auth]'s hosts go through [authenticated] (redirects
 * off, so a redirect never carries the token elsewhere), every other request through [bare].
 */
internal class HostRoutedFetcherFactory(
    private val auth: ImageAuth,
    private val authenticated: Fetcher.Factory<Uri>,
    private val bare: Fetcher.Factory<Uri>,
) : Fetcher.Factory<Uri> {
    override fun create(data: Uri, options: Options, imageLoader: coil3.ImageLoader): Fetcher? =
        (if (auth.authorizes(data.toString())) authenticated else bare).create(data, options, imageLoader)
}
