# stapel-push

Push notifications for Stapel clients behind one provider-neutral facade: the OS permission flow
(never at launch, a rationale first, iOS provisional and denied handled), a token lifecycle bound to
the session, and taps routed into the app's deep links. Vendor SDKs live only in provider artifacts
(`stapel-push-firebase`); the backend seam is `PushDeviceRegistry` (`stapel-notifications-push`).
A project that wants no push depends on none of these.

Coordinates: `io.github.usestapel:stapel-push` (provisional group).

```kotlin
val app = stapelApp(platform) {
    environment(AppEnvironment)
    bricks(AuthBrick())
    modules(pushModule(PushOptions(ask = AskPolicy.OnDemand)), firebasePushModule(platform), notificationsPushModule())
}
val push = app.koin.get<PushController>()
push.attach(app::openUrl)                 // taps open deep links; the launch notification is kept until now
scope.launch { push.enable() }            // from a toggle: rationale → OS prompt → token → registered
push.state.collect { render(it) }         // availability, permission, registration, enabled
```
