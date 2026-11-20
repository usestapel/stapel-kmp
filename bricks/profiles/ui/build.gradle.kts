// stapel-profiles-ui: the profiles screens with the default skin. Material-free (R9): screens draw
// through the StapelSkin slots of stapel-ui only; the app picks the skin. Every screen is a stateless
// composable of its presenter's state plus an event callback; ProfilesScreens is the override table;
// profilesNavContribution() registers the destinations with the web ids and paths.
plugins {
    id("stapel.brick-ui")
    id("stapel.serialization")
}

stapel {
    description = "Profiles screens for Stapel clients: settings, language, notifications, phone numbers, connections and public profiles over the StapelSkin seam."
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
            implementation(projects.stapelTesting)
            // The media library an app installs next to profiles (avatar upload wiring).
            implementation(projects.stapelCdn)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}
