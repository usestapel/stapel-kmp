# stapel-auth-e2e

The auth domain library against a REAL backend: no mocked server. Never published.

```sh
bricks/auth/e2e/stand/stand.sh up        # stapel-tools 0.81.2 from PyPI, --type minimal, prototype posture
./gradlew :stapel-auth-e2e:test \
    -Pstapel.e2e.baseUrl=http://127.0.0.1:18731/ \
    -Pstapel.e2e.captchaBaseUrl=http://127.0.0.1:18732/
bricks/auth/e2e/stand/stand.sh down      # stops only what the script started (pid files)
```

The stand is scaffolded into `build/stand/` with its own Python 3.12 venvs, runs natively (SQLite,
`runserver`), and adds four files from `stand/`: a settings overlay (5-second access token, 2-second
OTP resend cooldown), a captcha settings overlay (a verifier accepting one fixed token), a probe
middleware counting requests that carry a `Cookie` header (`GET /e2e/probe/`), and the URL conf
exposing the probe. Without `-Pstapel.e2e.baseUrl` the test task is skipped.

## Scenarios (typed assertions only)

| Id | Scenario |
|---|---|
| P1–P3 | the stand is the subject: prototype posture, mocked channels, code length 4, access `exp − iat == 5`, installed `stapel-auth` == pinned contract version |
| chooser | the method chooser renders exactly e-mail, phone, password in server order |
| D-1 | e-mail OTP registration: mock flag, masked target, `Registered`, tokens stored, `/me/` same id |
| D-2 | wrong code → `InvalidCode` with `attemptsRemaining` going down, no session; right code succeeds |
| D-3 | phone OTP: second device signs in to the same account (`LoggedIn`, same id) |
| D-4 | resend during the cooldown sends nothing |
| D-5 | password set through `/password/change/otp/*`, then password sign-in; wrong password → `InvalidCredentials`, no tokens |
| D-6 | logout → `RevokedOnServer`, `SignedOut(UserRequested)`, vault empty, profile gone from the cache partition, old refresh → `error.401.refresh_revoked` |
| D-7 | after the real 5 s expiry: one refresh, no 401 on the wire, both tokens rotated |
| D-8 | ten parallel calls after expiry share one refresh |
| D-9 | proactive refresh off: one 401, one refresh, one replay |
| D-10 | a session revoked from a second device → the first ends `SignedOut(Revoked)`, change emitted, vault empty, the other device unaffected |
| D-11 | refresh that reaches no server (a loopback proxy switched off) → `Network` error, still signed in, recovers |
| D-12 | guest reuse without a call, promotion in place (same id, `Promoted`), merge into an existing account (`Merged`, ids differ) |
| guest-dead-token | a public call with an expired guest token returns the same guest; inversion: with proactive refresh off the server mints a second guest |
| everywhere | sign out everywhere: other devices end `Revoked`, this one `UserRequested` |
| cookies | after each class: the server saw requests and none carried a `Cookie` header |
| captcha | captcha instance: no solver → `CaptchaRequired`, one request; fake solver → solved once, `400` then `200`; wrong token → `CaptchaInvalid` |
| koin | `authModule()` + substrate modules: a presenter from Koin signs in, refresh works through the bare transport |
| findings | revoke-others revokes the caller (pinned, see below) |

## What the real server did that the contract does not say (lines for the backend train)

1. **The scaffolded minimal project cannot sign anybody in.** `requirements.txt` has no `celery`, yet
   `stapel_auth.tasks` imports `celery.shared_task` on every session issuance (login notification):
   `ModuleNotFoundError` → 500 on `/email/verify/`, `/phone/verify/`. The stand installs celery.
2. **With celery installed, a threaded server still answers 500**: `config/celery.py` makes the project
   app current only in the importing thread; a request served on another thread (threaded
   `runserver`, gthread workers) binds `@shared_task` to Celery's unconfigured default app
   (`amqp://localhost`, connection refused). The stand calls `app.set_default()`.
3. **`GET /sessions/` never marks the caller `is_current` for a bearer client**: it compares the stored
   refresh `jti` with the access token's `refresh_jti` claim, which access tokens do not carry.
4. **`DELETE /sessions/` ("all except current") revokes the caller too** for a bearer client (same
   missing claim). "Sign out other devices" signs this device out as well. Pinned by a test that
   fails once the backend is fixed.
5. **429 params**: `error.429.rate_limit` carries `retry_after` (seconds) and `retry_after_display`, not
   in `errors.json` (which lists only `retry_after_minutes`); `retry_after_minutes` is rounded up (30 s → 1).
   The code is not bound to `/email/request/` in the contract, so it arrives through the transport
   family (`RateLimited`), not as a domain error of the operation.
6. **The per-identifier resend cooldown also gates `/password/change/otp/request/`** right after an
   e-mail sign-in (one shared limiter), answered `429`.
7. **Capabilities under `--auth-model public`**: `registration.sso` is `true` while `login.sso` is
   `false`; `registration.anonymous` is `true`.
8. **Phone validation** rejects numbers in reserved fiction ranges libphonenumber marks invalid
   (UK `+44 7700 900xxx`), answering `error.400.invalid_phone`; NANP `555-01xx` is accepted.
9. **The proactive refresh window must be shorter than the access-token lifetime**: with the session
   default (30 s) and a 5 s token every request refreshes first. Not a server defect; a substrate
   default to clamp (`stapel-session`).
