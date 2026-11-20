package stapel.conventions

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.RegularFileProperty
import java.io.File

private val PACKAGE_LINE = Regex("""^\s*package\s+([A-Za-z_][\w.]*)""", RegexOption.MULTILINE)

/** Base for the cheap source gates: they write a stamp so Gradle can keep them up to date. */
abstract class StapelSourceGate : DefaultTask() {
    /** Stamp file written on success. */
    @get:OutputFile
    abstract val stamp: RegularFileProperty

    /** Project path, for messages. */
    @get:Input
    abstract val projectPath: Property<String>

    protected fun pass(note: String) {
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText(note + "\n")
    }
}

/** `checkPackageRoot`: every Kotlin file's package starts with the module's derived package. */
@CacheableTask
abstract class CheckPackageRoot : StapelSourceGate() {
    /** All Kotlin sources of the module (main, test, generated). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Required package prefix. */
    @get:Input
    abstract val packageName: Property<String>

    /** Module directory, for relative paths in messages. */
    @get:Internal
    abstract val moduleDir: DirectoryProperty

    @TaskAction
    fun run() {
        val pkg = packageName.get()
        val bad = sources.files.filter { it.extension == "kt" }.mapNotNull { f ->
            val declared = PACKAGE_LINE.find(f.readText())?.groupValues?.get(1) ?: "<default package>"
            if (declared == pkg || declared.startsWith("$pkg.")) null
            else "  ${f.relativeTo(moduleDir.get().asFile)}: package $declared"
        }
        if (bad.isNotEmpty()) {
            throw GradleException(
                "checkPackageRoot (${projectPath.get()}): every file must be in package '$pkg' or below " +
                    "(derived from the module directory, docs/adding-a-module.md):\n" + bad.joinToString("\n"),
            )
        }
        pass("ok")
    }
}

/** `checkSourceSets`: a UI module is commonMain-only unless it declares a platform subject (D2). */
@CacheableTask
abstract class CheckSourceSets : StapelSourceGate() {
    /** Names of platform main source-set directories present under src/. */
    @get:Input
    abstract val platformDirs: ListProperty<String>

    /** Declared platform subject, if any. */
    @get:Input
    @get:Optional
    abstract val platformSubject: Property<String>

    @TaskAction
    fun run() {
        val dirs = platformDirs.get()
        if (dirs.isNotEmpty() && platformSubject.orNull.isNullOrBlank()) {
            throw GradleException(
                "checkSourceSets (${projectPath.get()}): UI libraries are commonMain-only (D2), but found " +
                    dirs.joinToString { "src/$it" } + ". If a platform API is this module's subject, say so: " +
                    "stapel { platformSubject(\"<what>\") }",
            )
        }
        pass("ok")
    }
}

/** `checkModuleDocs`: every published module has README.md and MODULE.md. */
@CacheableTask
abstract class CheckModuleDocs : StapelSourceGate() {
    /** Files that must exist. */
    @get:Input
    abstract val required: ListProperty<String>

    /** Module directory. */
    @get:Internal
    abstract val moduleDir: DirectoryProperty

    @TaskAction
    fun run() {
        val missing = required.get().filterNot { File(moduleDir.get().asFile, it).isFile }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "checkModuleDocs (${projectPath.get()}): missing ${missing.joinToString()} " +
                    "(README.md = human entry, MODULE.md = seams and invariants; docs/adding-a-module.md).",
            )
        }
        pass("ok")
    }
}

/**
 * `checkKoinLeak` (ruling R6): `org.koin` may appear only in files whose package is `<module>.di`
 * (or below; for a module whose package already ends in `.di`, that package). Main source sets
 * only; tests may use koin-test anywhere.
 */
@CacheableTask
abstract class CheckKoinLeak : StapelSourceGate() {
    /** Main Kotlin sources. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** The module package; `<it>.di` is the only place Koin may be named. */
    @get:Input
    abstract val packageName: Property<String>

    /** Module directory, for messages. */
    @get:Internal
    abstract val moduleDir: DirectoryProperty

    @TaskAction
    fun run() {
        // A module whose own package is already a `di` package (stapel-di: the Koin conventions
        // themselves) may name Koin anywhere in it.
        val diPkg = packageName.get().let { if (it.endsWith(".di")) it else "$it.di" }
        val bad = sources.files.filter { it.extension == "kt" }.mapNotNull { f ->
            val text = f.readText()
            if (!text.contains("org.koin")) return@mapNotNull null
            val declared = PACKAGE_LINE.find(text)?.groupValues?.get(1) ?: ""
            if (declared == diPkg || declared.startsWith("$diPkg.")) null
            else "  ${f.relativeTo(moduleDir.get().asFile)} (package $declared)"
        }
        if (bad.isNotEmpty()) {
            throw GradleException(
                "checkKoinLeak (${projectPath.get()}): org.koin is allowed only in package '$diPkg' " +
                    "(one public <module>Module() factory; constructors stay plain, R6). Found in:\n" +
                    bad.joinToString("\n"),
            )
        }
        pass("ok")
    }
}

/** `checkWasmReady`: external commonMain dependencies of pure families are listed in gradle/wasm-ready.txt. */
@CacheableTask
abstract class CheckWasmReady : StapelSourceGate() {
    /** `group:name` of every external dependency declared on a commonMain configuration. */
    @get:Input
    abstract val declared: ListProperty<String>

