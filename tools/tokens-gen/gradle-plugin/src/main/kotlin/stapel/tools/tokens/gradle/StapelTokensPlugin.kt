package stapel.tools.tokens.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import stapel.tools.tokens.ThemeException
import stapel.tools.tokens.ThemeProblem
import stapel.tools.tokens.ThemeValidationException
import stapel.tools.tokens.TokensGenerator
import stapel.tools.tokens.Visibility
import java.io.File

/**
 * `stapelTokens { }`: the app's theme file and the Kotlin object it becomes.
 *
 * @property theme the host theme (`stapel.theme.json`, the same file the web app uses); default
 *   `stapel.theme.json` next to the build file; an absent file means the library default theme.
 * @property packageName package of the generated object (default `app.theme`).
 * @property objectName name of the generated `StapelTokens` value (default `AppTokens`).
 * @property public whether the value is public (default false: internal to the module).
 */
abstract class StapelTokensExtension {
    /** The host theme. */
    abstract val theme: RegularFileProperty

    /** Package of the generated value. */
    abstract val packageName: Property<String>

    /** Name of the generated value. */
    abstract val objectName: Property<String>

    /** Public instead of internal. */
    abstract val public: Property<Boolean>
}

/**
 * Turns an app's `stapel.theme.json` into a `StapelTokens` value at build time, with the Kotlin port of
 * the §68 resolver (no Node): `generateStapelTokens` writes `build/generated/stapelTokens` (added to
 * `commonMain`), `checkStapelTokens` (part of `check`) validates the theme — a contrast or reference
 * error fails the build exactly as the web's bin does — and proves the generated source is current.
 */
class StapelTokensPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val ext = project.extensions.create("stapelTokens", StapelTokensExtension::class.java)
        ext.theme.convention(project.layout.projectDirectory.file("stapel.theme.json"))
        ext.packageName.convention("app.theme")
        ext.objectName.convention("AppTokens")
        ext.public.convention(false)
        val out = project.layout.buildDirectory.dir("generated/stapelTokens")
        val generate = project.tasks.register("generateStapelTokens", GenerateStapelTokens::class.java) { t ->
            t.group = "stapel generate"
            t.description = "Generates the app's StapelTokens value from stapel.theme.json."
            t.theme.set(ext.theme.map { f -> if (f.asFile.isFile) f else null as org.gradle.api.file.RegularFile? })
            t.packageName.set(ext.packageName)
            t.objectName.set(ext.objectName)
            t.publicValue.set(ext.public)
            t.outputDir.set(out)
        }
        val check = project.tasks.register("checkStapelTokens", CheckStapelTokens::class.java) { t ->
            t.group = "verification"
            t.description = "Validates stapel.theme.json (§68 contrast and references) and that the generated tokens are current."
            t.theme.set(ext.theme.map { f -> if (f.asFile.isFile) f else null as org.gradle.api.file.RegularFile? })
            t.packageName.set(ext.packageName)
            t.objectName.set(ext.objectName)
            t.publicValue.set(ext.public)
            t.generated.set(generate.flatMap { it.outputDir })
            t.stamp.set(project.layout.buildDirectory.file("stapel-gates/checkStapelTokens"))
        }
        project.pluginManager.withPlugin("lifecycle-base") { project.tasks.named("check").configure { it.dependsOn(check) } }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
                .sourceSets.getByName("commonMain").kotlin.srcDir(generate)
        }
    }
}

/** Shared inputs of the two tasks. */
abstract class TokensTaskBase : DefaultTask() {
    /** The host theme, absent = library default. */
    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.NONE) abstract val theme: RegularFileProperty

    /** Package. */
    @get:Input abstract val packageName: Property<String>

    /** Name. */
    @get:Input abstract val objectName: Property<String>

    /** Visibility. */
    @get:Input abstract val publicValue: Property<Boolean>

    /** Runs the app generation into [outDir], turning a theme error into a build failure that lists every problem. */
    protected fun generation(outDir: File): TokensGenerator.() -> stapel.tools.tokens.Generation = {
        try {
            app(
                theme.orNull?.asFile, null, outDir, packageName.get(), objectName.get(),
                if (publicValue.get()) Visibility.Public else Visibility.Internal, null,
            )
        } catch (e: ThemeValidationException) {
            throw GradleException(
                "stapel.theme.json is not a valid theme (§68):\n" +
                    e.problems.joinToString("\n") { "  $it" } +
                    "\n${e.problems.count { it.severity == ThemeProblem.Severity.Error }} error(s).",
            )
        } catch (e: ThemeException) {
            throw GradleException("stapel.theme.json: ${e.problem}")
        }
    }
}

/** `generateStapelTokens`. */
@CacheableTask
abstract class GenerateStapelTokens : TokensTaskBase() {
    /** Output source root. */
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun run() {
        val dir = outputDir.get().asFile
        dir.deleteRecursively()
        val g = generation(dir)(TokensGenerator)
        g.warnings.forEach { logger.warn(it.toString()) }
        TokensGenerator.apply(g, check = false)
    }
}

/** `checkStapelTokens`. */
abstract class CheckStapelTokens : TokensTaskBase() {
    /** The generated source root to compare. */
    @get:org.gradle.api.tasks.Internal abstract val generated: DirectoryProperty

    /** Stamp. */
    @get:OutputFile abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        val g = generation(generated.get().asFile)(TokensGenerator)
        val stale = TokensGenerator.apply(g, check = true)
        if (stale.isNotEmpty()) {
            throw GradleException("checkStapelTokens: generated tokens are stale: $stale. Run ./gradlew generateStapelTokens.")
        }
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}
