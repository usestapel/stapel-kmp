# stapel-notifications-push

The notifications brick's adapter for `stapel-push`: the device registry over the generated
notifications client, mark-read on a tap, the push routing of the brick (canonical `url` first, one
Android channel per routing group), and the push payload types generated at build time from the
pinned `contracts/notifications/push.json` (`NotificationPushMessage`, `NotificationPushData`).

Coordinates: `io.github.usestapel:stapel-notifications-push` (provisional group).

```kotlin
modules(
    pushModule(PushOptions(routing = notificationsPushRouting(fallbackUri = "myapp://notifications"))),
    notificationsPushModule(),                  // + notificationsModule(): NotificationsApi
    firebasePushModule(/* platform */),
)
```
