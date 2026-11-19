// Stories of stapel-ui (unpublished): every StapelSkin slot, the composites and the foundations
// (colour roles, type scale, spacing, the page gutter), drawn through the wrappers bricks use.
// Exports `val storyCatalog`; the gallery discovers this module by its directory.
plugins {
    id("stapel.showcase")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelShowcase)
            api(projects.stapelUi)
        }
        getByName("skikoTest").dependencies {
            implementation(projects.stapelUiMaterial3)
        }
    }
}
