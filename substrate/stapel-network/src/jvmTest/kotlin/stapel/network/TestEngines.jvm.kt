package stapel.network

internal const val engineName = "OkHttp"

internal fun platformTestTransport(ktorCookies: Boolean = false): HttpTransport =
    okHttpTransport(CertificatePins.None, ktorCookies = ktorCookies)

/** OkHttp itself keeps no cookies even unhardened, so the cookie-keeping client is Ktor's plug-in. */
internal fun cookieKeepingTestTransport(): HttpTransport = okHttpTransport(CertificatePins.None, hardened = false, ktorCookies = true)
