// stapel-auth-ui: the auth screens with the default skin. Material-free (R9): screens draw through the
// StapelSkin slots of stapel-ui only; the app picks the skin (Material3Skin, FoundationSkin or its own).
// Every screen is a stateless composable of a state class plus an event callback; AuthScreens is the
// override table; authNavContribution() registers the destinations with the web ids and paths.
plugins {
    id("stapel.brick-ui")
    id("stapel.serialization")
}

stapel {
    description = "Auth screens for Stapel clients: stateless sign-in, code, challenge, guest and session screens over the StapelSkin seam."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelUi)
            api(projects.stapelNavigationCompose)
            api(projects.stapelMviCompose)
        }
        getByName("skikoTest").dependencies {
            // Tests render every screen with both shipped skins; test classpaths are not dependency edges.
            implementation(projects.stapelUiMaterial3)
            // AuthBrickTest composes the descriptor through stapelApp over the test environment.
            implementation(projects.stapelTesting)
        }
        jvmTest.dependencies {
            // The app's content collects with repeatOnLifecycle, which needs a Main dispatcher on the desktop.
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}
