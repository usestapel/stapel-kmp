plugins {
    id("stapel.substrate")
}

stapel {
    description = "Test kit for stapel-analytics: fake sink, recording Analytics and the conformance suite every analytics provider must pass."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelAnalytics)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
