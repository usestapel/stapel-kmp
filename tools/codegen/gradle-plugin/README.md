# stapel-codegen-gradle-plugin

Generates typed Kotlin clients for an app's **own** backend modules (not the Stapel bricks, which ship
their clients) from the contract the backend emits, with the generator the bricks use.

Plugin id: `io.github.usestapel.codegen` (group provisional until the first publish; version = the train).

```kotlin
plugins { id("io.github.usestapel.codegen") }

stapelCodegen {
    module("orders") {
        contractDir.set(layout.projectDirectory.dir("../contracts/orders"))  // schema.json (+ errors.json, cache.json)
        packageName.set("shop.orders.api")
    }
}
```

`./gradlew stapelGenerate` writes `src/commonMain/generated/api/orders/` (commit it; its `kotlin/` is
added to `commonMain`); `./gradlew stapelGenerateCheck` (part of `check`) fails on drift. The client
compiles against `stapel-wire`.
