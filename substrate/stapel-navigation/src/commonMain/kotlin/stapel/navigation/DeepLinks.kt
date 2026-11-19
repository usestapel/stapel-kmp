package stapel.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Which URIs belong to the app. The URL space is the web's: `https://<host><basePath><absolutePath>`
 * and `<scheme>://<absolutePath without the leading slash>`.
 *
 * @property schemes custom schemes (the project slug).
 * @property hosts universal-link / app-link hosts.
 * @property basePath prefix the web build is served under (`"/kmp"`), stripped from https links.
 */
public class DeepLinkConfig(
    public val schemes: Set<String>,
    public val hosts: Set<String>,
    public val basePath: String = "",
)

/**
 * A resolved deep link.
 *
 * @property target the destination the link names.
 * @property stack the synthetic stack to open: ancestors, then the target.
 */
public data class DeepLinkMatch(public val target: DestinationId, public val stack: List<Route>)

/**
 * Turns URIs into stacks and routes into URIs, using the same absolute paths as the web routes.
 * When two paths match, the one with more literal segments wins, then (order, id).
 */
public class DeepLinkResolver(private val nav: ResolvedNav, private val config: DeepLinkConfig) {
    private val candidates: List<Pair<ResolvedDestination, PathPattern>> = nav.byId.values
        .map { it to PathPattern(it.absolutePath) }
        .sortedWith(compareBy({ -it.second.literalCount }, { it.first.order }, { it.first.id.value }))

    /** The stack for [uri], or null when it is not one of the app's links or matches no destination. */
    public fun resolve(uri: String): DeepLinkMatch? {
        val parsed = parse(uri) ?: return null
        for ((dest, pattern) in candidates) {
            val params = pattern.match(parsed.segments) ?: continue
            val route = routeFor(dest, params, parsed.query) ?: continue
            return DeepLinkMatch(dest.id, nav.syntheticStack(route))
        }
        return null
    }

    /**
     * The link of [route]: `<scheme>://path` when [scheme] is given, otherwise the https link on the
     * first configured host; null when the route is not installed or no host is configured.
     */
    public fun uriFor(route: Route, scheme: String? = null): String? {
        val dest = nav.destinationOf(route) ?: return null
        val address = nav.addressOf(route) ?: return null
        val path = PathPattern(dest.absolutePath).format(address.path)
        val query = address.query.entries.sortedBy { it.key }
            .flatMap { (k, vs) -> vs.map { "${percentEncode(k)}=${percentEncode(it)}" } }
            .joinToString("&")
        val suffix = if (query.isEmpty()) "" else "?$query"
        if (scheme != null) return "$scheme://${path.removePrefix("/")}$suffix"
        val host = config.hosts.minOrNull() ?: return null
        return "https://$host${config.basePath.trimEnd('/')}$path$suffix"
    }

    private fun routeFor(dest: ResolvedDestination, params: Map<String, String>, query: Map<String, List<String>>): Route? {
        val spec = dest.spec ?: return SectionRoute(dest.id)
        return spec.codec.parse(params, query)
    }

    private class Parsed(val segments: List<String>, val query: Map<String, List<String>>)

    private fun parse(uri: String): Parsed? {
        val schemeEnd = uri.indexOf("://")
        if (schemeEnd <= 0) return null
        val scheme = uri.substring(0, schemeEnd).lowercase()
        var rest = uri.substring(schemeEnd + 3).substringBefore('#')
        val query = rest.substringAfter('?', "")
        rest = rest.substringBefore('?')
        val path: String = when (scheme) {
            "https", "http" -> {
                val authority = rest.substringBefore('/')
                val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
                if (host !in config.hosts.map { it.lowercase() }) return null
                val raw = "/" + rest.substringAfter('/', "")
                val base = config.basePath.trimEnd('/')
                when {
                    base.isEmpty() -> raw
                    raw == base -> "/"
                    raw.startsWith("$base/") -> raw.substring(base.length)
                    else -> return null
                }
            }
            in config.schemes.map { it.lowercase() } -> "/$rest"
            else -> return null
        }
        val segments = path.split('/').filter { it.isNotEmpty() }.map { percentDecode(it) }
        val q = LinkedHashMap<String, MutableList<String>>()
        if (query.isNotEmpty()) {
            for (pair in query.split('&')) {
                if (pair.isEmpty()) continue
                val k = percentDecode(pair.substringBefore('='), plusIsSpace = true)
                val v = percentDecode(pair.substringAfter('=', ""), plusIsSpace = true)
                q.getOrPut(k) { mutableListOf() } += v
            }
        }
        return Parsed(segments, q)
    }
}

/**
 * Where platform entry points hand raw URIs (Android `onNewIntent`, iOS `onOpenURL`, desktop
 * `setOpenURIHandler`). Buffered until the host collects; one collector receives each URI once.
 */
public object ExternalUris {
    private val channel = Channel<String>(Channel.UNLIMITED)

    /** Queues [uri] for the host. Safe from any thread. */
    public fun offer(uri: String) {
        channel.trySend(uri)
    }

    /** URIs in arrival order; a single collector (the app's navigation host) consumes them. */
    public val incoming: Flow<String> = channel.receiveAsFlow()
}
