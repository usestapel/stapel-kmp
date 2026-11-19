// tools/tokens-gen: the Kotlin port of @stapel/tokens' resolver (merge -> validate incl. the contrast
// gate -> resolve, held byte-equal to the Node implementation by tokens-source/conformance) and the
// `compose@1` renderer that writes stapel-tokens' generated sources and an app's own tokens object.
plugins {
    id("stapel.jvm-tool")
    id("stapel.serialization")
}

dependencies {
    implementation(libs.kotlinpoet)
}

val tokensSource = rootProject.layout.projectDirectory.dir("tokens-source")

// The bundled default theme and ramps: an app's host theme deep-merges over these offline.
tasks.named<ProcessResources>("processResources") {
    from(tokensSource) {
        include("theme.default.json", "ramps.standard.json", "presets.json", "SOURCE.json")
        into("stapel/tools/tokens")
    }
}

tasks.named<Test>("test") {
    systemProperty("stapel.repoRoot", rootDir.absolutePath)
    inputs.dir(tokensSource).withPathSensitivity(PathSensitivity.RELATIVE)
}
