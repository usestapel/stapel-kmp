# stapel-captcha — module map

## Provides
`CaptchaSolver` (the app's provider: web view widget, test double), `CaptchaRequest`,
`CaptchaReason`, `CaptchaCodes`, `CaptchaPlugin(solver, proactive)` (a `NetworkPlugin`, id
`stapel.captcha`, order 300); `di.captchaModule(solver, proactive)` contributes it.

## Invariants (tested through the real pipeline with a fake solver)
- One solve and one retry per logical call; a rejected token comes back as the error.
- The token goes where the operation reads it (`OperationSpec.captcha`: `X-Captcha-Token`
  header for `@captcha_protected`, `captcha_token` body field for `CaptchaMixin`); an operation
  without the declaration gets both.
- An abandoned challenge (solver returns null) returns the original refusal.
- Prototype posture: the backend verifier is a no-op, the plug-in never fires.

## What not to do
- Do not ship a provider or a site key here: the backend publishes neither; the app owns both.
