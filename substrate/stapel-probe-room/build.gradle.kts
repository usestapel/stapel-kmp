// BUILD PROBE — not a product module. Proves `stapel.room` (KSP + Room 3 + bundled SQLite) on every
// target before stapel-cache-room builds on it. Delete it once stapel-cache-room is green.
plugins {
    id("stapel.substrate")
    id("stapel.room")
}

stapel {
    description = "Build probe for the stapel.room convention (not a product module)."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
