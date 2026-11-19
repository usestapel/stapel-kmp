# stapel-auth-e2e

The auth domain library against a REAL backend: no mocked server. Never published.

```sh
bricks/auth/e2e/stand/stand.sh up        # stapel-tools 0.82.0 from PyPI, --type minimal, prototype posture;
                                         # stapel-auth 0.53.0 / stapel-core 0.98.0 (the pinned contract)
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
| app | `stapelApp { bricks(AuthBrick()) }` alone: sign-in owner is `auth.login`, the mock code signs in, navigation sees a member |
| current | each device sees exactly its own row `isCurrent`, and keeps it after a refresh rotation |
| others | revoke-others (presenter) spares this device: own refresh works, the other device's is dead, one row left |
| logout-bare | logout with the access token only (no refresh token in the body) kills the session's refresh token |

## What the real server did that the contract does not say (lines for the backend train)

Fixed and proven by this suite since `stapel-auth` 0.53.0 / `stapel-core` 0.98.0 / `stapel-tools`
0.82.0: the minimal scaffold signs people in with no stand-side celery install and no
`app.set_default()` call; `is_current` marks the bearer caller; `DELETE /sessions/` spares the caller;
a body-less logout revokes the session; `retry_after` / `retry_after_display` are documented params of
`error.429.rate_limit`, `error.422.blocked` and `error.423.account_locked`; the contract's own examples
decode (one left, owned by stapel-gdpr, pinned in the domain's `GeneratedContractTest`).

Open:

1. **The scaffold's registry caps `stapel-auth` below the contract the suite proves**: `stapel-tools`
   0.82.0 writes `stapel-auth[phone]>=0.52.0,<0.53` into `requirements.txt`. The stand rewrites the
   `stapel-auth` and `stapel-core` lines to the exact versions above and nothing else.
2. **The per-identifier resend cooldown also gates `/password/change/otp/request/`** right after an
   e-mail sign-in (one shared limiter), answered `429`.
3. **Capabilities under `--auth-model public`**: `registration.sso` is `true` while `login.sso` is
   `false`; `registration.anonymous` is `true`.
4. **Phone validation** rejects numbers in reserved fiction ranges libphonenumber marks invalid
   (UK `+44 7700 900xxx`), answering `error.400.invalid_phone`; NANP `555-01xx` is accepted.
5. **The proactive refresh window must be shorter than the access-token lifetime**: with the session
   default (30 s) and a 5 s token every request refreshes first. Not a server defect; a substrate
   default to clamp (`stapel-session`).
