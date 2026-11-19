// stapel-auth: the auth domain library. `src/commonMain/generated/` is produced by stapel-codegen from
// the pinned contract in contracts/auth/ and is never edited by hand (`stapelGenerateCheck` gates it);
// hand-written code (session binding, repositories, presenters, mapping) lives in src/commonMain/kotlin.
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
            api(projects.stapelResult)
            api(projects.stapelNetwork)
            api(projects.stapelSession)
            api(projects.stapelCache)
            api(projects.stapelMvi)
            implementation(projects.stapelCacheSession)
            implementation(projects.stapelRuntime)
            implementation(projects.stapelDi)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(projects.stapelTesting)
            implementation(projects.stapelConfig)
        }
    }
}

// Generated round-trip fixtures (every named example of the pinned contract).
kotlin.sourceSets.getByName("commonTest").kotlin.srcDir("src/commonTest/generated/kotlin")
