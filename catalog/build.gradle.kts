// stapel-catalog: the Gradle version catalog a generated app imports
// (`versionCatalogs { create("libs") { from("io.github.usestapel:stapel-catalog:<train>") } }`): every published
// stapel artifact and Gradle plugin at the train version, plus the toolchain and library pins the app's own build
// files name (Kotlin, AGP, Compose Multiplatform, Koin, coroutines, serialization). Contents come from the build
// (stapel.catalog); checkPublishedCatalog refuses a pre-release version or a consumer module.
plugins {
    id("stapel.catalog")
}
