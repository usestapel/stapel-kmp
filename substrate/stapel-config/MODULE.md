# stapel-config — module map

Rank 1 substrate; depends on `stapel-result` only. No coroutines, no platform handle.

## Seams

| Seam | Who |
|---|---|
| `StapelEnvironment` | emitted by the scaffold per build flavour; consumed by `stapel-network` (`url`, `endpoint`), realtime (`websocketOrigin`) |
| `StapelEnvironment.url(module, path)` | the one place a generated canonical path (`/auth/api/v1/…`) becomes an absolute URL: kept as is on a bare-origin endpoint (monolith), the `/<module>/api/v<N>/` mount replaced by the endpoint's path otherwise |
| `ServiceEndpoint` | base URL of one module's mount; `origin` is what the bearer is scoped to |
| `ClientInfo`, `platformClientInfo` | `User-Agent` and `Sec-CH-UA-*` client hints sent by the network layer |
| `Posture` | network (body logging, pins), scaffold |

## Invariants

- Cleartext `http://` is refused at construction under `Posture.Live`, and under `Posture.Prototype`
  except to loopback / RFC 1918 / link-local / `*.local` / `localhost` hosts (the Android emulator's
  `10.0.2.2` is RFC 1918).
- `endpoint(module)` fails loudly naming the module when nothing serves it.
- `ClientInfo.sdkVersion` is generated from the build version (`generateStapelSdkVersion`).

## Not here

- No feature flags and no runtime config fetch: the backend exposes neither.
