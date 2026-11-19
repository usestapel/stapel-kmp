# stapel-codegen-gradle-plugin — module notes

- Bundles `tools/codegen/core` (unpublished) into its jar; its external dependencies (KotlinPoet,
  kotlinx-serialization-json) are this artifact's own runtime dependencies.
- Runs the generator in the Gradle daemon (no worker isolation in v0): a build with an incompatible
  KotlinPoet on its buildscript classpath would conflict; none of the plugins a generated app applies
  carries one.
- Ratchets, pins and the name-lock acceptance flow are brick-library concerns; an app's module gets the
  generated tree, the name lock file and the drift check.
- Generated schema examples (brick round-trip tests) are not emitted for app modules.
