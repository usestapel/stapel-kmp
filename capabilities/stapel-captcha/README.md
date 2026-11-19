# stapel-captcha

Headless captcha for Stapel clients: a `CaptchaSolver` seam and a network plug-in
that answers `error.400.captcha_required` / `captcha_invalid` by asking the solver
once and retrying once with the token (header and/or body field, as the operation
declares). No provider SDK, no UI.

Coordinates: `io.github.usestapel:stapel-captcha` (provisional group).

```kotlin
val api = StapelTransport(deps, authenticator = session.authenticator,
    plugins = listOf(CaptchaPlugin(solver = { request -> showTurnstile(request.action) })))
// With Koin: captchaModule(solver) next to networkModule(environment).
```
