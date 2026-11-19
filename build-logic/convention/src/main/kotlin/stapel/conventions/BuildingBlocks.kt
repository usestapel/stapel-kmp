package stapel.conventions

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import java.io.File

/** Fully-qualified names of the opt-in markers excluded from ABI dumps and docs (CORE owns the class). */
internal val INTERNAL_API_MARKERS = listOf(
    "stapel.result.InternalStapelApi",
    "stapel.result.StapelInternalApi",
)

/** `stapel.base`: the extension, derived names, coordinates, the source-shape checks. */
internal fun Project.applyStapelBase(family: StapelFamily): StapelExtension {
    val names = stapelNames()
    if (name != names.artifactId && family !in setOf(StapelFamily.TOOL, StapelFamily.SHOWCASE, StapelFamily.GRADLE_PLUGIN)) {
        throw GradleException(
            "Project '$path' in '${projectDir.relativeTo(rootDir)}' must be named '${names.artifactId}' " +
                "(settings derives it from the directory).",
        )
    }
    val ext = extensions.create("stapel", StapelExtension::class.java)
    ext.family.set(family)
    ext.family.finalizeValue()
    names.brick?.let { ext.brick.set(it) }
    ext.brick.finalizeValue()
    extensions.extraProperties.set(FAMILY_PROPERTY, family.name)
    extensions.extraProperties.set(BRICK_PROPERTY, names.brick ?: "")

    group = mavenGroup(this)
    version = versionName(this)

    registerLifecycleTasks()
    registerSourceChecks(family, names, ext)
    applyLibraryLines()
    return ext
}

internal const val FAMILY_PROPERTY = "stapel.family"
internal const val BRICK_PROPERTY = "stapel.brick"

/** `stapelCheckFast` / `stapelCheck` in every project; `./gradlew stapelCheckFast` runs them all by name. */
internal fun Project.registerLifecycleTasks() {
    val fast = tasks.register("stapelCheckFast") {
        group = "verification"
        description = "Everything that runs on Linux: jvm + android host tests, all stapel gates, ABI, generator drift."
    }
    tasks.register("stapelCheck") {
        group = "verification"
        description = "stapelCheckFast + iOS simulator tests + Dokka gate + staging publish (macOS)."
        dependsOn(fast)
    }
    fast.configure {
        dependsOn(tasks.matching { it.name.startsWith("check") && it.name != "check" && it.group == STAPEL_GATES })
        dependsOn(tasks.matching { it.name == "stapelGenerateCheck" })
    }
}

internal const val STAPEL_GATES = "stapel gates"

