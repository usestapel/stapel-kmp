package stapel.tools.codegen.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import stapel.tools.codegen.Generator
import stapel.tools.codegen.parseJsonObject
import java.nio.file.Files
import java.nio.file.Path
import javax.inject.Inject

/**
 * One project-owned backend module whose client is generated.
 *
 * @property contractDir the module's contract directory (`schema.json`, plus `errors.json`,
 *   `cache.json`, `overlay.json` when present) — the same files the backend's `make contract` emits.
 * @property packageName root package of the generated client (models under `.model`, errors under `.error`).
 * @property outputDir where the generated tree goes; default `src/commonMain/generated/api/<module>`
 *   (committed; its `kotlin/` directory is added to `commonMain`).
 */
abstract class StapelCodegenModule @Inject constructor(private val moduleName: String) : Named {
    @Input
    override fun getName(): String = moduleName

    /** The module's contract directory. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val contractDir: DirectoryProperty

    /** Root package of the generated client. */
    @get:Input
    abstract val packageName: Property<String>

    /** The generated tree (committed). */
    @get:Internal
    abstract val outputDir: DirectoryProperty
}

/** `stapelCodegen { module("orders") { … } }`. */
abstract class StapelCodegenExtension @Inject constructor(private val project: Project) {
    /** Every configured module. */
    abstract val modules: NamedDomainObjectContainer<StapelCodegenModule>

    /** Configures (or adds) module [name]. */
    fun module(name: String, configure: StapelCodegenModule.() -> Unit) {
        modules.maybeCreate(name).apply {
            outputDir.convention(project.layout.projectDirectory.dir("src/commonMain/generated/api/$name"))
            configure()
        }
    }
}

/**
 * Generates typed Kotlin clients for an app's own backend modules from their emitted contracts, with
 * the same generator (and the same runtime, `stapel-wire`) the Stapel bricks use.
 *
 * - `stapelGenerate`: regenerates every module into its committed tree;
 * - `stapelGenerateCheck` (part of `check`): regenerates in memory and fails on any difference — the
 *   drift gate a generated app wires into CI and pre-commit.
 *
 * The generated client depends only on `stapel-wire` (add it, or `stapel-network` which carries it,
 * to `commonMain`). Ratchets and pins are a library concern and are not applied to an app's modules.
 */
class StapelCodegenPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext = project.extensions.create("stapelCodegen", StapelCodegenExtension::class.java, project)
        project.tasks.register("stapelGenerate", StapelCodegenTask::class.java) { t ->
            t.group = "stapel generate"
            t.description = "Generates the clients of the app's own backend modules (stapelCodegen { })."
            t.modules.addAll(project.provider { ext.modules.toList() })
            t.check.set(false)
        }
        val verify = project.tasks.register("stapelGenerateCheck", StapelCodegenTask::class.java) { t ->
            t.group = "verification"
            t.description = "Fails when a committed generated client differs from what its contract generates."
            t.modules.addAll(project.provider { ext.modules.toList() })
            t.check.set(true)
        }
        project.pluginManager.withPlugin("lifecycle-base") {
            project.tasks.named("check").configure { it.dependsOn(verify) }
        }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val kotlin = project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
            ext.modules.all { module ->
                kotlin.sourceSets.getByName("commonMain").kotlin.srcDir(module.outputDir.dir("kotlin"))
            }
        }
    }
}

/** Generates (or checks) every configured module. */
@UntrackedTask(because = "writes into the committed source tree; the check compares in memory")
abstract class StapelCodegenTask : DefaultTask() {
    /** The modules. */
    @get:Nested
    abstract val modules: org.gradle.api.provider.ListProperty<StapelCodegenModule>

    /** True = drift check, nothing written. */
    @get:Input
    abstract val check: Property<Boolean>

    @TaskAction
    fun run() {
        val problems = mutableListOf<String>()
        for (m in modules.get()) {
            val out = m.outputDir.get().asFile.toPath()
            val existing = Generator.existingFiles(out)
            val previousLock = existing[Generator.LOCK]?.let(::parseJsonObject)
            val generation = try {
                Generator(m.name, m.contractDir.get().asFile.toPath(), apiPackage = m.packageName.get()).run(previousLock)
            } catch (e: stapel.tools.codegen.GenerationFailed) {
                throw GradleException("stapelCodegen '${m.name}': ${e.message}", e)
            }
            val files = generation.files.filterKeys { !it.startsWith(Generator.TEST_PREFIX) && it != Generator.REPORT }
            val current = existing.filterKeys { it != Generator.REPORT }
            if (check.get()) {
                val diff = Generator.diff(current, files)
                if (diff.isNotEmpty()) problems += "${m.name}: " + diff.joinToString("; ")
            } else {
                write(out, current.keys, files)
                logger.lifecycle("stapelCodegen: ${m.name} -> ${files.size} files (${generation.model.operations.size} operations)")
            }
        }
        if (problems.isNotEmpty()) {
            throw GradleException(
                "stapelGenerateCheck: committed clients differ from their contracts:\n" +
                    problems.joinToString("\n") { "  - $it" } + "\nRun ./gradlew stapelGenerate and commit the result.",
            )
        }
    }

    private fun write(target: Path, existing: Set<String>, files: Map<String, String>) {
        for (stale in existing - files.keys) Files.deleteIfExists(target.resolve(stale))
        for ((path, text) in files) {
            val file = target.resolve(path)
            Files.createDirectories(file.parent)
            if (!Files.exists(file) || Files.readString(file) != text) Files.writeString(file, text)
        }
    }
}
