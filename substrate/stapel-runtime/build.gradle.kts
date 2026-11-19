// stapel-runtime: the platform seams every other substrate artifact injects instead of reading
// ambient state — dispatchers, clock, ids, locale, time zone, lifecycle, connectivity, logging,
// telemetry and the analytics type seam.
plugins {
    id("stapel.substrate")
}

stapel {
    description = "Platform seams for Stapel clients: dispatchers, clock, locale, lifecycle, connectivity, logging."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelResult)
            api(projects.stapelDi)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
