# stapel-shell

The app shell, counterpart of `@stapel/shell-react`: primary navigation chrome chosen by the token
breakpoints (bottom bar on a phone, rail on a tablet, drawer on a desktop), the chrome slots
(`topbar.*`, `sidenav.*`, `bottombar`, `sheet.foot`), the "More" sheet for overflow tabs, section
screens for container roots, the wait screen, named refusals and placeholders. Material-free: it
draws through the `StapelSkin` slots of the theme the caller applies.

Coordinates: `io.github.usestapel:stapel-shell` (group provisional until the first publish).

```kotlin
StapelTheme(skin = Material3Skin) {
    StapelShell(
        ShellConfig(
            contributions = listOf(AuthNav, ChatNav),
            slots = SlotPlan.offered(ShellTemplate.AdaptiveTabs, listOf(AuthNav, ChatNav)),
            policy = NavPolicy(signIn = DestinationId("auth.login")),
            auth = authSource,
            label = { key -> stapelString(StringKey(key)) },
        ),
    )
}
```
