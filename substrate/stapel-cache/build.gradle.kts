plugins {
    id("stapel.substrate")
}

stapel {
    description = "Cache seam for Stapel clients: read policies, Resource flows, partitions and the storage SPI."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelResult)
            api(projects.stapelWire)
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
        }
    }
}
