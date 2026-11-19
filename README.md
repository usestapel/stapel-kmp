# stapel-kmp

Kotlin / Compose Multiplatform libraries of the Stapel framework: for every
backend brick, a **domain library** (pure Kotlin, no Compose) and a **UI
library** (Compose Multiplatform with a default skin), on a thin modular
substrate. Targets: Android, iOS (arm64, simulator arm64), JVM desktop.

> Status: bootstrap. The build, its gates and two build-probe modules exist;
> the substrate and the first brick (auth) are being built. Nothing is
> published to Maven Central yet.

## Layout

```
substrate/stapel-<x>/      small substrate artifacts (result, network, session, cache, ui, …)
capabilities/stapel-<x>/   optional backend-core capabilities a project opts into
bricks/<brick>/domain/     stapel-<brick>       pure Kotlin (generated API client + repositories + presenters)
bricks/<brick>/ui/         stapel-<brick>-ui    Compose Multiplatform screens on the skin seam
bricks/<brick>/demo/       demos (never published)
tools/                     generators (JVM)
build-logic/               convention plugins (stapel.*) and the gates
gradle/libs.versions.toml  the one version catalog
consumer-smoke/            separate build consuming the published artifacts by coordinates
```

Dependency direction is strictly downward and enforced by the build:
`stapel-<brick>-ui → stapel-<brick> → substrate`; a brick never depends on
another brick.

## Using it

Coordinates are `io.github.usestapel:stapel-<module>` (provisional group; one
version for every module, aligned by `stapel-bom`). Until the first Central
release, build the artifacts locally:

```sh
source scripts/env.sh
./gradlew publishToMavenLocal          # or publishAllPublicationsToStagingRepository → build/staging-repo
```

## Developing

```sh
brew install openjdk@21
source scripts/env.sh        # JAVA_HOME, ANDROID_HOME, DEVELOPER_DIR for this shell only
scripts/setup-hooks.sh       # pre-push hook
scripts/doctor.sh            # checks the toolchain, prints fixes
./gradlew stapelCheckFast    # jvm + android host tests + all gates
./gradlew stapelCheck        # + iOS simulator tests + Dokka gate + staging publish (macOS)
```

- Adding a module: [docs/adding-a-module.md](docs/adding-a-module.md)
- Gates: [docs/gates.md](docs/gates.md)
- Toolchain and measured versions: [docs/toolchain.md](docs/toolchain.md)
- Releasing: [docs/release.md](docs/release.md)

## License

MIT — see [LICENSE](LICENSE).
