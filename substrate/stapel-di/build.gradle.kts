// stapel-di: the Koin conventions every stapel-kmp library's `di` package follows (ruling R6).
plugins {
    id("stapel.substrate")
}

stapel {
    description = "Koin conventions for Stapel clients: strict composition root, contributions, session scope."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.koin.core)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
