package stapel.conventions

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.util.Properties

/** One row of the layer table (docs/gates.md). */
data class LayerRule(val from: StapelFamily, val mayDependOn: Set<StapelFamily>)

/** Families each family may depend on through main (non-test) project edges; same-brick/rank refinements below. */
val LAYERS: List<LayerRule> = listOf(
    LayerRule(StapelFamily.SUBSTRATE, setOf(StapelFamily.SUBSTRATE)),
    LayerRule(StapelFamily.SUBSTRATE_UI, setOf(StapelFamily.SUBSTRATE, StapelFamily.SUBSTRATE_UI)),
    LayerRule(StapelFamily.CAPABILITY, setOf(StapelFamily.SUBSTRATE)),
    LayerRule(StapelFamily.CAPABILITY_UI, setOf(StapelFamily.SUBSTRATE, StapelFamily.SUBSTRATE_UI, StapelFamily.CAPABILITY)),
    LayerRule(StapelFamily.BRICK_DOMAIN, setOf(StapelFamily.SUBSTRATE)),
    LayerRule(StapelFamily.BRICK_UI, setOf(StapelFamily.SUBSTRATE, StapelFamily.SUBSTRATE_UI, StapelFamily.BRICK_DOMAIN)),
    LayerRule(StapelFamily.BRICK_ADAPTER, setOf(StapelFamily.SUBSTRATE, StapelFamily.BRICK_DOMAIN)),
    LayerRule(
        StapelFamily.DEMO,
        setOf(
            StapelFamily.SUBSTRATE, StapelFamily.SUBSTRATE_UI, StapelFamily.CAPABILITY, StapelFamily.CAPABILITY_UI,
            StapelFamily.BRICK_DOMAIN, StapelFamily.BRICK_UI, StapelFamily.BRICK_ADAPTER,
        ),
    ),
    LayerRule(
        StapelFamily.E2E,
        setOf(
            StapelFamily.SUBSTRATE, StapelFamily.SUBSTRATE_UI, StapelFamily.CAPABILITY, StapelFamily.CAPABILITY_UI,
            StapelFamily.BRICK_DOMAIN, StapelFamily.BRICK_UI, StapelFamily.BRICK_ADAPTER, StapelFamily.TOOL,
        ),
    ),
    LayerRule(StapelFamily.TOOL, setOf(StapelFamily.TOOL)),
    LayerRule(StapelFamily.SHOWCASE, StapelFamily.entries.toSet()),
    LayerRule(StapelFamily.BOM, StapelFamily.entries.toSet()),
)

/** Coordinates forbidden on a family's main classpaths, transitively. */
data class ForbiddenCoordinate(
    val families: Set<StapelFamily>,
    val groupRegex: Regex,
    val nameRegex: Regex,
    val why: String,
    val compileOnly: Boolean = false,
    val unless: (artifactId: String) -> Boolean = { false },
)

private val ANY = Regex(".*")

internal fun forbiddenCoordinates(materialAllowed: Set<String>): List<ForbiddenCoordinate> = listOf(
    ForbiddenCoordinate(
        PURE_FAMILIES,
        Regex("""(org\.jetbrains\.compose(\.(runtime|ui|foundation|animation|material.*|components|desktop))?|androidx\.compose\..*)"""),
        ANY,
        "D6: domain and pure substrate libraries have no Compose",
    ),
    ForbiddenCoordinate(PURE_FAMILIES, Regex("""io\.insert-koin"""), Regex("""koin-(compose|android).*"""), "D6/R6: koin-compose only in UI artifacts"),
    ForbiddenCoordinate(
        PURE_FAMILIES,
        Regex("""(org\.jetbrains\.)?androidx\.navigation3?"""),
        ANY,
        "D6: no navigation in domain/pure libraries",
    ),
    ForbiddenCoordinate(
        StapelFamily.entries.toSet() - StapelFamily.SHOWCASE,
        Regex("""androidx\.(room3?|sqlite)"""),
        ANY,
        "D6/D9: Room/SQLite only in `*-room` artifacts",
        unless = { it.endsWith("-room") },
    ),
    ForbiddenCoordinate(
        setOf(StapelFamily.BRICK_UI, StapelFamily.CAPABILITY_UI, StapelFamily.SUBSTRATE_UI),
        Regex("""(org\.jetbrains\.compose\.material3?|androidx\.compose\.material3?|com\.google\.android\.material)"""),
        ANY,
        "R9: Material lives only in the default skin (gradle/material-allowed.txt)",
        unless = { it in materialAllowed },
    ),
    ForbiddenCoordinate(
        setOf(StapelFamily.BRICK_UI, StapelFamily.CAPABILITY_UI),
        Regex("""io\.coil-kt.*"""),
        ANY,
        "UI-TOKENS §2: brick UI never sees Coil types (use stapel-image-compose)",
        compileOnly = true,
    ),
)

