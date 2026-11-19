# stapel-navigation-compose

The Compose half of Stapel navigation: UI libraries declare a `NavContribution` (manifest data +
the composables bound to it), the app creates a navigator with `rememberStapelNavigator` and draws
it with `StapelNavHost`. The rendering engine (Navigation 3, `navigation3-ui` 1.2.0) is an
implementation detail: no engine type appears in this module's public API.

Coordinates: `io.github.usestapel:stapel-navigation-compose` (group provisional until the first publish).

```kotlin
val ChatNav = navContribution("io.github.usestapel:stapel-chat-ui", VERSION) {
    destination<InboxRoute>(id = "chat.inbox", path = "chat", labelKey = "chat.nav.inbox",
        icon = NavIcons.Message, codec = PathCodec.constant(InboxRoute)) { InboxDestination() }
}

val navigator = rememberStapelNavigator(listOf(AuthNav, ChatNav), policy, authSource, deepLinks)
StapelNavHost(navigator, listOf(AuthNav, ChatNav),
    unavailable = { d, missing -> Placeholder(d, missing) }, waiting = { Splash() })
```
