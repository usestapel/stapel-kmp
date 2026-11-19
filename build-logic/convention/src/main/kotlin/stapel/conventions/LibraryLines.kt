package stapel.conventions

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * JetBrains redirect artifacts: since the androidx libraries publish their own iOS and desktop
 * variants, these JetBrains groups ship declaration-free klibs/jars that only depend on the androidx
 * module of the same name. Their common metadata klibs carry the same `unique_name` as the androidx
 * ones (`lifecycle-common_commonMain`, `savedstate_commonMain`, …), so any intermediate source set
 * (`iosMain`, `skikoMain`) fails with "KLIB loader: the same unique_name" under -Werror.
 *
 * Every configuration substitutes the redirect by its androidx target at the catalog version
 * (key = JetBrains group, value = androidx group to catalog version alias). The versions are the
 * ones Compose Multiplatform 1.12.2 and Navigation 3 1.2.0 require in their published `.module`
 * metadata (see docs/gates.md, "One library line").
 */
val REDIRECT_SHIMS: Map<String, Pair<String, String>> = mapOf(
    "org.jetbrains.androidx.lifecycle" to ("androidx.lifecycle" to "androidx-lifecycle"),
    "org.jetbrains.androidx.savedstate" to ("androidx.savedstate" to "androidx-savedstate"),
    "org.jetbrains.androidx.navigationevent" to ("androidx.navigationevent" to "androidx-navigationevent"),
    "org.jetbrains.androidx.navigation" to ("androidx.navigation" to "androidx-navigation"),
    "org.jetbrains.compose.annotation-internal" to ("androidx.annotation" to "androidx-annotation"),
    "org.jetbrains.compose.collection-internal" to ("androidx.collection" to "androidx-collection"),
)

/**
 * The androidx twin of a JetBrains coordinate group, or null when the group has none:
 * `org.jetbrains.androidx.<x>` → `androidx.<x>`, `org.jetbrains.compose.<x>-internal` → `androidx.<x>`.
 */
fun androidxTwinGroup(group: String): String? = when {
    group.startsWith("org.jetbrains.androidx.") -> "androidx." + group.removePrefix("org.jetbrains.androidx.")
    group.startsWith("org.jetbrains.compose.") && group.endsWith("-internal") ->
        "androidx." + group.removePrefix("org.jetbrains.compose.").removeSuffix("-internal")
    else -> null
}

/**
 * Pairs of one classpath where a JetBrains module and its androidx twin (same module name) both
 * resolve. Input: `group:module:version` coordinates and the `group:module` edges between them.
 *
 * On a klib or metadata classpath ([klib] true) every twin is a violation: the two common metadata
 * klibs share a `unique_name`. On a JVM/Android classpath a twin is allowed only as a redirect (the
 * JetBrains module or its platform variant depends on the twin group, e.g. Navigation 3's Android
 * variant forwards to androidx); any other twin means two copies of one library line.
 */
fun twinViolations(coordinates: Collection<String>, edges: Set<Pair<String, String>>, klib: Boolean): List<String> {
    val byGroupModule = coordinates.associateBy { it.substringBeforeLast(':') }
    return coordinates.mapNotNull { c ->
        val (group, module) = c.split(':')
        val twin = androidxTwinGroup(group) ?: return@mapNotNull null
        val other = byGroupModule["$twin:$module"] ?: return@mapNotNull null
        // The JetBrains module (or its platform variant `<module>-<platform>`) depends on the twin group.
        val redirect = edges.any { (from, to) ->
            to.startsWith("$twin:") &&
                (from == "$group:$module" || from.startsWith("$group:$module-"))
        }
        if (!klib && redirect) null else "$c next to $other"
    }.sorted()
}