private val PRERELEASE =
    Regex("""(?i)[-.+](snapshot|dev|eap|alpha|beta|rc|cr|m|milestone|preview|pre)[-.]?\d*([-.+]|$)""")

/** True for a version string that is not a final release. */
fun isPreRelease(version: String): Boolean = PRERELEASE.containsMatchIn(version)

/**
 * `checkDependencyDirection` (D6). Two independent sensors:
 * 1. declared project edges of main configurations against the layer table, brick and rank rules;
 * 2. resolved external coordinates of every main compilation (all targets) against the forbidden table.
 */
abstract class CheckDependencyDirection : DefaultTask() {
    /** This module. */
    @get:Input abstract val self: Property<String>

    /** Family name of this module. */
    @get:Input abstract val selfFamily: Property<String>

    /** Brick key ("" when none). */
    @get:Input abstract val selfBrick: Property<String>

    /** Project edges: "configuration|target|targetFamily|targetBrick". */
    @get:Input abstract val edges: ListProperty<String>

    /** Resolved roots of main classpaths, by configuration name (compile ones end with "#compile"). */
    @get:Input abstract val resolved: MapProperty<String, ResolvedComponentResult>

    /** gradle/substrate-ranks.properties and gradle/material-allowed.txt and the pre-release allow-list. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val dataFiles: ConfigurableFileCollection

    /** Stamp. */
    @get:OutputFile abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        val files = dataFiles.files.associateBy { it.name }
        val ranks = Properties().apply { files["substrate-ranks.properties"]?.reader()?.use { load(it) } }
        val materialAllowed = files["material-allowed.txt"]?.readLines().orEmpty()
            .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
        val preAllowed = files["prerelease-allowlist.txt"]?.readLines().orEmpty()
            .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()

        val me = self.get()
        val family = StapelFamily.valueOf(selfFamily.get())
        val brick = selfBrick.get()
        val errors = mutableListOf<String>()

        // Sensor 1: project edges.
        val allowed = LAYERS.first { it.from == family }.mayDependOn
        for (edge in edges.get().distinct()) {
            val (conf, target, tFamilyName, tBrick) = edge.split("|")
            if (tFamilyName.isEmpty()) {
                errors += "$me -> $target ($conf): target is not a stapel module (no family plugin)."
                continue
            }
            val tFamily = StapelFamily.valueOf(tFamilyName)
            val rule = "$family may depend on ${allowed.joinToString()}"
            if (tFamily !in allowed) {
                errors += "$me ($family) -> $target ($tFamily) via $conf: forbidden by the layer table ($rule). " +
                    "Fix: declare the interface in the substrate and wire the two in the app."
                continue
            }
            val sameBrickNeeded = tFamily in setOf(StapelFamily.BRICK_DOMAIN, StapelFamily.BRICK_UI, StapelFamily.BRICK_ADAPTER)
            if (sameBrickNeeded && tBrick != brick) {
                errors += "$me -> $target via $conf: a brick never depends on another brick (D6). " +
                    "Fix: put the shared interface in the substrate; the app wires both bricks."
                continue
            }
            if (family == StapelFamily.CAPABILITY_UI && tFamily == StapelFamily.CAPABILITY && "$target-ui" != me) {
                errors += "$me -> $target via $conf: a capability UI depends only on its own capability."
                continue
            }
            val sameKind = (family == StapelFamily.SUBSTRATE && tFamily == StapelFamily.SUBSTRATE) ||
                (family == StapelFamily.SUBSTRATE_UI && tFamily == StapelFamily.SUBSTRATE_UI)
            if (sameKind) {
                val mine = ranks.getProperty(me)?.toIntOrNull()
                val theirs = ranks.getProperty(target)?.toIntOrNull()
                when {
                    mine == null -> errors += "$me has no rank in gradle/substrate-ranks.properties (ask the build owner)."
                    theirs == null -> errors += "$target has no rank in gradle/substrate-ranks.properties (ask the build owner)."
                    theirs >= mine -> errors += "$me (rank $mine) -> $target (rank $theirs) via $conf: substrate edges " +
                        "go strictly downward (gradle/substrate-ranks.properties)."
                }
            }
        }

