// stapel-cdn: the media (stapel-cdn) domain library. `src/commonMain/generated/` is produced by
// stapel-codegen from the pinned contract in contracts/cdn/ and is never edited by hand
// (`stapelGenerateCheck` gates it); hand-written code (the MediaUploader binding) lives in src/commonMain/kotlin.
plugins {
    id("stapel.brick-domain")
}

stapel {
    description = "Media (stapel-cdn) domain library for Stapel clients: generated API client, models and errors, and the MediaUploader other bricks store references from."
    // Generated packages are held by the documentation ratchet (R11) instead of Dokka's zero-undocumented gate.
    ratchetedPackages.add("stapel\\.cdn\\.api(\\..*)?")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelWire)
            api(projects.stapelResult)
            implementation(projects.stapelDi)
        }
        commonTest.dependencies {
            implementation(projects.stapelTesting)
            implementation(projects.stapelConfig)
            implementation(projects.stapelNetwork)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

// Generated round-trip fixtures (every named example of the pinned contract).
kotlin.sourceSets.getByName("commonTest").kotlin.srcDir("src/commonTest/generated/kotlin")
