package stapel.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** Shared body of the published Kotlin multiplatform library families. */
internal fun Project.applyLibrary(family: StapelFamily): StapelExtension {
    val ext = applyStapelBase(family)
    applyStapelKmp(ext)
    if (family.compose) applyStapelCompose()
    applyStapelAbi()
    applyStapelDokka(ext)
    applyStapelPublish(ext)
    applyStapelDepcheck(ext)
    return ext
}

/** `stapel.substrate`: pure Kotlin substrate artifact (`substrate/stapel-<x>`). */
class SubstratePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.applyLibrary(StapelFamily.SUBSTRATE)
    }
}

/** `stapel.substrate-ui`: Compose substrate artifact. */
class SubstrateUiPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.applyLibrary(StapelFamily.SUBSTRATE_UI)
    }
}

/** `stapel.capability`: optional backend-core capability (`capabilities/stapel-<x>`). */
class CapabilityPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.applyLibrary(StapelFamily.CAPABILITY)
    }
}

/** `stapel.capability-ui`: Compose half of a capability (`capabilities/stapel-<x>-ui`). */
class CapabilityUiPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.applyLibrary(StapelFamily.CAPABILITY_UI)
    }
}

/** Main class the generator CLI (`:tools:codegen:cli`) must expose; see docs/adding-a-module.md. */
const val CODEGEN_MAIN_CLASS = "stapel.tools.codegen.cli.MainKt"

/**
 * `stapel.brick-domain`: `bricks/<brick>/domain` → `stapel-<brick>`. Pure Kotlin + serialization, generated
 * sources root `src/commonMain/generated/kotlin`, and the generator hooks `stapelGenerate` /
 * `stapelGenerateCheck` (wired into `check` and `stapelCheckFast`) once `:tools:codegen:cli` exists.
 */
class BrickDomainPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val ext = applyLibrary(StapelFamily.BRICK_DOMAIN)
        applyStapelSerialization()
        val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)
        kmp.sourceSets.getByName("commonMain").kotlin.srcDir("src/commonMain/generated/kotlin")
        registerCodegenHooks(ext)
    }
}

internal fun Project.registerCodegenHooks(ext: StapelExtension) {
    val cli = rootProject.findProject(":tools:codegen:cli") ?: return
    val codegen = configurations.create("stapelCodegen") {
        isCanBeConsumed = false
        isCanBeResolved = true
    }
    dependencies.add(codegen.name, dependencies.project(mapOf("path" to cli.path)))
    val brick = ext.brick.get()
    val repoRoot = rootDir.absolutePath
    val common = { t: JavaExec, check: Boolean ->
        t.group = "stapel generate"
        t.classpath = codegen
        t.mainClass.set(CODEGEN_MAIN_CLASS)
        t.args(listOfNotNull("gen", if (check) "--check" else null, "--module", name, "--brick", brick, "--repo-root", repoRoot))
        t.inputs.dir(rootProject.layout.projectDirectory.dir("contracts/$brick")).optional()
            .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    }
    tasks.register("stapelGenerate", JavaExec::class.java) {
        description = "Regenerates src/commonMain/generated from the pinned contracts."
        common(this, false)
        outputs.dir("src/commonMain/generated")
    }
    val check = tasks.register("stapelGenerateCheck", JavaExec::class.java) {
        description = "Regenerates into a temp dir and fails on any difference (D4)."
        common(this, true)
        inputs.dir("src/commonMain/generated").optional()
        outputs.upToDateWhen { false }
    }
    pluginManager.withPlugin("lifecycle-base") { tasks.named("check").configure { dependsOn(check) } }
}

/** `stapel.brick-ui`: `bricks/<brick>/ui` → `stapel-<brick>-ui`; gets `api(stapel-<brick>)` automatically. */
class BrickUiPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val ext = applyLibrary(StapelFamily.BRICK_UI)
        val domain = rootProject.findProject(":stapel-${ext.brick.get()}")
        if (domain != null) {
            extensions.getByType(KotlinMultiplatformExtension::class.java)
                .sourceSets.getByName("commonMain").dependencies { api(project(domain.path)) }
        }
    }
}