        // Sensor 2: resolved external coordinates.
        val rules = forbiddenCoordinates(materialAllowed).filter { family in it.families && !it.unless(me) }
        val seen = mutableSetOf<String>()
        for ((confKey, root) in resolved.get()) {
            val compile = confKey.endsWith("#compile")
            val conf = confKey.substringBefore('#')
            walk(root) { component, via ->
                val id = component.id as? ModuleComponentIdentifier ?: return@walk
                for (r in rules) {
                    if (r.compileOnly && !compile) continue
                    if (r.groupRegex.matches(id.group) && r.nameRegex.matches(id.module)) {
                        val key = "${r.why}|${id.group}:${id.module}"
                        if (seen.add(key)) {
                            errors += "$me: ${id.group}:${id.module}:${id.version} on $conf (via $via) — ${r.why}."
                        }
                    }
                }
                if (family.published && isPreRelease(id.version) &&
                    "${id.group}:${id.module}:${id.version}" !in preAllowed
                ) {
                    val key = "pre|${id.group}:${id.module}:${id.version}"
                    if (seen.add(key)) {
                        errors += "$me: pre-release ${id.group}:${id.module}:${id.version} on $conf (via $via) — R1: " +
                            "only coordinates on gradle/prerelease-allowlist.txt, and never in a published POM."
                    }
                }
            }
        }

        if (errors.isNotEmpty()) {
            throw GradleException("checkDependencyDirection failed:\n" + errors.joinToString("\n") { "  - $it" })
        }
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }

    private fun walk(root: ResolvedComponentResult, visit: (ResolvedComponentResult, String) -> Unit) {
        val seen = HashSet<Any>()
        val queue = ArrayDeque<Pair<ResolvedComponentResult, String>>()
        queue.add(root to "direct")
        while (queue.isNotEmpty()) {
            val (c, via) = queue.removeFirst()
            if (!seen.add(c.id)) continue
            if (c != root) visit(c, via)
            val label = if (c == root) "direct" else c.id.displayName
            c.dependencies.filterIsInstance<ResolvedDependencyResult>().forEach {
                queue.add(it.selected to (if (c == root) "direct" else label))
            }
        }
    }
}

private val EDGE_CONFIG = Regex("""(.*)(Api|Implementation|CompileOnly|RuntimeOnly|api|implementation|compileOnly|runtimeOnly)""")