/** `stapel.kmp`: the four D2 targets, explicit API, warnings as errors, test wiring. */
@OptIn(ExperimentalKotlinGradlePluginApi::class)
internal fun Project.applyStapelKmp(ext: StapelExtension, explicitApi: Boolean = true) {
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    pluginManager.apply("com.android.kotlin.multiplatform.library")
    val names = stapelNames()
    val jvmTarget = JvmTarget.fromTarget(catalogVersion("jvm-target"))
    val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)

    if (explicitApi) kmp.explicitApi()
    kmp.compilerOptions {
        allWarningsAsErrors.set(true)
        progressiveMode.set(true)
        optIn.addAll(ext.optIns)
    }
    kmp.jvm {
        compilerOptions {
            this.jvmTarget.set(jvmTarget)
            freeCompilerArgs.add("-Xjdk-release=${catalogVersion("jvm-target")}")
        }
    }
    kmp.iosArm64()
    kmp.iosSimulatorArm64()

    kmp.extensions.configure(KotlinMultiplatformAndroidLibraryTarget::class.java) {
        namespace = names.packageName
        compileSdk = catalogVersion("android-compileSdk").toInt()
        minSdk = catalogVersion("android-minSdk").toInt()
        withHostTest { }
        compilerOptions { this.jvmTarget.set(jvmTarget) }
    }

    kmp.sourceSets.getByName("commonTest").dependencies {
        implementation(lib("kotlin-test"))
        implementation(lib("kotlinx-coroutines-test"))
    }

    tasks.withType(Test::class.java).configureEach {
        // JUnit 4 runner for kotlin-test on jvm/android host; Gradle picks the framework from kotlin-test.
        testLogging {
            events("failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
    wireVerifyTestsRan()
    registerCheckLibraryLines()

    tasks.named("stapelCheckFast").configure {
        dependsOn(tasks.withType(Test::class.java))
        dependsOn(tasks.matching { it.name == "checkKotlinAbi" })
    }
    tasks.named("stapelCheck").configure {
        dependsOn(tasks.matching { it.name == "iosSimulatorArm64Test" })
    }
}

/**
 * `verifyTestsRan`: a test task of a module that has common tests must have executed at least one
 * test. Guards against a target silently not wired into the test tree.
 */
internal fun Project.wireVerifyTestsRan() {
    val commonTestDir = file("src/commonTest")
    tasks.withType(AbstractTestTask::class.java).configureEach {
        val xmlDir = reports.junitXml.outputLocation
        val taskName = name
        doLast("verifyTestsRan") {
            val hasCommonTests = commonTestDir.walkTopDown().any { it.isFile && it.extension == "kt" }
            if (!hasCommonTests) return@doLast
            val executed = countExecutedTests(xmlDir.get().asFile)
            if (executed == 0) {
                throw GradleException(
                    "verifyTestsRan: '$taskName' executed 0 tests although src/commonTest has tests. " +
                        "The target is not wired into the test tree, or a filter excludes everything.",
                )
            }
            logger.info("verifyTestsRan: $taskName executed $executed test(s)")
        }
    }
}

private val TESTS_ATTR = Regex("""<testsuite\b[^>]*?\btests="(\d+)"[^>]*?\bskipped="(\d+)"""")

/** Sum of executed (non-skipped) tests over the JUnit XML files in [dir]. */
fun countExecutedTests(dir: File): Int {
    if (!dir.isDirectory) return 0
    return dir.walkTopDown().filter { it.isFile && it.extension == "xml" }.sumOf { f ->
        TESTS_ATTR.findAll(f.readText()).sumOf { m -> m.groupValues[1].toInt() - m.groupValues[2].toInt() }
    }
}

/** `stapel.compose`: Compose Multiplatform + compiler, base deps, UI tests on the skiko targets. */
@OptIn(ExperimentalKotlinGradlePluginApi::class)
internal fun Project.applyStapelCompose() {
    pluginManager.apply("org.jetbrains.compose")
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
    val names = stapelNames()
    val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)

    // jvm + iOS share Skiko: `skikoMain`/`skikoTest` hold code (and Compose UI tests) that do not
    // run on Android host tests.
    kmp.applyDefaultHierarchyTemplate {
        common {
            group("skiko") {
                withJvm()
                group("ios") { withIos() }
            }
        }
    }

    kmp.sourceSets.getByName("commonMain").dependencies {
        api(lib("compose-runtime"))
        api(lib("compose-ui"))
        api(lib("compose-foundation"))
        implementation(lib("compose-components-resources"))
    }
    kmp.sourceSets.getByName("commonTest").dependencies {
        implementation(lib("compose-ui-test"))
    }
    kmp.sourceSets.getByName("jvmTest").dependencies {
        implementation(desktopRuntimeCoordinate())
    }
    kmp.extensions.configure(KotlinMultiplatformAndroidLibraryTarget::class.java) {
        androidResources { enable = true }
    }
    (extensions.getByType(org.jetbrains.compose.ComposeExtension::class.java) as org.gradle.api.plugins.ExtensionAware)
        .extensions.configure(org.jetbrains.compose.resources.ResourcesExtension::class.java) {
        publicResClass = false
        packageOfResClass = "${names.packageName}.generated.resources"
    }
}

/** Skiko runtime for the machine running the desktop tests (what `compose.desktop.currentOs` resolves to). */
internal fun Project.desktopRuntimeCoordinate(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val osPart = when {
        os.contains("mac") -> "macos"
        os.contains("win") -> "windows"
        else -> "linux"
    }
    val archPart = if (arch == "aarch64" || arch == "arm64") "arm64" else "x64"
    return "org.jetbrains.compose.desktop:desktop-jvm-$osPart-$archPart:${catalogVersion("compose-multiplatform")}"
}

/** `stapel.serialization`: the serialization compiler plugin + kotlinx-serialization-json as `api`. */
internal fun Project.applyStapelSerialization() {
    pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        extensions.getByType(KotlinMultiplatformExtension::class.java)
            .sourceSets.getByName("commonMain").dependencies { api(lib("kotlinx-serialization-json")) }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        dependencies.add("implementation", lib("kotlinx-serialization-json"))
    }
}

/** `stapel.abi`: KGP built-in ABI validation; dumps are committed (see docs/gates.md). */
@OptIn(ExperimentalAbiValidation::class)
internal fun Project.applyStapelAbi() {
    val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.abiValidation {
        filters {
            exclude {
                annotatedWith.addAll(INTERNAL_API_MARKERS)
                // Compose resources' generated `Res` accessors are internal; the compiler's synthetic
                // `$stableprop` members would otherwise appear in klib dumps.
                byNames.add("**.generated.resources.**")
                // Compose compiler singletons holding composable lambdas (`ComposableSingletons$<File>Kt`
                // with `getLambda$<hash>$<module>` members): not API, and the hash moves whenever a
                // callee's signature in another module changes, which turned dependents' ABI checks red
                // with no source change of their own. Klib dumps do not list them; the filter is shared.
                byNames.add("**.ComposableSingletons**")
            }
        }
    }
}
