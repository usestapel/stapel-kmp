// stapel-input-compose: binds stapel-input formats to the state-based text field
// (TextFieldState + InputTransformation / OutputTransformation). Depends on foundation only: a skin's
// TextField / CodeField slot takes the two transformations as plain foundation types, so stapel-ui
// never depends on this artifact.
plugins {
    id("stapel.substrate-ui")
}

stapel {
    description = "Compose binding of Stapel input masks: input and output transformations for state-based text fields."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelInput)
        }
    }
}
