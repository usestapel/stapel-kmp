# stapel-config

Environment, service endpoints, posture and client identity. Pure data: no I/O, no parsing.

```kotlin
implementation("io.github.usestapel:stapel-config")   // version from stapel-bom
```

```kotlin
val env = StapelEnvironment(
    name = "production",
    posture = Posture.Live,
    defaultEndpoint = ServiceEndpoint("https://api.example.com/"),
    client = platformClientInfo(appId = "com.example.app", appVersion = "1.0", build = "1"),
)
env.url("auth", "/auth/api/v1/otp/request/")   // https://api.example.com/auth/api/v1/otp/request/
```

See [MODULE.md](MODULE.md).
