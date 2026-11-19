// The skin conformance suite (unpublished): assertSkinContract(skin) renders every slot of a skin and
// asserts the slot duties on the semantics tree. Used by the tests of stapel-ui (FoundationSkin) and
// stapel-ui-material3 (Material3Skin); a project runs it against its own skin. Becomes the published
// stapel-ui-testing artifact once that module exists.
plugins {
    id("stapel.showcase")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelUi)
            api(libs.compose.ui.test)
            implementation(kotlin("test"))
        }
    }
}
