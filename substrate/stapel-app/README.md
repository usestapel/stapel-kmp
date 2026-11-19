# stapel-app

One composition entry for a Stapel app: `stapelApp { }` builds the Koin graph from the substrate and
the bricks you install, and `StapelAppContent(app)` draws theme, strings, navigation and shell.

Coordinates: `io.github.usestapel:stapel-app` (group provisional until the first publish; versions from `stapel-bom`).

```kotlin
val app = stapelApp(PlatformContext()) {
    environment(StapelEnvironment.single("dev", "http://localhost:8000/", Posture.Prototype, clientInfo))
    offline(cacheRoomModule())                       // optional: durable cache
    bricks(AuthBrick())                              // one StapelBrick per installed brick UI library
    navigation(home = DestinationId("shop.home"))
    theme { content -> StapelMaterialTheme(tokens = AppTokens, content = content) }
}
// Compose: StapelAppContent(app)        Deep links: app.openUrl(url)        On exit: app.close()
```

Everything the builder does is public API: an app may call `stapelKoin(...)`, the modules'
`<module>Module()` factories and `StapelShell(...)` itself instead.
