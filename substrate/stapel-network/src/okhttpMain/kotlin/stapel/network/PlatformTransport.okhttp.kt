package stapel.network

import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.cookies.HttpCookies
import okhttp3.CertificatePinner
import okhttp3.CookieJar
import stapel.network.internal.KtorTransport
import stapel.result.TimeoutPhase
import stapel.result.TransportFailure
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

public actual fun platformTransport(pins: CertificatePins): HttpTransport = okHttpTransport(pins)

/**
 * [hardened] = false keeps Ktor's engine defaults (OkHttp's `retryOnConnectionFailure(true)`), and
 * [ktorCookies] installs Ktor's cookie plug-in; both exist only so the wire tests can show their
 * harness detects what the hardened transport forbids.
 */
internal fun okHttpTransport(pins: CertificatePins, hardened: Boolean = true, ktorCookies: Boolean = false): HttpTransport {
    val engine = OkHttp.create {
        if (!hardened) return@create
        config {
            retryOnConnectionFailure(false)
            followRedirects(false)
            followSslRedirects(false)
            cookieJar(CookieJar.NO_COOKIES)
            cache(null)
            if (!pins.isEmpty) {
                val pinner = CertificatePinner.Builder()
                for ((host, set) in pins.byHost) pinner.add(host, *set.toTypedArray())
                certificatePinner(pinner.build())
            }
        }
    }
    return KtorTransport(engine, ::classifyOkHttpFailure) { if (ktorCookies) install(HttpCookies) }
}

internal fun classifyOkHttpFailure(e: Throwable): WireOutcome.Failure = when (e) {
    is HttpRequestTimeoutException -> WireOutcome.Failure(TransportFailure.Timeout(TimeoutPhase.Request, "request timeout"), true)
    is ConnectTimeoutException -> WireOutcome.Failure(TransportFailure.Timeout(TimeoutPhase.Connect, "connect timeout"), false)
    // Ktor's SocketTimeoutException is java.net.SocketTimeoutException on the JVM.
    is SocketTimeoutException ->
        if (e.message.orEmpty().contains("connect", ignoreCase = true)) {
            WireOutcome.Failure(TransportFailure.Timeout(TimeoutPhase.Connect, "connect timeout"), false)
        } else {
            WireOutcome.Failure(TransportFailure.Timeout(TimeoutPhase.Socket, "socket timeout"), true)
        }
    is SSLPeerUnverifiedException ->
        if (e.message.orEmpty().contains("pinning", ignoreCase = true)) {
            WireOutcome.Failure(TransportFailure.TlsRejected(pinMismatch = true, "certificate pin mismatch"), false)
        } else {
            WireOutcome.Failure(TransportFailure.TlsRejected(pinMismatch = false, e::class.simpleName), false)
        }
    is SSLHandshakeException -> WireOutcome.Failure(TransportFailure.TlsRejected(pinMismatch = false, e::class.simpleName), false)
    is UnknownHostException, is ConnectException, is NoRouteToHostException ->
        WireOutcome.Failure(TransportFailure.ConnectionFailed(e::class.simpleName), false)
    else -> WireOutcome.Failure(TransportFailure.ConnectionFailed(e::class.simpleName), true)
}
