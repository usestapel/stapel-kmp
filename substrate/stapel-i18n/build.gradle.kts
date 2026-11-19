// stapel-i18n: the UI-string engine (flat dictionaries, {param}, CLDR plural keys, floor -> brick -> app
// override order, runtime locale as state). Pure Kotlin; error-code text is stapel-result's ErrorTextResolver.
plugins {
    id("stapel.substrate")
}

stapel {
    description = "UI strings for Stapel clients: layered dictionaries, named parameters, plural rules, runtime locale."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
