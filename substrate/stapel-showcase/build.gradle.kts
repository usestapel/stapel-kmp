// stapel-showcase: the story API. A story is one component, composite or screen with named states,
// typed knobs, anatomy notes and canvas options; a module exports one StoryCatalog. Stories live in
// consumer modules (bricks/<brick>/demo, showcase/stories/*), never inside a feature library; the
// gallery (showcase/gallery) discovers the catalogues at build time.
plugins {
    id("stapel.substrate-ui")
}

stapel {
    description = "Stapel showcase story API: stories, states, knobs, catalogues and the story canvas."
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.stapelUi)
        }
    }
}
