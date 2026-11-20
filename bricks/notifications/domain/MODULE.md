# stapel-notifications — module map

## Provides
- Generated (`src/commonMain/generated`, `stapelGenerateCheck`): `NotificationsApi` / `NotificationsApiClient`,
  models, per-operation errors (`RegisterDeviceTokenError`, …), cache keys, the feed pager.
- `di.notificationsModule()`: `NotificationsApi` on the authenticated transport.

## Invariants
- The generated tree equals what the pinned contract generates (drift gate); the contract is
  vendored by `pins sync` with its sha256 in `contracts/notifications/pin.json`.
- Round-trip fixtures of every named example of the contract (`src/commonTest/generated`).

## Seams
- `stapel-notifications-push` (brick adapter): the device registry for stapel-push, the push
  payload types generated from `contracts/notifications/push.json`, mark-read on a tap.

## Not here yet
- Feed and preferences presenters, the notifications brick UI (later slice).
