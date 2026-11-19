plugins {
    id("stapel.substrate-ui")
}

stapel {
    description = "Compose retention for Stapel presenters: ViewModel-backed holders, saved state, a factory seam with a Koin binding."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelMvi)
            api(projects.stapelDi)
            api(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation(libs.kotlinx.serialization.json)
        }
        // viewModelScope runs on Dispatchers.Main; a desktop app (and the desktop UI tests) supply it.
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}
