plugins {
    id("stapel.substrate-ui")
    id("stapel.serialization")
}

stapel {
    description = "Stapel app shell: adaptive chrome (bar, rail, drawer) by token breakpoints, slots, section and placeholder screens. Material-free; drawn through the StapelSkin."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelNavigationCompose)
            api(projects.stapelUi)
            implementation(libs.kotlinx.coroutines.core)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}
