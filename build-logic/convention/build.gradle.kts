plugins {
    `kotlin-dsl`
}

group = "stapel.conventions"

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

// The plugins are applied by the convention plugins, so they are runtime
// dependencies of this build (one classloader for every project of the main
// build: the root applies `stapel.root` from the same artifact).
dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.serialization.gradle.plugin)
    implementation(libs.compose.compiler.gradle.plugin)
    implementation(libs.compose.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.dokka.gradle.plugin)
    implementation(libs.vanniktech.publish.gradle.plugin)
    implementation(libs.ksp.gradle.plugin)
    implementation(libs.room3.gradle.plugin)
}

val functionalTest: SourceSet = sourceSets.create("functionalTest")

configurations[functionalTest.implementationConfigurationName].extendsFrom(configurations.testImplementation.get())
configurations[functionalTest.runtimeOnlyConfigurationName].extendsFrom(configurations.testRuntimeOnly.get())

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestImplementation"(platform(libs.junit.bom))
    "functionalTestImplementation"(libs.junit.jupiter)
    "functionalTestImplementation"(libs.kotlin.test.junit5)
    "functionalTestRuntimeOnly"(libs.junit.platform.launcher)
}

gradlePlugin {
    testSourceSets(functionalTest)
    plugins {
        fun plugin(id: String, impl: String) = register(id) {
            this.id = id
            implementationClass = "stapel.conventions.$impl"
        }
        // Families: the one line a module build file starts with.
        plugin("stapel.substrate", "SubstratePlugin")
        plugin("stapel.substrate-ui", "SubstrateUiPlugin")
        plugin("stapel.capability", "CapabilityPlugin")
        plugin("stapel.capability-ui", "CapabilityUiPlugin")
        plugin("stapel.brick-domain", "BrickDomainPlugin")
        plugin("stapel.brick-ui", "BrickUiPlugin")
        plugin("stapel.brick-adapter", "BrickAdapterPlugin")
        plugin("stapel.brick-demo", "BrickDemoPlugin")
        plugin("stapel.brick-e2e", "BrickE2ePlugin")
        plugin("stapel.jvm-tool", "JvmToolPlugin")
        plugin("stapel.showcase", "ShowcasePlugin")
        plugin("stapel.bom", "BomPlugin")
        plugin("stapel.root", "RootPlugin")
        // Add-ons a module applies next to its family plugin.
        plugin("stapel.serialization", "SerializationPlugin")
        plugin("stapel.room", "RoomPlugin")
    }
}

val functionalTestTask = tasks.register<Test>("functionalTest") {
    description = "TestKit inversion tests: every gate must fail on its fixture."
    group = "verification"
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    useJUnitPlatform()
    // Fixture builds resolve real dependencies; share one persistent TestKit home.
    val testKitHome = providers.gradleProperty("stapel.testkitHome")
        .orElse(providers.environmentVariable("STAPEL_TESTKIT_HOME"))
        .orElse(providers.systemProperty("user.home").map { "$it/.gradle/stapel-testkit" })
    systemProperty("stapel.testkitHome", testKitHome.get())
    systemProperty("stapel.repoRoot", rootDir.parentFile.absolutePath)
    maxParallelForks = 1
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.named("check") { dependsOn(functionalTestTask) }

tasks.withType<Test>().configureEach { useJUnitPlatform() }
