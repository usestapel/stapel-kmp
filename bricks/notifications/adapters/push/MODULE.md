# stapel-notifications-push — module map

## Provides
- `NotificationsPushRegistry` (`PushDeviceRegistry`): `POST devices/ {token, platform, device_id}`,
  `POST devices/unregister/ {device_id}` (404 = already gone), `GET devices/` matched by fingerprint.
  409 → `HeldByAnotherAccount`, 403 → `NotAllowed`, anything else → `Failed`.
- `MarkReadOnOpen` (`PushOpenListener`): `POST feed/read/ {ids: [data.notification_id]}`.
- `notificationsPushRouting(fallbackUri, channelName)`: link keys `url`, then the legacy keys; channels
  `auth`/`billing`/`messages`/`system` (`android.channel_id` = `data.group`).
- Generated at build time from `contracts/notifications/push.json` (`generatePushPayload`, nothing
  committed): `NotificationPushMessage` (the whole message, kotlinx.serialization), `NotificationPushData`
  (typed `data`, `parse` refuses another `v` or a missing required key), `NotificationPushGroup`,
  `NotificationPushContract` (id, version, sha256 of the pinned file).
- `di.notificationsPushModule()`.

## Invariants (tests: `NotificationsPushTest`, real network pipeline on a fake wire)
- The registry's bodies carry exactly the contract's keys; the token is never in a URL; the session is
  the access token's (`sid`), never sent in the body.
- Refusals map as above (a 409 mapped to success turns the test red); an absent row on unregister is done.
- A captured message of the mock sender decodes strictly with the generated types.

## What not to do
- Do not hand-write payload keys elsewhere: read `NotificationPushData`.
