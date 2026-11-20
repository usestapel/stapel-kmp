# stapel-notifications-e2e

Push registration against a real backend: `stand/stand.sh up` scaffolds a project with stapel-tools
0.86.0 (`--type minimal --modules auth notifications --posture prototype`), pins stapel-notifications
0.31.1, stapel-auth 0.54.1 and stapel-core 0.99.5, sets the mock push sender and serves on
127.0.0.1:18771 natively (no Docker). Three stand-only probes (`stand/e2e_urls.py`): the server's
device rows of an account, a real notification through `process_notification` answering what the
mock sender captured, liveness. The suite wires the app the way a scaffolded `--mobile-push` app is
wired, with `FakePushProvider` in place of the vendor SDK.

```sh
bricks/notifications/e2e/stand/stand.sh up
./gradlew :stapel-notifications-e2e:test -Pstapel.e2e.baseUrl=http://127.0.0.1:18771/
bricks/notifications/e2e/stand/stand.sh down
```

| Scenario | Proves |
|---|---|
| E-1 | nothing registered before `enable()`; then one row with the installation id, bound to the session, active; sign-out → the client's own `POST devices/unregister/` answers 204 before `POST logout/` leaves, and when it leaves the server holds no row for the account (read by a stand probe from the network plug-in's `decorate`), so the row went by the client's call, not by the backend's session-revoked cleanup; the provider token deleted; another account on the same installation registers a fresh token, nothing left for the first, no conflict (red when the token is never deleted, red when auth revokes before the farewell) |
| E-2 | the mock sender's captured message decodes strictly with the types generated from `push.json`; a tap opens `data.url` through the controller and marks the feed row read |
| E-3 | a guest session holds no device row |
| E-4 | a farewell hook that throws and one that never returns do not block the sign-out (bounded by `SessionConfig.farewellTimeout`); push's own unregister still answers 204 before the revoke |
