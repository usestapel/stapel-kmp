package stapel.conventions

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
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
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File
import java.util.zip.ZipFile

/**
 * The minimum iOS version of every stapel-kmp library binary, the reference app and every app the
 * scaffold generates (owner, 2026-11-20). API newer than this is called only behind an availability
 * check with a working fallback. Moving it is a decision, not a refactoring: the sample's Xcode
 * project, its `shared` build and the stapel-tools templates move with it (`checkIosFloor`).
 */
const val IOS_FLOOR: String = "15.0"

/** The konan targets whose `osVersionMin` the floor pins. */
internal val IOS_KONAN_TARGETS: List<String> = listOf("ios_arm64", "ios_simulator_arm64")

/**
 * The Kotlin/Native argument that pins the minimum OS of a binary. Kotlin 2.4 has no Gradle DSL for
 * it (the `swiftPMDependencies` deployment targets cover SwiftPM import only); the konan property
 * override is the supported route. Kotlin 2.4.21's own default is 15.0 as well — the pin keeps a
 * compiler update from moving the floor silently.
 */
fun iosFloorCompilerArg(floor: String = IOS_FLOOR): String =
    "-Xoverride-konan-properties=" + IOS_KONAN_TARGETS.joinToString(";") { "osVersionMin.$it=$floor" }

/** Every binary (test executables included) of every iOS target is linked for [IOS_FLOOR]. */
internal fun applyIosFloor(kmp: KotlinMultiplatformExtension) {
    kmp.targets.withType(KotlinNativeTarget::class.java).configureEach {
        binaries.configureEach { freeCompilerArgs += iosFloorCompilerArg() }
    }
}

