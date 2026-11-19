// stapel-testing: deterministic fakes of the substrate seams, for tests of substrate, bricks and apps.
// Published so brick and app test source sets depend on it (testImplementation); never a main dependency.
plugins {
    id("stapel.substrate")
}

stapel {
    description = "Test doubles for Stapel clients: wire transports, clock, dispatchers, ids, locale, connectivity, logger."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelResult)
            api(projects.stapelRuntime)
            api(projects.stapelConfig)
            api(projects.stapelDi)
            api(projects.stapelWire)
            api(projects.stapelNetwork)
            api(projects.stapelSession)
            api(libs.kotlinx.coroutines.test)
        }
    }
}
