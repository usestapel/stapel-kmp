# stapel-auth

Auth domain library: the generated client of the `stapel-auth` backend (`AuthApi`, models, typed
errors, pagers, cache keys) plus, as it lands, the hand-written repositories and sign-in flows.

Coordinates: `io.github.usestapel:stapel-auth` (provisional group).

```kotlin
val api: AuthApi = AuthApiClient(transport)            // transport: ApiTransport (stapel-network)
when (val r = api.emailVerify(EmailAuthVerify(email = "a@example.com", code = "123456"))) {
    is StapelResult.Ok -> when (val login = r.value) {
        is AuthResponse -> signedIn(login.tokens)
        is TOTPChallengeResponse -> askTotp(login.challengeToken)
        else -> retry()
    }
    is StapelResult.Err -> show(r.error.code)
}
```
