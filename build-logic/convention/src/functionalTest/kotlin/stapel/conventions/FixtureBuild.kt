package stapel.conventions

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files

/**
 * A throw-away Gradle build laid out like stapel-kmp (same settings rules, the real version catalog
 * and gate data files), with the convention plugins under test injected by TestKit.
 */
class FixtureBuild(private val name: String) {
    private val repoRoot = File(System.getProperty("stapel.repoRoot"))
    private val testKitHome = File(System.getProperty("stapel.testkitHome"))
    val dir: File = Files.createTempDirectory("stapel-fixture-$name").toFile()
    private val modules = mutableListOf<Pair<String, String>>()

    init {
        File(dir, "gradle").mkdirs()
        listOf(
            "libs.versions.toml", "substrate-ranks.properties", "material-allowed.txt",
            "prerelease-allowlist.txt", "wasm-ready.txt", "depcheck-exceptions.toml",
        ).forEach { File(repoRoot, "gradle/$it").copyTo(File(dir, "gradle/$it"), overwrite = true) }
        File(dir, "gradle.properties").writeText(
            """
            VERSION_NAME=0.0.1
            stapel.group=io.github.usestapel
            stapel.packageRoot=stapel
            org.gradle.jvmargs=-Xmx3g -XX:+UseParallelGC
            kotlin.native.ignoreDisabledTargets=true
            android.useAndroidX=true
            """.trimIndent(),
        )
        File(dir, "build.gradle.kts").writeText("plugins { id(\"stapel.root\") }\n")
    }

    /** Adds a module at [path] (e.g. `substrate/stapel-result`) with the given build script. */
    fun module(path: String, buildScript: String): FixtureBuild {
        val m = File(dir, path).apply { mkdirs() }
        File(m, "build.gradle.kts").writeText(buildScript.trimIndent() + "\n")
        File(m, "README.md").writeText("# fixture\n")
        File(m, "MODULE.md").writeText("# fixture\n")
        modules += path to projectName(path)
        return this
    }

    /** Writes a source file below the module directory. */
    fun file(path: String, content: String): FixtureBuild {
        File(dir, path).apply { parentFile.mkdirs() }.writeText(content.trimIndent() + "\n")
        return this
    }

    private fun projectName(path: String): String {
        val p = path.split('/')
        return when (p[0]) {
            "substrate", "capabilities" -> p[1]
            "bom" -> "stapel-bom"
            "catalog" -> "stapel-catalog"
            "tools", "showcase" -> ":" + p.joinToString(":")
            "bricks" -> when (p[2]) {
                "domain" -> "stapel-${p[1]}"
                "e2e" -> "stapel-${p[1]}-e2e"
                "ui" -> "stapel-${p[1]}-ui"
                "demo" -> "stapel-${p[1]}-ui-demo"
                "testing" -> "stapel-${p[1]}-testing"
                "adapters" -> "stapel-${p[1]}-${p[3]}"
                else -> error(path)
            }
            else -> error(path)
        }
    }

    private fun writeSettings() {
        val includes = modules.joinToString("\n") { (path, name) ->
            val gradlePath = if (name.startsWith(":")) name else ":$name"
            "include(\"$gradlePath\")\nproject(\"$gradlePath\").projectDir = file(\"$path\")"
        }
        File(dir, "settings.gradle.kts").writeText(
            """
            dependencyResolutionManagement {
                repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
                repositories {
                    google {
                        content {
                            includeGroupByRegex("androidx\\..*")
                            includeGroupByRegex("com\\.android(\\..*)?")
                            includeGroupByRegex("com\\.google\\..*")
                        }
                    }
                    mavenCentral()
                }
            }
            enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
            rootProject.name = "fixture-$name"
            $includes
            """.trimIndent() + "\n",
        )
    }

    private fun runner(vararg args: String): GradleRunner {
        writeSettings()
        return GradleRunner.create()
            .withProjectDir(dir)
            .withTestKitDir(testKitHome)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace", "--console=plain")
            .forwardOutput()
    }

    /** Runs and expects success. */
    fun succeeds(vararg args: String): BuildResult = runner(*args).build()

    /** Runs and expects failure. */
    fun fails(vararg args: String): BuildResult = runner(*args).buildAndFail()
}

/** Build script of a pure substrate module, optionally with commonMain dependencies. */
fun substrate(deps: String = "", plugin: String = "stapel.substrate"): String = """
    plugins { id("$plugin") }
    stapel { description = "fixture" }
    kotlin { sourceSets { commonMain.dependencies { $deps } } }
"""
