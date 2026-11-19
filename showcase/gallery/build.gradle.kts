// The gallery (unpublished): every story catalogue of the repository — showcase/stories/* and every
// bricks/<brick>/demo — discovered at build time by stapel.showcase-gallery (no hand-edited list).
// Built on stapel-navigation: a page per catalogue and per story, each with a route and a deep link.
// Hosted by showcase/desktop. The same catalogues feed the screenshot baselines
// (recordScreenshots / verifyScreenshots, macOS) and the docs export (exportCatalog); see README.md.
plugins {
    id("stapel.showcase")
    id("stapel.showcase-gallery")
    id("stapel.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelUiMaterial3)
            implementation(projects.stapelNavigationCompose)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}
