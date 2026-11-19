// The gallery (unpublished): every StapelSkin slot, the token roles and the gutter ruler, side by side
// in light and dark, under Material3Skin or FoundationSkin and any theme preset. Hosted by
// showcase/desktop; the jvm tests here also measure screenshot capture (no baselines yet).
plugins {
    id("stapel.showcase")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelUiMaterial3)
        }
        jvmTest.dependencies {
            implementation(libs.roborazzi.compose.desktop)
        }
    }
}

tasks.withType<Test>().configureEach {
    // Measurement of ARCH-UI §testing: capture with no display at all.
    systemProperty("java.awt.headless", "true")
    // Pinned so captures from different machines can be compared.
    systemProperty("user.language", "en")
    systemProperty("user.country", "US")
    systemProperty("user.timezone", "UTC")
    systemProperty("roborazzi.test.record", "true")
    systemProperty("stapel.showcase.captureDir", layout.buildDirectory.dir("captures").get().asFile.absolutePath)
}
