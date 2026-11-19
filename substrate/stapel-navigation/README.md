# stapel-navigation

Pure navigation model for Stapel clients (no Compose): manifests UI libraries declare, `resolveNav`
(the same path rule as the web route plan), a serializable back stack `NavState` changed only by the
pure `reduceNav`, guards (surface × requiresAuth × auth state), redirect-and-return, deep links with
web-equal paths, typed results and the `StapelNavigator`.

Coordinates: `io.github.usestapel:stapel-navigation` (group provisional until the first publish).

```kotlin
val nav = resolveNav(listOf(authManifest, chatManifest))
val navigator = StapelNavigator(nav, NavPolicy(signIn = DestinationId("auth.login")), authSource, scope,
    DeepLinkResolver(nav, DeepLinkConfig(schemes = setOf("demo"), hosts = setOf("demo.example"))))
navigator.navigate(ThreadRoute("42"))          // signed out: sign-in first, then the thread
navigator.open("https://demo.example/app/chat/t/42")
```

Rendering lives in `stapel-navigation-compose`; chrome in `stapel-shell`.
