# stapel-notifications

The notifications domain library: the client of the `stapel-notifications` backend brick
(device registry, feed, notification keys), generated from its pinned contract
(`contracts/notifications/`, stapel-notifications 0.31.1, contract profile 2).

Coordinates: `io.github.usestapel:stapel-notifications` (provisional group).

```kotlin
modules(notificationsModule())                         // NotificationsApi on the authenticated transport
val api = koin.get<NotificationsApi>()
api.registerDeviceToken(DeviceTokenRequest(token, "ios", deviceId))
api.markNotificationFeedRead(FeedReadRequest(ids = listOf(notificationId)))
```

Push registration is not wired by hand: `stapel-notifications-push` implements stapel-push's
`PushDeviceRegistry` over this client.
