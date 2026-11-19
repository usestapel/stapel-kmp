// stapel-ui-material3: the default skin. Maps the §68 roles onto a Material 3 ColorScheme, typography
// and shapes, and draws the StapelSkin slots with Material 3 components. The only artifact allowed to
// see Material (gradle/material-allowed.txt); brick UI libraries never depend on it.
plugins {
    id("stapel.substrate-ui")
}

stapel {
    description = "Material 3 default skin for Stapel UI: role-to-ColorScheme mapping and the Material3Skin slots."
    optIn("androidx.compose.material3.ExperimentalMaterial3Api")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelUi)
            api(libs.compose.material3)
        }
        getByName("skikoTest").dependencies {
            implementation(projects.showcase.skinContract)
        }
    }
}
