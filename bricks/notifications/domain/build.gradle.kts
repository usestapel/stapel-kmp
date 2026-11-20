// stapel-notifications: the notifications domain library. `src/commonMain/generated/` is produced by
// stapel-codegen from the pinned contract in contracts/notifications/ and is never edited by hand
// (`stapelGenerateCheck` gates it); hand-written code is the Koin factory only for now (the feed and
// preferences presenters come with the notifications brick UI).
plugins {
    id("stapel.brick-domain")
}

stapel {
    description = "Notifications domain library for Stapel clients: generated API client, models and errors (device registry, feed, notification keys)."
    // Generated packages are held by the documentation ratchet (R11) instead of Dokka's zero-undocumented gate.
    ratchetedPackages.add("stapel\\.notifications\\.api(\\..*)?")
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
        }
    }
}

// Generated round-trip fixtures (every named example of the pinned contract).
kotlin.sourceSets.getByName("commonTest").kotlin.srcDir("src/commonTest/generated/kotlin")
