# Contributing to stapel-kmp

Thanks for helping. The short version:

1. `brew install openjdk@21`, then `source scripts/env.sh` and `scripts/doctor.sh`.
2. `uv tool install 'stapel-tools>=0.82.0'` (the pre-push hook's
   `stapel-exposure-lint`; an older install: `uv tool upgrade stapel-tools`), then
   `scripts/setup-hooks.sh` once per clone — it fails with that line until the
   lint is on PATH, and `scripts/doctor.sh` reports it too.
3. Read [docs/adding-a-module.md](docs/adding-a-module.md) before creating a
   module, and [docs/gates.md](docs/gates.md) before calling something done.
4. Run `./gradlew stapelCheckFast` for every change and `./gradlew stapelCheck`
   (macOS) before opening a pull request. Commit ABI dump changes together with
   the API change that caused them.
5. A change to a published module adds a change note under `.changes/`
   (see [.changes/README.md](.changes/README.md)).
6. Dependencies come only from `gradle/libs.versions.toml`; ask in the pull
   request if you need a new one.
7. The minimum iOS is **15.0** (`IOS_FLOOR` in `build-logic`, see
   [docs/gates.md](docs/gates.md#the-ios-floor)). Never raise a deployment
   target to reach a newer API: Swift calls it under `if #available(iOS N, *)`
   (or `@available`) with an iOS 15 path, Kotlin in `iosMain` behind an OS
   version check (`NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion`)
   or `respondsToSelector` with a fallback, and the pull request names the
   guarded call.
8. Releases are cut by maintainers only; the first Maven Central publish is a
   maintainer bootstrap ([docs/release.md](docs/release.md)).

Code style: Kotlin official style (`.editorconfig`), English everywhere, KDoc on
every public symbol (the Dokka gate enforces it), explicit API mode.
