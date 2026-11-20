# stapel-notifications-e2e

Push registration against a real backend: `stand/stand.sh up` scaffolds a project with stapel-tools
0.86.0 (`--type minimal --modules auth notifications --posture prototype`), pins stapel-notifications
0.31.1, stapel-auth 0.54.0 and stapel-core 0.99.4, sets the mock push sender and serves on
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
| E-1 | nothing registered before `enable()`; then one row with the installation id, bound to the session, active; sign-out → the row is gone and the provider token deleted; another account on the same installation registers a fresh token, nothing left for the first, no conflict (red when the token is never deleted) |
| E-2 | the mock sender's captured message decodes strictly with the types generated from `push.json`; a tap opens `data.url` through the controller and marks the feed row read |
| E-3 | a guest session holds no device row |
| E-4 | with `SessionManager.farewell()` before the server revoke, the client's own unregister reaches the server (today's auth sign-out revokes first: E-1 logs `unregister failed error.401.unauthorized` and the row goes by the backend's session-revoked cleanup) |
