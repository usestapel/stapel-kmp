# stapel-auth-testing — module notes

## Seams

- `signInWithMockOtp(identifier, code = PROTOTYPE_MOCK_CODE, requireMockHint = true, timeoutMillis)`:
  identifier → send code → mock-delivery hint (prototype posture) → code → sign-in screen gone.
  Each step waits with its own budget and fails with `AuthWalkException` naming the step.
- `AuthTags` = `stapel.auth.ui.AuthTestTags`; `awaitTag(tag)`.

## Invariants

- The walker never fakes a backend answer: it is meant for a real stapel-auth in prototype posture
  (mock OTP). A live-posture backend fails the walk at the hint instead of waiting for a real code.
- The code is the backend's configured mock code; `PROTOTYPE_MOCK_CODE` is stapel-auth's default.

## What not to do

- Do not depend on this artifact from main source sets; it carries the Compose UI test API.
