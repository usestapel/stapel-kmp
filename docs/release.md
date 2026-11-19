# Releasing

One version (`VERSION_NAME` in `gradle.properties`) for every published module,
released together as one train and aligned by `stapel-bom`. Pre-1.0:
`0.MINOR.PATCH`, a minor is breaking. Maven Central is immutable: a published
version can never be replaced or deleted, so a failed release is a patch bump,
never a moved tag.

## State today

- The release workflow (`.github/workflows/release.yml`) exists and is
  **disabled**: it runs only when the repository variable
  `STAPEL_RELEASE_ENABLED` is `true` and the `maven-central` environment holds
  the secrets below. Nothing has been published.
- Not built yet: `stapelVersion` as a version bumper (today it prints the
  version), `checkChangeNote`, `checkAbiLevel`, `verify-central.yml`, Dokka site
  deployment, dependency verification metadata.

## Train (once the bootstrap is done)

1. Every change to a published module carries a note in `.changes/`.
2. Version commit (alone): bump `VERSION_NAME`, prepend the CHANGELOG section,
   delete the consumed notes. Push it to `main`; wait for a green `ci-ok`.
3. Annotated tag on that commit, pushed by name:
   `git tag -a vX.Y.Z -m vX.Y.Z && git push origin vX.Y.Z`. The pre-push hook
   refuses a tag whose tree, `VERSION_NAME` or `.changes/` does not match, or
   that is not on `origin/main`.
4. Run the `release` workflow with the tag. It re-checks the guards, runs
   `stapelGenerateCheck checkKotlinAbi checkReleaseCoordinates` (no pre-release
   coordinate may reach a POM) and uploads with `publishToMavenCentral`.
5. In the Central Portal, open Deployments, read the validation, press
   **Publish**.
6. Verify on the index, not by the upload: every module's POM answers 200 at
   `https://repo1.maven.org/maven2/<group path>/<artifact>/<version>/`. Only then
   is the version released and may the scaffold pin it.

## Owner bootstrap (maintainers only, once)

1. Decide the Maven group (provisional `io.github.usestapel`; changing it before
   the first publish touches only `stapel.group` in `gradle.properties` — Kotlin
   packages are independent of it).
2. Central Portal: sign in, add and verify the namespace (DNS TXT for an own
   domain, or the documented verification route for `io.github.*`).
3. Generate a Portal user token (username + password pair; shown once).
4. Create a PGP key (`gpg --gen-key`), publish the public key to a key server,
   keep an offline backup and a revocation certificate; Central verifies against
   the primary key.
5. GitHub: environment `maven-central` with a required reviewer and deployments
   restricted to `v*` tags; secrets `MAVEN_CENTRAL_USERNAME`,
   `MAVEN_CENTRAL_PASSWORD`, `SIGNING_IN_MEMORY_KEY` (armored secret key),
   `SIGNING_KEY_ID`, `SIGNING_KEY_PASSWORD`; repository variable
   `STAPEL_RELEASE_ENABLED=true`.
6. Before the first publish: move Compose Multiplatform to the stable 1.13.x line
   (ruling R1) and add `gradle/verification-metadata.xml`.
