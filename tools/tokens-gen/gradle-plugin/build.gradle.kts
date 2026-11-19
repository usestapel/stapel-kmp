// The tokens plugin: `plugins { id("io.github.usestapel.tokens") }` + `stapelTokens { theme.set(file("stapel.theme.json")) }`
// turns an app's theme into a StapelTokens value at build time with the Kotlin resolver of tools/tokens-gen
// (bundled into this jar, with its default theme and ramps). No Node. Plugin id = <stapel.group>.tokens.
plugins {
    id("stapel.gradle-plugin")
}

stapel {
    description = "Gradle plugin turning an app's stapel.theme.json into a StapelTokens value at build time (no Node), with theme validation."
    pluginImplementationClass.set("stapel.tools.tokens.gradle.StapelTokensPlugin")
}

dependencies {
    stapelBundled(projects.tools.tokensGen)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinx.serialization.json)
    compileOnly(libs.kotlin.gradle.plugin)
    testImplementation(gradleTestKit())
}

tasks.withType<Test>().configureEach {
    systemProperty("stapel.testkitHome", providers.systemProperty("user.home").map { "$it/.gradle/stapel-testkit" }.get())
    systemProperty("stapel.repoRoot", rootDir.absolutePath)
}