/** Dotted version compare: `15.0` < `15.0.1` < `16`. */
internal fun compareVersions(a: String, b: String): Int {
    val x = a.split('.').map { it.toIntOrNull() ?: 0 }
    val y = b.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

private fun sameVersion(a: String, b: String) = compareVersions(a, b) == 0

/**
 * `checkIosFloor` (root): every place in the repository that declares a minimum iOS declares the
 * floor — Xcode projects (`IPHONEOS_DEPLOYMENT_TARGET`, every target and configuration), xcconfig
 * files, Info.plist `MinimumOSVersion`, and Gradle builds outside build-logic (the separate sample
 * build): a build that links an Apple framework or executable pins `osVersionMin` of both iOS konan
 * targets, and any `osVersionMin` it declares is the floor.
 */
abstract class CheckIosFloor : DefaultTask() {
    /** The files that may declare a minimum iOS. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val declarations: ConfigurableFileCollection

    /** Repository root, for readable paths. */
    @get:Input
    abstract val root: Property<String>

    /** The floor. */
    @get:Input
    abstract val floor: Property<String>

    /** Stamp. */
    @get:OutputFile
    abstract val stamp: RegularFileProperty

    @TaskAction
    fun run() {
        val floor = floor.get()
        val rootDir = File(root.get())
        val errors = declarations.files.sortedBy { it.path }.flatMap { f ->
            iosFloorViolations(f.relativeTo(rootDir).invariantSeparatorsPath, f.readText(), floor)
        }
        if (errors.isNotEmpty()) {
            throw GradleException(
                "checkIosFloor failed: the minimum iOS is $floor (IOS_FLOOR in build-logic IosFloor.kt); " +
                    "newer API goes behind #available / an OS-version check with a fallback, not a higher target:\n" +
                    errors.joinToString("\n") { "  - $it" },
            )
        }
        stamp.get().asFile.apply { parentFile.mkdirs() }.writeText("ok $floor\n")
    }
}

private val PBX_TARGET = Regex("""IPHONEOS_DEPLOYMENT_TARGET\s*=\s*"?([0-9.]+)"?\s*;""")
private val XCCONFIG_TARGET = Regex("""^\s*IPHONEOS_DEPLOYMENT_TARGET\s*=\s*([0-9.]+)""", RegexOption.MULTILINE)
private val PLIST_MIN = Regex("""<key>MinimumOSVersion</key>\s*<string>([^<]*)</string>""")
private val KONAN_MIN = Regex("""osVersionMin\.(ios_[a-z0-9_]+)=([^;"\s]+)""")
private val FLOOR_VAL = Regex("""val\s+IOS_FLOOR\s*(?::\s*String)?\s*=\s*"([0-9.]+)"""")
private val LINKS_APPLE_BINARY = Regex("""\.framework\s*[({]|binaries\s*\.\s*framework|\bexecutable\s*[({]|XCFramework\(""")

/** The rule violations of one file (path relative to the repository root). */
internal fun iosFloorViolations(path: String, text: String, floor: String): List<String> {
    val name = path.substringAfterLast('/')
    val out = mutableListOf<String>()
    when {
        name == "project.pbxproj" -> {
            val found = PBX_TARGET.findAll(text).map { it.groupValues[1] }.toList()
            if (found.isEmpty()) out += "$path declares no IPHONEOS_DEPLOYMENT_TARGET (set $floor at project level)"
            found.filterNot { sameVersion(it, floor) }.distinct().forEach {
                out += "$path: IPHONEOS_DEPLOYMENT_TARGET = $it (must be $floor)"
            }
        }
        name.endsWith(".xcconfig") -> XCCONFIG_TARGET.findAll(text).map { it.groupValues[1] }
            .filterNot { sameVersion(it, floor) }.distinct()
            .forEach { out += "$path: IPHONEOS_DEPLOYMENT_TARGET = $it (must be $floor)" }
        name == "Info.plist" -> PLIST_MIN.findAll(text).map { it.groupValues[1] }
            .filterNot { sameVersion(it, floor) }.distinct()
            .forEach { out += "$path: MinimumOSVersion $it (must be $floor)" }
        name.endsWith(".gradle.kts") -> {
            val local = FLOOR_VAL.find(text)?.groupValues?.get(1)
            val declared = KONAN_MIN.findAll(text).map { m ->
                val raw = m.groupValues[2]
                val value = if (raw == "\$IOS_FLOOR" || raw == "\${IOS_FLOOR}") local ?: raw else raw
                m.groupValues[1] to value
            }.toList()
            declared.filterNot { sameVersion(it.second, floor) }.distinct().forEach { (t, v) ->
                out += "$path: osVersionMin.$t=$v (must be $floor)"
            }
            if (LINKS_APPLE_BINARY.containsMatchIn(text)) {
                IOS_KONAN_TARGETS.filter { t -> declared.none { it.first == t } }.forEach { t ->
                    out += "$path links an Apple binary but does not pin osVersionMin.$t " +
                        "(add `freeCompilerArgs += \"${iosFloorCompilerArg("\$IOS_FLOOR")}\"` to its binaries)"
                }
            }
        }
    }
    return out
}

/**
 * `checkIosBinaries`: no Mach-O object that can run code declares a minimum iOS above the floor.
 * Reads frameworks, `.app` bundles, static archives, test executables and klibs (the manifest's
 * `-m…-version-min` options and any bundled native object). An object above the floor that holds no
 * instructions and imports no symbol (resource data such as an ICU table) is reported, not failed:
 * it cannot call an API the floor lacks. macOS-free: the Mach-O reader is pure Kotlin.
 */
abstract class CheckIosBinaries : DefaultTask() {
    /** Files or directories to read. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val binaries: ConfigurableFileCollection

    /** The floor. */
    @get:Input
    abstract val floor: Property<String>

    /** Report. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun run() {
        val result = scanIosBinaries(binaries.files, floor.get())
        val text = buildString {
            appendLine("floor ${floor.get()}; read ${result.objects} Mach-O object(s) and ${result.klibs} klib(s) in ${result.files} file(s)")
            result.dataOnly.forEach { appendLine("note: $it") }
            result.violations.forEach { appendLine("FAIL: $it") }
        }
        report.get().asFile.apply { parentFile.mkdirs() }.writeText(text)
        logger.lifecycle(text.trimEnd())
        if (result.files == 0) throw GradleException("checkIosBinaries: nothing to read (build the iOS binaries first, or pass -Pstapel.ios.binaries=<paths>)")
        if (result.violations.isNotEmpty()) {
            throw GradleException(
                "checkIosBinaries failed: code built for a newer iOS than the floor ${floor.get()}:\n" +
                    result.violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}

internal class IosBinaryScan(
    val files: Int,
    val objects: Int,
    val klibs: Int,
    val violations: List<String>,
    val dataOnly: List<String>,
)

private val KLIB_VERSION_OPT = Regex("""-m(?:ios|iphoneos|ios-simulator|iphonesimulator)-version-min=([0-9.]+)|-apple-ios([0-9][0-9.]*)""")

internal fun scanIosBinaries(roots: Collection<File>, floor: String): IosBinaryScan {
    var files = 0
    var objects = 0
    var klibs = 0
    val violations = mutableListOf<String>()
    val dataOnly = mutableListOf<String>()
    fun judge(where: String, objs: List<MachObject>) {
        objects += objs.size
        objs.filter { it.isIos && it.minOs != null && compareVersions(it.minOs, floor) > 0 }.forEach { o ->
            val label = "$where(${o.name}) minos ${o.minOs}"
            if (o.hasCode || o.undefinedSymbols > 0) {
                violations += "$label: ${if (o.hasCode) "has instructions" else "imports ${o.undefinedSymbols} symbol(s)"}"
            } else {
                dataOnly += "$label: data only (no instructions, no imports)"
            }
        }
    }
    roots.flatMap { r -> if (r.isDirectory) r.walkTopDown().filter { it.isFile }.toList() else listOf(r) }
        .filter { it.isFile }
        .forEach { f ->
            if (f.name.endsWith(".klib")) {
                files++
                klibs++
                ZipFile(f).use { zip ->
                    zip.entries().asSequence().filter { !it.isDirectory }.forEach { e ->
                        val bytes = zip.getInputStream(e).use { it.readBytes() }
                        if (e.name.endsWith("/manifest") || e.name == "manifest") {
                            String(bytes).lines().filter { it.startsWith("compilerOpts") || it.startsWith("linkerOpts") }.forEach { line ->
                                KLIB_VERSION_OPT.findAll(line).forEach { m ->
                                    val v = m.groupValues[1].ifEmpty { m.groupValues[2] }
                                    if (compareVersions(v, floor) > 0) violations += "${f.path}!${e.name}: ${m.value} above the floor"
                                }
                            }
                        } else if (MachOReader.isMachOOrArchive(bytes)) {
                            judge("${f.path}!${e.name}", MachOReader.read(e.name.substringAfterLast('/'), bytes))
                        }
                    }
                }
            } else {
                val head = f.inputStream().use { s -> ByteArray(8).also { s.read(it) } }
                if (MachOReader.isMachOOrArchive(head)) {
                    files++
                    judge(f.path, MachOReader.read(f.name, f.readBytes()))
                }
            }
        }
    return IosBinaryScan(files, objects, klibs, violations, dataOnly)
}

/** The root tasks: `checkIosFloor` (a gate, any OS) and `checkIosBinaries` (after the iOS binaries exist). */
internal fun Project.registerIosFloorTasks() {
    val floorCheck = tasks.register("checkIosFloor", CheckIosFloor::class.java) {
        group = STAPEL_GATES
        description = "Every Xcode project, xcconfig, Info.plist and app build in the repository declares the iOS floor $IOS_FLOOR."
        declarations.from(
            fileTree(rootDir) {
                include("**/*.pbxproj", "**/*.xcconfig", "**/Info.plist", "**/*.gradle.kts")
                exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**", "**/node_modules/**", "build-logic/**", ".git/**")
            },
        )
        root.set(rootDir.absolutePath)
        floor.set(IOS_FLOOR)
        stamp.set(layout.buildDirectory.file("stapel-gates/checkIosFloor"))
    }
    tasks.named("check").configure { dependsOn(floorCheck) }
    tasks.named("stapelCheckFast").configure { dependsOn(floorCheck) }

    val explicit = providers.gradleProperty("stapel.ios.binaries")
    tasks.register("checkIosBinaries", CheckIosBinaries::class.java) {
        group = "stapel ios"
        description = "No iOS Mach-O code (test executables, staging klibs, or -Pstapel.ios.binaries=<paths>) targets an iOS above $IOS_FLOOR."
        val defaults = files(
            fileTree(layout.buildDirectory.dir("staging-repo")) { include("**/*.klib") },
            rootProject.subprojects.map { p -> fileTree(p.layout.buildDirectory.dir("bin")) { include("ios*/**/*.kexe") } },
        )
        val requested: List<File>? = explicit.orNull?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)?.map { file(it) }
        if (requested != null) binaries.from(requested) else binaries.from(defaults)
        floor.set(IOS_FLOOR)
        report.set(layout.buildDirectory.file("stapel-gates/checkIosBinaries.txt"))
    }
}
