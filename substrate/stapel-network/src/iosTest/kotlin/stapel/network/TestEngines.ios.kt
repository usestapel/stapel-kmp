package stapel.network

internal const val engineName = "Darwin"

internal fun platformTestTransport(ktorCookies: Boolean = false): HttpTransport =
    darwinTransport(CertificatePins.None, ktorCookies = ktorCookies)

/** NSURLSession with the shared cookie store and default cookie handling. */
internal fun cookieKeepingTestTransport(): HttpTransport = darwinTransport(CertificatePins.None, hardened = false)
