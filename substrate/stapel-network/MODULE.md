# stapel-network — module map

## Provides
- `StapelTransport(deps, config, authenticator, verification, plugins, transport): ApiTransport` —
  the pipeline. `NetworkDependencies`, `NetworkConfig`, `RetryPolicy`, `TimeoutPolicy`, `CertificatePins`.
- Seams: `RequestAuthenticator` (+ `RefreshVerdict`; implemented by `stapel-session`),
  `VerificationHandler` (+ `VerificationOutcome`; implemented by the auth domain), `NetworkPlugin`
  (+ `RequestContext`, `Recovery`, `CallEvent`; optional capabilities such as `stapel-captcha`).
- The wire: `HttpTransport`, `WireRequest`, `WireBody`, `WirePart`, `WireOutcome`;
  `platformTransport(pins)`.
- `di.networkModule(environment, config)`; `di.NetworkQualifiers.Bare`.

## Invariants (each has a test)
- **No cookie ever leaves the client** (the backend reads a cookie before the bearer header):
  OkHttp has `CookieJar.NO_COOKIES`, Darwin has no cookie storage and `HTTPShouldHandleCookies =
  false`, `Set-Cookie` is dropped from every response. Proven per engine against a real loopback
  server (`src/engineTest`, runs on jvm and in the iOS simulator), with inversions showing the
  harness sees a cookie when a client keeps one.
- **Engine retries and redirects are off.** OkHttp's `retryOnConnectionFailure` is forced off (Ktor's
  engine default turns it on; the stale keep-alive test shows the default re-sends a POST). A 3xx is
  returned, never followed (it would carry the bearer elsewhere).
- **Upload progress reaches the caller.** `OperationCall.onProgress` travels as `WireRequest.onUpload`
  to Ktor's body progress; a multipart upload reports up to the whole body (`EngineWireTest`, per
  engine) and the pipeline hands the listener over (`PipelineTest`).
- **Recover once.** Per logical call each recovery fires at most once: refresh on a 401 that
  carried a token, step-up on a 403 with a `verification.challenge_id`, each plug-in. A second 401
  is `Unauthenticated`. A refresh without verdict fails the call as `ConnectionFailed` (transient),
  never as signed out. Recoveries do not consume transport attempts.
- **One retry layer.** Transport faults: a request that provably never left is retried; a query is
  retried after a timeout/drop, 502/503/504, or a 429 whose wait ≤ `maxHonouredRetryAfter`; a
  mutation is never re-sent once it may have arrived (no idempotency key on the backend yet).
  TLS rejections are never retried. Full-jitter exponential backoff.
- `X-Request-ID` is the same on every attempt of one call; the echoed id ends up on the error.
- `Offline` (from `Connectivity`) short-circuits without sending; every outcome reports
  reachability back.

## What not to do
- Do not install Ktor `Auth`, `HttpRequestRetry`, `HttpCookies` or `ContentNegotiation` anywhere:
  the pipeline is the only place these decisions are made.
- A token refresher must use the bare transport (`NetworkQualifiers.Bare` / an authenticator-less
  `StapelTransport`), never the authenticated one.
- Do not add an `Idempotency-Key`; the backend has none (a contract gap, not a client feature).

## Not covered here (stated, not hidden)
- Certificate pinning is wired into both engines (OkHttp `CertificatePinner`, Ktor Darwin
  `CertificatePinner`) but **no test proves a mismatch on a real TLS socket** (the in-process
  test server has no TLS). Mismatch classification is by exception shape.
- Downloads are buffered in memory (`BinaryBody` over the whole body), not streamed.
- OkHttp on an Android device or emulator is not exercised (no system image on the workstation);
  the JVM run covers the same OkHttp code (one source set compiled for both).