/** Substitutes every redirect artifact by its androidx target, in every configuration of the project. */
internal fun Project.applyLibraryLines() {
    val targets = REDIRECT_SHIMS.mapValues { (_, v) -> v.first to catalogVersion(v.second) }
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution.all {
            val req = requested as? ModuleComponentSelector ?: return@all
            val (group, version) = targets[req.group] ?: return@all
            useTarget("$group:${req.module}:$version", "JetBrains redirect replaced by its androidx target (one library line)")
        }
    }
}

/**
 * `checkLibraryLines`: no classpath of the module (compile and runtime of every target, and the
 * metadata classpaths of the shared source sets) resolves a JetBrains module next to its androidx
 * twin.
 */
abstract class CheckLibraryLines : DefaultTask() {
    /** Project path, for messages. */
    @get:Input abstract val projectPath: Property<String>

    /** Resolved roots by configuration name. */
    @get:Input abstract val resolved: MapProperty<String, ResolvedComponentResult>

    /** Stamp. */
    @get:OutputFile abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        val errors = resolved.get().flatMap { (conf, root) ->
            val (coordinates, edges) = collect(root)
            twinViolations(coordinates, edges, klib = isKlibClasspath(conf)).map { "  $conf: $it" }
        }
        if (errors.isNotEmpty()) {
            throw GradleException(
                "checkLibraryLines (${projectPath.get()}): a JetBrains module and its androidx twin resolve on one " +
                    "classpath (duplicate klibs: 'KLIB loader: the same unique_name' in iosMain/skikoMain). " +
                    "Add the group to REDIRECT_SHIMS in build-logic with the androidx version the JetBrains " +
                    "artifact requires, or align the line in gradle/libs.versions.toml:\n" + errors.joinToString("\n"),
            )
        }
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }

    private fun collect(root: ResolvedComponentResult): Pair<Set<String>, Set<Pair<String, String>>> {
        val out = mutableSetOf<String>()
        val edges = mutableSetOf<Pair<String, String>>()
        val seen = HashSet<Any>()
        val queue = ArrayDeque(listOf(root))
        fun ga(c: ResolvedComponentResult) = (c.id as? ModuleComponentIdentifier)?.let { "${it.group}:${it.module}" }
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (!seen.add(c.id)) continue
            (c.id as? ModuleComponentIdentifier)?.let { out += "${it.group}:${it.module}:${it.version}" }
            c.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach { d ->
                val from = ga(c)
                val to = ga(d.selected)
                if (from != null && to != null) edges += from to to
                queue.add(d.selected)
            }
        }
        return out to edges
    }
}

/** True for a classpath of klibs or common metadata (as opposed to JVM/Android jars). */
fun isKlibClasspath(configuration: String): Boolean =
    configuration.startsWith("metadata") || configuration.startsWith("ios") ||
        configuration.endsWith("Klibraries") || configuration.endsWith("DependenciesMetadata")

private val LINE_CHECKED = Regex(
    """(.*(CompileClasspath|RuntimeClasspath|CompileKlibraries)|allSourceSetsCompileDependenciesMetadata)""",
)

/** Registers `checkLibraryLines` over every main compile/runtime/metadata classpath of a KMP module. */
internal fun Project.registerCheckLibraryLines() {
    val task = tasks.register("checkLibraryLines", CheckLibraryLines::class.java) {
        group = STAPEL_GATES
        description = "No JetBrains module next to its androidx twin on one classpath (duplicate klibs)."
        projectPath.set(path)
        stamp.set(layout.buildDirectory.file("stapel-gates/checkLibraryLines"))
    }
    configurations.matching {
        it.isCanBeResolved && LINE_CHECKED.matches(it.name) && !it.name.contains("test", ignoreCase = true) &&
            !it.name.contains("dev", ignoreCase = true)
    }.all {
        val conf = this
        task.configure { resolved.put(conf.name, conf.incoming.resolutionResult.rootComponent) }
    }
    pluginManager.withPlugin("lifecycle-base") { tasks.named("check").configure { dependsOn(task) } }
}