    /** gradle/wasm-ready.txt. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val allowList: ConfigurableFileCollection

    @TaskAction
    fun run() {
        val allowed = allowList.files.flatMap { it.readLines() }
            .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
        val missing = declared.get().filter { it !in allowed && !it.startsWith("org.jetbrains.kotlin:") }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "checkWasmReady (${projectPath.get()}): commonMain of a domain/substrate library may only use " +
                    "dependencies that publish a wasmJs variant (D2). Not in gradle/wasm-ready.txt: " +
                    missing.joinToString() + ". Verify the variant and ask the build owner to add the line.",
            )
        }
        pass("ok")
    }
}

/** Registers the source-shape gates of `stapel.base` according to the family. */
internal fun Project.registerSourceChecks(family: StapelFamily, names: StapelNames, ext: StapelExtension) {
    val gateDir = layout.buildDirectory.dir("stapel-gates")
    val checks = mutableListOf<String>()
    val kotlinSources = fileTree("src") { include("**/*.kt") }

    if (family != StapelFamily.BOM) {
        tasks.register("checkPackageRoot", CheckPackageRoot::class.java) {
            group = STAPEL_GATES
            description = "Every Kotlin file lives in the module's derived package."
            projectPath.set(path)
            sources.from(kotlinSources)
            packageName.set(names.packageName)
            moduleDir.set(layout.projectDirectory)
            stamp.set(gateDir.map { it.file("checkPackageRoot") })
        }
        checks += "checkPackageRoot"
    }

    if (family.compose && family in LIBRARY_FAMILIES) {
        tasks.register("checkSourceSets", CheckSourceSets::class.java) {
            group = STAPEL_GATES
            description = "UI libraries are commonMain-only unless a platform API is their subject (D2)."
            projectPath.set(path)
            val src = file("src")
            platformDirs.set(
                provider {
                    src.listFiles().orEmpty().map { it.name }.filter {
                        it.endsWith("Main") && it != "commonMain" && it != "skikoMain" &&
                            File(src, it).walkTopDown().any { f -> f.isFile && f.extension == "kt" }
                    }.sorted()
                },
            )
            platformSubject.set(ext.platformSubjectReason)
            stamp.set(gateDir.map { it.file("checkSourceSets") })
        }
        checks += "checkSourceSets"
    }

    if (family.published && family != StapelFamily.BOM) {
        tasks.register("checkModuleDocs", CheckModuleDocs::class.java) {
            group = STAPEL_GATES
            description = "Published modules carry README.md and MODULE.md."
            projectPath.set(path)
            required.set(listOf("README.md", "MODULE.md"))
            moduleDir.set(layout.projectDirectory)
            stamp.set(gateDir.map { it.file("checkModuleDocs") })
        }
        checks += "checkModuleDocs"
    }

    if (family in LIBRARY_FAMILIES || family == StapelFamily.DEMO) {
        tasks.register("checkKoinLeak", CheckKoinLeak::class.java) {
            group = STAPEL_GATES
            description = "org.koin only inside the module's `di` package (R6)."
            projectPath.set(path)
            sources.from(fileTree("src") { include("*Main/**/*.kt") })
            packageName.set(names.packageName)
            moduleDir.set(layout.projectDirectory)
            stamp.set(gateDir.map { it.file("checkKoinLeak") })
        }
        checks += "checkKoinLeak"
    }

    if (family != StapelFamily.BOM) {
        val moduleName = name
        tasks.register("checkSourceRules", CheckSourceRules::class.java) {
            group = STAPEL_GATES
            description = "G-R1 no ambient time/dispatcher/random/locale outside stapel-runtime; G-R2 no swallowed " +
                "cancellation (libraries); G-T1 expression-bodied JUnit 5 tests declare Unit (tools, e2e)."
            projectPath.set(path)
            projectName.set(moduleName)
            sources.from(fileTree("src") { include("*Main/**/*.kt", "main/**/*.kt") })
            testSources.from(fileTree("src") { include("*Test/**/*.kt", "test/**/*.kt") })
            baselineFile.from(rootProject.file("gradle/source-rules-baseline.txt"))
            ambientRule.set(moduleName != "stapel-runtime")
            libraryRules.set(family in LIBRARY_FAMILIES)
            junit5Rules.set(family == StapelFamily.TOOL || family == StapelFamily.E2E)
            vendorRule.set(family in VENDOR_FREE_FAMILIES)
            buildFile.from(layout.projectDirectory.file("build.gradle.kts"))
            today.set(java.time.LocalDate.now().toString())
            moduleDir.set(layout.projectDirectory)
            stamp.set(gateDir.map { it.file("checkSourceRules") })
        }
        checks += "checkSourceRules"
    }

    if (family in PURE_FAMILIES && !name.endsWith("-room")) {
        tasks.register("checkWasmReady", CheckWasmReady::class.java) {
            group = STAPEL_GATES
            description = "commonMain dependencies of pure libraries publish a wasmJs variant (D2)."
            projectPath.set(path)
            declared.set(
                provider {
                    configurations.filter { it.name.startsWith("commonMain") && !it.name.contains("Test") }
                        .flatMap { c ->
                            c.dependencies.withType(org.gradle.api.artifacts.ExternalModuleDependency::class.java)
                                .map { "${it.group}:${it.name}" }
                        }.distinct().sorted()
                },
            )
            allowList.from(rootProject.file("gradle/wasm-ready.txt"))
            stamp.set(gateDir.map { it.file("checkWasmReady") })
        }
        checks += "checkWasmReady"
    }

    pluginManager.withPlugin("lifecycle-base") {
        tasks.named("check").configure { dependsOn(checks) }
    }
    tasks.named("stapelCheckFast").configure { dependsOn(checks) }
}
