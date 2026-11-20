# stapel-push-testing

Test kit for `stapel-push`: `FakePushProvider` (scriptable OS permission, token minting, rotation,
incoming notifications and taps), `FakeDeviceRegistry` (the notifications backend's rules: one row
per installation and account, 409 for a token live elsewhere, 403 for guests) and
`PushProviderContract`, the suite every push provider artifact passes on every target.

Coordinates: `io.github.usestapel:stapel-push-testing` (provisional group). Test source sets only.

```kotlin
@Test fun contract() = runTest { PushProviderContract.verify { MyProviderHarness() } }
@Test fun unconfigured() = runTest { PushProviderContract.verifyUnavailable(myProvider(configured = false)) }
```
