plugins {
    id("stapel.substrate")
}

stapel {
    description = "Headless state holders for Stapel clients: MVI presenters and flow machines, no Compose or lifecycle."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelResult)
            api(projects.stapelWire)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.core)
        }
    }
}
