package stapel.network

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.engine.darwin.DarwinHttpRequestException
import io.ktor.client.engine.darwin.certificates.CertificatePinner
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.cookies.HttpCookies
import kotlinx.cinterop.UnsafeNumber
import platform.Foundation.NSHTTPCookieAcceptPolicy
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURLErrorCancelled
import platform.Foundation.NSURLErrorCannotConnectToHost
import platform.Foundation.NSURLErrorCannotFindHost
import platform.Foundation.NSURLErrorClientCertificateRejected
import platform.Foundation.NSURLErrorDNSLookupFailed
import platform.Foundation.NSURLErrorNotConnectedToInternet
import platform.Foundation.NSURLErrorSecureConnectionFailed
import platform.Foundation.NSURLErrorServerCertificateHasBadDate
import platform.Foundation.NSURLErrorServerCertificateHasUnknownRoot
import platform.Foundation.NSURLErrorServerCertificateNotYetValid
import platform.Foundation.NSURLErrorServerCertificateUntrusted
import platform.Foundation.NSURLErrorTimedOut
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.setHTTPShouldHandleCookies
import stapel.network.internal.KtorTransport
import stapel.result.TimeoutPhase
import stapel.result.TransportFailure

public actual fun platformTransport(pins: CertificatePins): HttpTransport = darwinTransport(pins)

/** [ktorCookies] installs Ktor's cookie plug-in: only for the wire tests' inversions. */
internal fun darwinTransport(pins: CertificatePins, hardened: Boolean = true, ktorCookies: Boolean = false): HttpTransport =
    KtorTransport(darwinEngine(pins, hardened), { classifyDarwinFailure(it, !pins.isEmpty) }) {
        if (ktorCookies) install(HttpCookies)
    }

/**
 * [hardened] = false gives the session the shared cookie store and default cookie handling — what a
 * plain `NSURLSession` does; it exists only so the wire tests can show their harness detects cookies.
 */
@OptIn(UnsafeNumber::class)
internal fun darwinEngine(pins: CertificatePins, hardened: Boolean = true): HttpClientEngine = Darwin.create {
    if (!hardened) {
        configureSession {
            setHTTPCookieStorage(NSHTTPCookieStorage.sharedHTTPCookieStorage)
            setHTTPShouldSetCookies(true)
            setHTTPCookieAcceptPolicy(NSHTTPCookieAcceptPolicy.NSHTTPCookieAcceptPolicyAlways)
        }
        return@create
    }
    configureSession {
        setHTTPCookieStorage(null)
        setHTTPShouldSetCookies(false)
        setHTTPCookieAcceptPolicy(NSHTTPCookieAcceptPolicy.NSHTTPCookieAcceptPolicyNever)
        setURLCache(null)
        setRequestCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
    }
    configureRequest {
        setHTTPShouldHandleCookies(false)
    }
    if (!pins.isEmpty) {
        val pinner = CertificatePinner.Builder()
        for ((host, set) in pins.byHost) pinner.add(host, *set.toTypedArray())
        handleChallenge(pinner.build())
    }
}

@OptIn(UnsafeNumber::class)
internal fun classifyDarwinFailure(e: Throwable, pinned: Boolean): WireOutcome.Failure = when (e) {
    is HttpRequestTimeoutException -> WireOutcome.Failure(TransportFailure.Timeout(TimeoutPhase.Request, "request timeout"), true)
    is ConnectTimeoutException -> WireOutcome.Failure(TransportFailure.Timeout(TimeoutPhase.Connect, "connect timeout"), false)
    is SocketTimeoutException -> WireOutcome.Failure(TransportFailure.Timeout(TimeoutPhase.Socket, "socket timeout"), true)
    is DarwinHttpRequestException -> when (e.origin.code) {
        NSURLErrorTimedOut -> WireOutcome.Failure(TransportFailure.Timeout(TimeoutPhase.Socket, "timed out"), true)
        NSURLErrorCannotFindHost, NSURLErrorCannotConnectToHost, NSURLErrorDNSLookupFailed,
        NSURLErrorNotConnectedToInternet,
        -> WireOutcome.Failure(TransportFailure.ConnectionFailed("NSURLError ${e.origin.code}"), false)
        NSURLErrorSecureConnectionFailed, NSURLErrorServerCertificateHasBadDate,
        NSURLErrorServerCertificateUntrusted, NSURLErrorServerCertificateHasUnknownRoot,
        NSURLErrorServerCertificateNotYetValid, NSURLErrorClientCertificateRejected,
        -> WireOutcome.Failure(TransportFailure.TlsRejected(pinMismatch = false, "NSURLError ${e.origin.code}"), false)
        // Ktor's Darwin CertificatePinner cancels the authentication challenge on a mismatch.
        NSURLErrorCancelled ->
            if (pinned) {
                WireOutcome.Failure(TransportFailure.TlsRejected(pinMismatch = true, "challenge cancelled (pin mismatch)"), false)
            } else {
                WireOutcome.Failure(TransportFailure.ConnectionFailed("NSURLError ${e.origin.code}"), true)
            }
        else -> WireOutcome.Failure(TransportFailure.ConnectionFailed("NSURLError ${e.origin.code}"), true)
    }
    else -> WireOutcome.Failure(TransportFailure.ConnectionFailed(e::class.simpleName), true)
}
