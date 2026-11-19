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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
