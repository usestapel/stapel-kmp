// stapel-codegen core: contract parser (OpenAPI 3.0.3 + errors/flows/capabilities/cache plan/overlay)
// → IR → KotlinPoet emitters, pins, report and ratchets. JVM only, never published in v0.
plugins {
    id("stapel.jvm-tool")
    `java-library`
}

dependencies {
    implementation(libs.kotlinpoet)
    api(libs.kotlinx.serialization.json)
}

tasks.withType<Test>().configureEach {
    systemProperty("stapel.repoRoot", rootDir.absolutePath)
    providers.environmentVariable("UPDATE_GOLDEN").orNull?.let { systemProperty("stapel.updateGolden", it) }
}
