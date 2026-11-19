// stapel-auth: the auth domain library. `src/commonMain/generated/` is produced by stapel-codegen from
// the pinned contract in contracts/auth/ and is never edited by hand (`stapelGenerateCheck` gates it);
// hand-written code (repositories, presenters, mapping) lives in src/commonMain/kotlin.
plugins {
    id("stapel.brick-domain")
}

stapel {
    description = "Auth domain library for Stapel clients: generated API client, models and errors, sessions and sign-in flows."
    // Generated packages are held by the documentation ratchet (R11) instead of Dokka's zero-undocumented gate.
    ratchetedPackages.add("stapel\\.auth\\.api(\\..*)?")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelWire)
        }
    }
}

// Generated round-trip fixtures (every named example of the pinned contract).
kotlin.sourceSets.getByName("commonTest").kotlin.srcDir("src/commonTest/generated/kotlin")
