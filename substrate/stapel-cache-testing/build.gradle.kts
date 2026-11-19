plugins {
    id("stapel.substrate")
}

stapel {
    description = "Test kit for stapel-cache: the storage contract suite every cache store must pass."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelCache)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
