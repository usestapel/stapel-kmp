plugins {
    id("stapel.substrate")
    id("stapel.serialization")
}

stapel {
    description = "Ktor-free wire runtime that generated Stapel API clients compile against."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelResult)
            api(libs.kotlinx.io.core)
        }
    }
}
