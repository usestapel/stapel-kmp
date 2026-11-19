package stapel.network

/**
 * The platform [HttpTransport]: Ktor OkHttp on Android and desktop, Ktor Darwin (`NSURLSession`) on
 * iOS. Cookie storage is off and `Set-Cookie` is dropped (the backend prefers a cookie over the bearer
 * header, so a stored cookie would outrank a fresh token); engine-level retries and redirects are off
 * (every retry decision belongs to the pipeline).
 */
public expect fun platformTransport(pins: CertificatePins = CertificatePins.None): HttpTransport
