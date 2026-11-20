# stapel-profiles-ui

The profiles screens of Stapel clients (Compose Multiplatform, Material-free): the settings container
(avatar, display name, theme, and a row for every section installed under it — language,
notifications, phone numbers, and other libraries' such as `auth.security`), the language screen, the
notification switches, the phone numbers with SMS confirmation, connections (followers / following /
blocked) and a person's public profile with its `page.actions` slot. Every screen is a stateless
composable of its presenter's state and callbacks, drawn only through `stapel-ui`'s `StapelSkin` slots.

Coordinates: `io.github.usestapel:stapel-profiles-ui` (depends on `stapel-profiles`).

```kotlin
val app = stapelApp(PlatformContext()) {
    environment(environment)
    bricks(AuthBrick(), ProfilesBrick(picker = myPhotoPicker))   // the picker is the one avatar wiring point
    navigation(home = DestinationId("profiles.settings"))
}
// Or by hand: profilesNavContribution(version, settings = { ProfileSettings(profiles, picker) }, …)
```

Replace one screen: `object MyScreens : ProfilesScreens by DefaultProfilesScreens { override fun Contacts(…) }`,
passed as `ProfilesBrick(screens = MyScreens)` (provided through `LocalProfilesScreens`).

Stories of every state: `bricks/profiles/demo` (in the showcase gallery).
