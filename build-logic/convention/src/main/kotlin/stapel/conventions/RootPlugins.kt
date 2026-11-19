package stapel.conventions

import com.vanniktech.maven.publish.JavaPlatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.time.LocalDate

/**
 * `checkCatalog`: exact versions only; no pre-release unless allow-listed; depcheck exceptions not expired.
 */
abstract class CheckCatalog : DefaultTask() {
    /** gradle/libs.versions.toml. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalog: ConfigurableFileCollection

    /** gradle/prerelease-allowlist.txt. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val allowList: ConfigurableFileCollection

    /** gradle/depcheck-exceptions.toml. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val exceptions: ConfigurableFileCollection

    /** Today's date (an input so that an expiring exception re-runs the check). */
    @get:Input
    abstract val today: Property<String>

    /** Stamp. */
    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        val errors = mutableListOf<String>()
        val allowed = allowList.files.flatMap { it.readLines() }
            .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }
        val text = catalog.singleFile.readText()
        val versions = mutableMapOf<String, String>()
        var section = ""
        text.lines().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.startsWith("[")) section = line
            else if (section == "[versions]" && "=" in line) {
                val key = line.substringBefore('=').trim()
                val value = line.substringAfter('=').trim().trim('"')
                versions[key] = value
            }
        }
        for ((key, v) in versions) {
            when {
                Regex("""[\[\]()+,]""").containsMatchIn(v) || v.contains("latest", ignoreCase = true) ->
                    errors += "version '$key' = '$v' is not an exact version"
                isPreRelease(v) && allowed.none { it.endsWith(":$v") } ->
                    errors += "version '$key' = '$v' is a pre-release and not on gradle/prerelease-allowlist.txt"
            }
        }
        // Inline versions (`version = "..."`) bypass [versions]; same rules.
        Regex("""version\s*=\s*"([^"]+)"""").findAll(text).forEach { m ->
            val v = m.groupValues[1]
            if (isPreRelease(v) && allowed.none { it.endsWith(":$v") }) errors += "inline version '$v' is a pre-release"
        }
        val now = LocalDate.parse(today.get())
        exceptions.files.flatMap { it.readLines() }.forEach { line ->
            Regex("""until\s*=\s*"(\d{4}-\d{2}-\d{2})"""").find(line)?.let {
                if (LocalDate.parse(it.groupValues[1]).isBefore(now)) {
                    errors += "gradle/depcheck-exceptions.toml has an expired entry: ${line.trim()}"
                }
            }
        }
        if (errors.isNotEmpty()) throw GradleException("checkCatalog failed:\n" + errors.joinToString("\n") { "  - $it" })
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}

/** `stapel.root`: root-only gates and lifecycle tasks. */
class RootPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        require(this == rootProject) { "stapel.root is applied to the root project only" }
        pluginManager.apply("base")
        registerLifecycleTasks()
        val catalogCheck = tasks.register("checkCatalog", CheckCatalog::class.java) {
            group = STAPEL_GATES
            description = "Exact versions only; pre-release only through gradle/prerelease-allowlist.txt."
            catalog.from(file("gradle/libs.versions.toml"))
            allowList.from(file("gradle/prerelease-allowlist.txt"))
            exceptions.from(file("gradle/depcheck-exceptions.toml"))
            today.set(LocalDate.now().toString())
            stamp.set(layout.buildDirectory.file("stapel-gates/checkCatalog"))
        }
        tasks.named("check").configure { dependsOn(catalogCheck) }
        tasks.named("stapelCheckFast").configure { dependsOn(catalogCheck) }
        tasks.register("stapelVersion") {
            group = "stapel release"
            description = "Prints the train version (VERSION_NAME)."
            val v = versionName(this@with)
            doLast { println(v) }
        }
        tasks.register("stapelGenerate") {
            group = "stapel generate"
            description = "Root slot: `./gradlew stapelGenerate` runs every module's task of this name."
        }
        tasks.register("stapelGenerateCheck") {
            group = "stapel generate"
            description = "Root slot: `./gradlew stapelGenerateCheck` runs every module's drift check."
        }
        Unit
    }
}

/** `stapel.bom`: `stapel-bom`, a java-platform with a constraint on every published module. */
class BomPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("java-platform")
        val ext = applyStapelBase(StapelFamily.BOM)
        ext.description.convention("Bill of materials: one version for every stapel-kmp artifact.")
        applyStapelPublish(ext)
        extensions.configure(MavenPublishBaseExtension::class.java) { configure(JavaPlatform()) }
        gradle.projectsEvaluated {
            rootProject.subprojects
                .filter { p -> p != this@with && p.extensions.extraProperties.has(FAMILY_PROPERTY) }
                .filter { p -> StapelFamily.valueOf(p.extensions.extraProperties.get(FAMILY_PROPERTY).toString()).published }
                .sortedBy { it.name }
                .forEach { p -> dependencies.constraints.add("api", dependencies.project(mapOf("path" to p.path))) }
        }
        Unit
    }
}
