// The consumer codegen plugin: `plugins { id("io.github.usestapel.codegen") }` + `stapelCodegen { module("orders") { … } }`
// generates an app's own backend clients with the same generator the bricks use (tools/codegen/core is bundled
// into this jar), with `stapelGenerateCheck` as the drift gate. Plugin id = <stapel.group>.codegen.
plugins {
    id("stapel.gradle-plugin")
}

stapel {
    description = "Gradle plugin generating typed Kotlin clients for an app's own Stapel backend modules from their contracts, with a drift check."
    pluginImplementationClass.set("stapel.tools.codegen.gradle.StapelCodegenPlugin")
}

dependencies {
    stapelBundled(projects.tools.codegen.core)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinx.serialization.json)
    compileOnly(libs.kotlin.gradle.plugin)
    testImplementation(gradleTestKit())
}

tasks.withType<Test>().configureEach {
    systemProperty("stapel.testkitHome", providers.systemProperty("user.home").map { "$it/.gradle/stapel-testkit" }.get())
}
