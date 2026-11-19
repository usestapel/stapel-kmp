// stapel-kmp settings.
//
// Modules are DISCOVERED, never listed: a directory is a project iff it holds a
// `build.gradle.kts`. A brigade adds a module by creating its directory and
// never edits this file. Naming rule (docs/adding-a-module.md):
//
//   substrate/stapel-<x>/               -> :stapel-<x>
//   capabilities/stapel-<x>/            -> :stapel-<x>
//   bricks/<brick>/domain/              -> :stapel-<brick>
//   bricks/<brick>/ui/                  -> :stapel-<brick>-ui
//   bricks/<brick>/demo/                -> :stapel-<brick>-ui-demo   (never published)
//   bricks/<brick>/e2e/                 -> :stapel-<brick>-e2e       (never published)
//   bricks/<brick>/adapters/<x>/        -> :stapel-<brick>-<x>
//   bom/                                -> :stapel-bom
//   tools/<a>/<b>/, showcase/<a>/<b>/   -> :tools:<a>:<b>, :showcase:<a>:<b>
//
// Repositories live only here; a project that declares one fails the build.

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("androidx\\..*")
                includeGroupByRegex("com\\.android(\\..*)?")
                includeGroupByRegex("com\\.google\\..*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

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

rootProject.name = "stapel-kmp"

fun isProjectDir(dir: File): Boolean = dir.isDirectory && File(dir, "build.gradle.kts").isFile

fun includeAt(path: String, dir: File) {
    include(path)
    project(path).projectDir = dir
}

val skipNames = setOf("build", ".gradle", ".kotlin", "src", "schemas", "api")

/** Every module directory below [root], mapped to a nested project path `:<prefix>:<a>:<b>`. */
fun includeTree(root: File, prefix: String) {
    if (!root.isDirectory) return
    root.walkTopDown()
        .onEnter { it == root || it.name !in skipNames && !it.name.startsWith(".") }
        .filter { it != root && isProjectDir(it) }
        .forEach { dir ->
            val rel = dir.relativeTo(root).invariantSeparatorsPath.replace('/', ':')
            includeAt(":$prefix:$rel", dir)
        }
}

// substrate/ and capabilities/: one level, directory name == artifact id.
listOf("substrate", "capabilities").forEach { family ->
    file(family).listFiles().orEmpty().sortedBy { it.name }.filter(::isProjectDir).forEach { dir ->
        require(dir.name.startsWith("stapel-")) {
            "Module directory $family/${dir.name} must be named stapel-<name> (it is the Maven artifact id)."
        }
        includeAt(":${dir.name}", dir)
    }
}

// bricks/<brick>/{domain,ui,demo,e2e,adapters/<x>}
file("bricks").listFiles().orEmpty().sortedBy { it.name }.filter { it.isDirectory }.forEach { brick ->
    val key = brick.name
    require(Regex("[a-z][a-z0-9-]*").matches(key)) { "Brick directory bricks/$key: use lower-case kebab-case." }
    mapOf(
        "domain" to "stapel-$key",
        "ui" to "stapel-$key-ui",
        "demo" to "stapel-$key-ui-demo",
        "e2e" to "stapel-$key-e2e",
    ).forEach { (sub, name) ->
        val dir = File(brick, sub)
        if (isProjectDir(dir)) includeAt(":$name", dir)
    }
    File(brick, "adapters").listFiles().orEmpty().sortedBy { it.name }.filter(::isProjectDir).forEach { dir ->
        includeAt(":stapel-$key-${dir.name}", dir)
    }
}

if (isProjectDir(file("bom"))) includeAt(":stapel-bom", file("bom"))

includeTree(file("tools"), "tools")
includeTree(file("showcase"), "showcase")
