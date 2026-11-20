# stapel-push-testing — module map

## Provides
- `FakePushProvider`, `pushMessage(...)`, `FakeDeviceRegistry` (share one `journal` list to assert
  the order of provider and registry calls).
- `PushProviderHarness` + `PushProviderContract.verify` (ready provider: identity, permission after
  grant, stable token, delete → new token, rotation reported, events kept until collected, taps as
  `Opened`) and `verifyUnavailable` (unconfigured/unsupported answers everything without throwing).

## Invariants
- The fakes pass the contract; a provider that keeps its token after `deleteToken()` or throws when
  unconfigured fails it (`FakesContractTest`).

## What not to do
- Never a main dependency of a library.
