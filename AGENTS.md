# AGENTS.md — stapel-kmp

Rules for every engineer and coding agent working in this repository.

1. **Start here:** [docs/adding-a-module.md](docs/adding-a-module.md) (module
   families, directory → artifact → package rules) and
   [docs/gates.md](docs/gates.md) (what "green" means).
2. **Shared build files are owned by the build:** `settings.gradle.kts`,
   `gradle/**` (catalog, ranks, allow-lists), `build-logic/**`, `.github/**`.
   A module brigade never edits them; it asks for a one-line change.
3. **Modules are discovered:** creating `substrate/stapel-<x>/build.gradle.kts`
   is how you add one. Your work stays inside your module directories.
4. **Done = the gates passed on your modules**, with evidence:
   `./gradlew :<module>:stapelCheck` (macOS) or `stapelCheckFast` plus the
   iOS/Dokka step, and the ABI dump committed.
5. **Public repository:** no client names, hosts, private paths or personal
   details in any file or commit message. English source, comments and
   commits; no "this used to be X" comments.
6. **Git:** commit only the paths you wrote, by explicit path
   (`git add <paths>` / `git commit --only -- <paths>`); never `add -A`, `.`,
   or a directory you share; never `--no-verify`; never reset, stash or check
   out what you did not write. Activate hooks once: `scripts/setup-hooks.sh`.
7. **Versions:** only from `gradle/libs.versions.toml`; no repositories in
   module build files; no pre-release coordinates except through
   `gradle/prerelease-allowlist.txt`.
8. **Publishing:** agents never publish to Maven Central. Local loops use
   `publishToMavenLocal` or the staging repository under `build/`.
9. **Toolchain:** `source scripts/env.sh` per shell; never `xcode-select -s`,
   never sudo; Gradle only through `./gradlew`.