/** `stapel.brick-adapter`: `bricks/<brick>/adapters/<x>` → `stapel-<brick>-<x>`, platform adapter of one brick seam. */
class BrickAdapterPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val ext = applyLibrary(StapelFamily.BRICK_ADAPTER)
        val domain = rootProject.findProject(":stapel-${ext.brick.get()}")
        if (domain != null) {
            extensions.getByType(KotlinMultiplatformExtension::class.java)
                .sourceSets.getByName("commonMain").dependencies { api(project(domain.path)) }
        }
    }
}

/** `stapel.brick-demo`: `bricks/<brick>/demo`, never published; gets the brick's UI (+ `stapel-showcase` once it exists). */
class BrickDemoPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val ext = applyStapelBase(StapelFamily.DEMO)
        applyStapelKmp(ext, explicitApi = false)
        applyStapelCompose()
        applyStapelDepcheck(ext)
        val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)
        listOfNotNull(
            rootProject.findProject(":stapel-${ext.brick.get()}-ui"),
            rootProject.findProject(":stapel-showcase"),
        ).forEach { p -> kmp.sourceSets.getByName("commonMain").dependencies { implementation(project(p.path)) } }
    }
}

/**
 * `stapel.brick-e2e`: `bricks/<brick>/e2e`, JVM end-to-end tests against a real backend. Not published;
 * its `test` task runs only when `-Pstapel.e2e.baseUrl=<url>` is given (so `check` stays hermetic).
 */
class BrickE2ePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val ext = applyStapelBase(StapelFamily.E2E)
        applyJvmToolBasics()
        applyStapelDepcheck(ext)
        val baseUrl = providers.gradleProperty("stapel.e2e.baseUrl")
        tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach {
            onlyIf("needs -Pstapel.e2e.baseUrl") { baseUrl.isPresent }
            baseUrl.orNull?.let { systemProperty("stapel.e2e.baseUrl", it) }
        }
    }
}

internal fun Project.applyJvmToolBasics() {
    pluginManager.apply("org.jetbrains.kotlin.jvm")
    val jvmTarget = catalogVersion("jvm-target")
    extensions.configure(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java) {
        compilerOptions {
            allWarningsAsErrors.set(true)
            this.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(jvmTarget))
            freeCompilerArgs.add("-Xjdk-release=$jvmTarget")
        }
    }
    extensions.configure(org.gradle.api.plugins.JavaPluginExtension::class.java) {
        val v = org.gradle.api.JavaVersion.toVersion(jvmTarget)
        sourceCompatibility = v
        targetCompatibility = v
    }
    dependencies.add("testImplementation", dependencies.platform(lib("junit-bom")))
    dependencies.add("testImplementation", lib("junit-jupiter"))
    dependencies.add("testImplementation", lib("kotlin-test-junit5"))
    dependencies.add("testRuntimeOnly", lib("junit-platform-launcher"))
    tasks.withType(org.gradle.api.tasks.testing.Test::class.java).configureEach { useJUnitPlatform() }
    tasks.named("stapelCheckFast").configure { dependsOn("test") }
}

/** `stapel.jvm-tool`: JVM tool under `tools/` (generators, CLIs). Kotlin/JVM 17 bytecode, JUnit 5. */
class JvmToolPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val ext = applyStapelBase(StapelFamily.TOOL)
        applyJvmToolBasics()
        applyStapelDepcheck(ext)
    }
}

/** `stapel.showcase`: KMP Compose library for `showcase/` (gallery, stories); never published. */
class ShowcasePlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        val ext = applyStapelBase(StapelFamily.SHOWCASE)
        applyStapelKmp(ext, explicitApi = false)
        applyStapelCompose()
        applyStapelDepcheck(ext)
    }
}

/** `stapel.serialization`: kotlinx.serialization plugin + json as `api`; applied next to a family plugin. */
class SerializationPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.applyStapelSerialization()
}

/** `stapel.room`: KSP + Room 3 for `*-room` artifacts; applied next to `stapel.substrate`. */
class RoomPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.applyStapelRoom()
}
