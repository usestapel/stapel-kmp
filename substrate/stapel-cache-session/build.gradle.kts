plugins {
    id("stapel.substrate")
}

stapel {
    description = "Binds stapel-cache partitions to the stapel-session account: open on sign-in, wipe on session end."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelCache)
            api(projects.stapelSession)
            implementation(projects.stapelDi)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(projects.stapelTesting)
        }
    }
}
