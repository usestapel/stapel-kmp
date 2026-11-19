plugins {
    id("stapel.substrate")
    id("stapel.serialization")
}

stapel {
    description = "Session for Stapel clients: secure token vault, single-flight refresh, session state and logout hooks."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelResult)
            api(projects.stapelRuntime)
            api(projects.stapelNetwork)
            implementation(projects.stapelDi)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(projects.stapelConfig)
            implementation(projects.stapelTesting)
        }
    }
}
