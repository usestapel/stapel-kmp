package stapel.conventions

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

/**
 * Names derived from a module's directory: nothing is typed twice.
 *
 * @property artifactId Maven artifact id and Gradle project name.
 * @property packageName Kotlin package every source file must live in (or below);
 *   also the Android namespace.
 * @property brick brick key for `bricks/<brick>/…`, null otherwise.
 */
data class StapelNames(val artifactId: String, val packageName: String, val brick: String?)

internal fun packageRoot(project: Project): String =
    project.providers.gradleProperty("stapel.packageRoot").orElse("stapel").get()

internal fun mavenGroup(project: Project): String =
    project.providers.gradleProperty("stapel.group").orElse("io.github.usestapel").get()

internal fun versionName(project: Project): String =
    project.providers.gradleProperty("VERSION_NAME").orElse("0.0.0").get()

private fun dotted(s: String) = s.replace('-', '.')

/** Derives [StapelNames] from the module's path relative to the root project. */
fun deriveNames(rootDir: File, projectDir: File, projectName: String, root: String): StapelNames {
    val rel = projectDir.relativeTo(rootDir).invariantSeparatorsPath.split('/')
    fun fail(): Nothing = throw GradleException(
        "Module directory '${rel.joinToString("/")}' is outside the layout of docs/adding-a-module.md",
    )
    return when (rel.first()) {
        "substrate", "capabilities" -> {
            if (rel.size != 2 || !rel[1].startsWith("stapel-")) fail()
            StapelNames(rel[1], "$root.${dotted(rel[1].removePrefix("stapel-"))}", null)
        }
        "bricks" -> {
            if (rel.size < 3) fail()
            val brick = rel[1]
            val b = dotted(brick)
            when {
                rel.size == 3 && rel[2] == "domain" -> StapelNames("stapel-$brick", "$root.$b", brick)
                rel.size == 3 && rel[2] == "ui" -> StapelNames("stapel-$brick-ui", "$root.$b.ui", brick)
                rel.size == 3 && rel[2] == "demo" -> StapelNames("stapel-$brick-ui-demo", "$root.$b.ui.demo", brick)
                rel.size == 3 && rel[2] == "e2e" -> StapelNames("stapel-$brick-e2e", "$root.$b.e2e", brick)
                rel.size == 3 && rel[2] == "testing" -> StapelNames("stapel-$brick-testing", "$root.$b.testing", brick)
                rel.size == 4 && rel[2] == "adapters" ->
                    StapelNames("stapel-$brick-${rel[3]}", "$root.$b.${dotted(rel[3])}", brick)
                else -> fail()
            }
        }
        "bom" -> StapelNames("stapel-bom", "$root.bom", null)
        "catalog" -> StapelNames("stapel-catalog", "$root.catalog", null)
        "tools" -> StapelNames(projectName, "$root.tools", null)
        "showcase" -> StapelNames(projectName, "$root.showcase", null)
        else -> fail()
    }
}

internal fun Project.stapelNames(): StapelNames =
    deriveNames(rootDir, projectDir, name, packageRoot(this))
