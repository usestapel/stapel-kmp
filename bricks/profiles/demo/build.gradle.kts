// stapel-profiles-ui-demo (unpublished): the stories of every profiles screen state for the showcase gallery
// (`./gradlew :showcase:desktop:run`), its screenshot baselines (snapshots/) and the docs export.
plugins {
    id("stapel.brick-demo")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.stapelProfilesTesting)
        }
        getByName("skikoTest").dependencies {
            implementation(projects.stapelUiMaterial3)
        }
    }
}
