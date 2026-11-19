plugins {
    id("stapel.substrate-ui")
    id("stapel.serialization")
}

stapel {
    description = "Compose navigation host for Stapel clients: contributions, entry-scoped state and back handling over a hidden Navigation 3 engine."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelNavigation)
            // The engine. No type of it appears in this module's public API (see the ABI dump).
            implementation(libs.jb.navigation3.ui)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            // Back handling (NavigationBackHandler) is used directly, so it is declared, not inherited.
            implementation(libs.androidx.navigationevent.compose)
        }
        commonTest.dependencies {
            implementation(projects.stapelMviCompose)
        }
        // viewModelScope runs on Dispatchers.Main; a desktop app (and the desktop UI tests) supply it.
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}
