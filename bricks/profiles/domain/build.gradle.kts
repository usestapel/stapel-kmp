// stapel-profiles: the profiles domain library. `src/commonMain/generated/` is produced by stapel-codegen
// from the pinned contract in contracts/profiles/ and is never edited by hand (`stapelGenerateCheck`
// gates it); hand-written code (repositories, the avatar seam, presenters, mapping) lives in
// src/commonMain/kotlin.
plugins {
    id("stapel.brick-domain")
}

stapel {
    description = "Profiles domain library for Stapel clients: generated API client, models and errors, profile, language, notification, contact and relationship repositories and presenters."
    // Generated packages are held by the documentation ratchet (R11) instead of Dokka's zero-undocumented gate.
    ratchetedPackages.add("stapel\\.profiles\\.api(\\..*)?")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelWire)
            api(projects.stapelResult)
            api(projects.stapelCache)
            api(projects.stapelMvi)
            api(projects.stapelImage)
            implementation(projects.stapelRuntime)
            implementation(projects.stapelSession)
            implementation(projects.stapelDi)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(projects.stapelTesting)
            implementation(projects.stapelConfig)
            implementation(projects.stapelNetwork)
        }
    }
}

// Generated round-trip fixtures (every named example of the pinned contract).
kotlin.sourceSets.getByName("commonTest").kotlin.srcDir("src/commonTest/generated/kotlin")
