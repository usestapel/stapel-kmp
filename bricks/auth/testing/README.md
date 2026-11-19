# stapel-auth-testing

Test kit for apps that install `stapel-auth-ui`: the screens' test tags (`AuthTags`) and walkers that
drive the real sign-in screens on a Compose `ComposeUiTest`, in an app's `commonTest` (desktop JVM and
the iOS simulator).

Coordinates: `io.github.usestapel:stapel-auth-testing` (test configurations only; group provisional
until the first publish; version from `stapel-bom`).

```kotlin
@OptIn(ExperimentalTestApi::class)
@Test
fun signInWalk() = runComposeUiTest {
    setContent { StapelAppContent(app) }                 // the app against a prototype-posture backend
    signInWithMockOtp("walk+${Random.nextInt()}@example.com")
    awaitTag("shop.home")                                // the first member screen
}
```