/** Registers `checkDependencyDirection` (stapel.depcheck). */
internal fun Project.applyStapelDepcheck(ext: StapelExtension) {
    val root = rootProject
    val task = tasks.register("checkDependencyDirection", CheckDependencyDirection::class.java) {
        group = STAPEL_GATES
        description = "D6 dependency direction: declared project edges + resolved external coordinates."
        self.set(this@applyStapelDepcheck.name)
        selfFamily.set(ext.family.map { it.name })
        selfBrick.set(ext.brick.orElse(""))
        dataFiles.from(
            root.file("gradle/substrate-ranks.properties"),
            root.file("gradle/material-allowed.txt"),
            root.file("gradle/prerelease-allowlist.txt"),
        )
        edges.set(
            provider {
                configurations.filter { c ->
                    EDGE_CONFIG.matches(c.name) && !c.name.contains("test", ignoreCase = true) &&
                        !c.name.startsWith("ksp")
                }.flatMap { c ->
                    c.dependencies.withType(ProjectDependency::class.java).map { d ->
                        val target = root.project(d.path)
                        val tFamily = target.extensions.extraProperties.let {
                            if (it.has(FAMILY_PROPERTY)) it.get(FAMILY_PROPERTY).toString() else ""
                        }
                        val tBrick = target.extensions.extraProperties.let {
                            if (it.has(BRICK_PROPERTY)) it.get(BRICK_PROPERTY).toString() else ""
                        }
                        "${c.name}|${target.name}|$tFamily|$tBrick"
                    }
                }.sorted()
            },
        )
        stamp.set(layout.buildDirectory.file("stapel-gates/checkDependencyDirection"))
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
        val kmp = extensions.getByType(KotlinMultiplatformExtension::class.java)
        kmp.targets.configureEach {
            if (platformType == KotlinPlatformType.common) return@configureEach
            compilations.matching { it.name == "main" }.configureEach {
                val compileName = compileDependencyConfigurationName
                val runtimeName = runtimeDependencyConfigurationName
                task.configure {
                    resolved.put(
                        "$compileName#compile",
                        configurations.named(compileName).flatMap { it.incoming.resolutionResult.rootComponent },
                    )
                    if (runtimeName != null) {
                        resolved.put(
                            "$runtimeName#runtime",
                            configurations.named(runtimeName).flatMap { it.incoming.resolutionResult.rootComponent },
                        )
                    }
                }
            }
        }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
        task.configure {
            resolved.put("compileClasspath#compile", configurations.named("compileClasspath").flatMap { it.incoming.resolutionResult.rootComponent })
            resolved.put("runtimeClasspath#runtime", configurations.named("runtimeClasspath").flatMap { it.incoming.resolutionResult.rootComponent })
        }
    }
    pluginManager.withPlugin("lifecycle-base") {
        tasks.named("check").configure { dependsOn(task) }
    }
    tasks.named("stapelCheckFast").configure { dependsOn(task) }
}

/**
 * `checkReleaseCoordinates` (R1): the generated POM and Gradle module metadata of a published module carry
 * no pre-release coordinate — the allow-list does not apply here. Part of the release path.
 */
abstract class CheckReleaseCoordinates : DefaultTask() {
    /** Generated POM and .module files. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val metadata: ConfigurableFileCollection

    /** Project path. */
    @get:Input abstract val projectPath: Property<String>

    /** Stamp. */
    @get:OutputFile abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        val versionRe = Regex(""""(?:requires|strictly|prefers)"\s*:\s*"([^"]+)"|<version>([^<]+)</version>""")
        val bad = metadata.files.filter { it.isFile }.flatMap { f ->
            versionRe.findAll(f.readText()).map { m -> m.groupValues[1].ifEmpty { m.groupValues[2] } }
                .filter { isPreRelease(it) }.map { "${f.name}: $it" }.toList()
        }
        if (bad.isNotEmpty()) {
            throw GradleException(
                "checkReleaseCoordinates (${projectPath.get()}): a published POM/module may not carry a " +
                    "pre-release coordinate (R1):\n" + bad.distinct().joinToString("\n") { "  $it" },
            )
        }
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("ok\n")
    }
}

internal fun Project.registerCheckReleaseCoordinates() {
    val task = tasks.register("checkReleaseCoordinates", CheckReleaseCoordinates::class.java) {
        group = "stapel release"
        description = "No pre-release coordinate in a published POM or module file (R1)."
        projectPath.set(path)
        metadata.from(tasks.matching { it.name.startsWith("generatePomFileFor") || it.name.startsWith("generateMetadataFileFor") })
        stamp.set(layout.buildDirectory.file("stapel-gates/checkReleaseCoordinates"))
    }
    tasks.matching { it.name.endsWith("ToMavenCentralRepository") }.configureEach { dependsOn(task) }
}
