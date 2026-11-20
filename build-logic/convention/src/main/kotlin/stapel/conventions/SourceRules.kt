package stapel.conventions

import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/** One rule hit in a source file (1-based line). */
data class SourceFinding(val rule: String, val line: Int, val what: String)

/**
 * Replaces comments and string/char literal contents with spaces, keeping every newline and the
 * length, so a pattern never matches documentation and offsets map back to lines. Handles nested
 * block comments, raw strings and `${…}` templates only as far as needed to find code (a template
 * expression is blanked with its string).
 */
fun blankCommentsAndStrings(src: String): String {
    val out = StringBuilder(src)
    var i = 0
    fun blank(from: Int, to: Int) {
        for (k in from until minOf(to, src.length)) if (src[k] != '\n') out.setCharAt(k, ' ')
    }
    while (i < src.length) {
        when {
            src.startsWith("//", i) -> {
                val end = src.indexOf('\n', i).let { if (it < 0) src.length else it }
                blank(i, end); i = end
            }
            src.startsWith("/*", i) -> {
                var depth = 1
                var j = i + 2
                while (j < src.length && depth > 0) {
                    when {
                        src.startsWith("/*", j) -> { depth++; j += 2 }
                        src.startsWith("*/", j) -> { depth--; j += 2 }
                        else -> j++
                    }
                }
                blank(i, j); i = j
            }
            src.startsWith("\"\"\"", i) -> {
                val end = src.indexOf("\"\"\"", i + 3).let { if (it < 0) src.length else it + 3 }
                blank(i + 3, end - 3); i = end
            }
            src[i] == '"' || src[i] == '\'' -> {
                val q = src[i]
                var j = i + 1
                while (j < src.length && src[j] != q && src[j] != '\n') j += if (src[j] == '\\') 2 else 1
                blank(i + 1, j); i = j + 1
            }
            else -> i++
        }
    }
    return out.toString()
}

private fun lineOf(text: String, offset: Int): Int = text.subSequence(0, offset).count { it == '\n' } + 1

/** G-R1 patterns: ambient time, dispatchers, randomness, platform locale and time zone. */
val AMBIENT_PATTERNS: List<Pair<Regex, String>> = listOf(
    Regex("""\bDispatchers\s*\.\s*(Main|IO|Default|Unconfined)\b""") to "Dispatchers.* (take StapelDispatchers)",
    Regex("""\bClock\s*\.\s*System\b""") to "Clock.System (take a kotlin.time.Clock)",
    Regex("""\bSystem\s*\.\s*(currentTimeMillis|nanoTime)\s*\(""") to "System clock (take a kotlin.time.Clock)",
    Regex("""\bRandom\s*\.\s*(Default\b|next\w*\s*\()""") to "Random.Default (take a Random or IdGenerator)",
    Regex("""[=(,]\s*(kotlin\.random\.)?Random\s*(?=[,)};]|$)""", RegexOption.MULTILINE) to
        "the Random companion as a value is Random.Default (take a Random or IdGenerator)",
    Regex("""\b(Uuid\s*\.\s*random|UUID\s*\.\s*randomUUID|Math\s*\.\s*random)\s*\(""") to "ambient random id (take an IdGenerator)",
    Regex("""\b(java\.util\.)?Random\s*\(\s*\)|\bSecureRandom\s*\(""") to "unseeded platform Random (take a Random or IdGenerator)",
    Regex("""\bLocale\s*\.\s*(getDefault|current)\b""") to "platform locale (take a LocaleProvider)",
    Regex("""\bLocaleList\s*\.\s*(getDefault|current)\b""") to "platform locale (take a LocaleProvider)",
    Regex("""\bNSLocale\s*\.\s*(currentLocale|preferredLanguages|systemLocale|autoupdatingCurrentLocale)\b""") to
        "platform locale (take a LocaleProvider)",
    Regex("""\bTimeZone\s*\.\s*(currentSystemDefault|getDefault)\b""") to "platform time zone (take a TimeZoneProvider)",
    Regex("""\bZoneId\s*\.\s*systemDefault\b""") to "platform time zone (take a TimeZoneProvider)",
    Regex("""\bNSTimeZone\s*\.\s*(localTimeZone|systemTimeZone|defaultTimeZone)\b""") to
        "platform time zone (take a TimeZoneProvider)",
)

private val VENDOR_IDENTIFIER = Regex("""(?i)[A-Za-z0-9_.]*(firebase|appmetrica)[A-Za-z0-9_]*""")

/**
 * G-X2 over one main source file of a facade, brick or `stapel-app`: no identifier (import,
 * package, type, function) names a vendor. Comments and strings are ignored, so documentation may
 * say "Firebase".
 */
fun vendorFindings(text: String): List<SourceFinding> {
    val code = blankCommentsAndStrings(text)
    return VENDOR_IDENTIFIER.findAll(code).map {
        SourceFinding("G-X2", lineOf(code, it.range.first), "vendor name in an identifier: ${it.value.trim()}")
    }.toList()
}

private val NATIVE_BINDING = Regex("""\b(cinterops?|swiftPMDependencies|cocoapods)\b""")

/** G-X2 over a module build file: no `cinterop`, `swiftPMDependencies` or `cocoapods` block in any Stapel artifact (ruling K3). */
fun nativeBindingFindings(text: String): List<SourceFinding> {
    val code = blankCommentsAndStrings(text)
    return NATIVE_BINDING.findAll(code).map {
        SourceFinding("G-X2", lineOf(code, it.range.first), "${it.value}: vendor SDKs reach iOS through an app-shell Swift bridge, never a native binding in a library")
    }.toList()
}

/** G-R1 over one file's text. */
fun ambientFindings(text: String): List<SourceFinding> {
    val code = blankCommentsAndStrings(text)
    return AMBIENT_PATTERNS.flatMap { (re, what) ->
        re.findAll(code).map { SourceFinding("G-R1", lineOf(code, it.range.first), what) }
    }.sortedBy { it.line }
}

private val CATCH = Regex("""\bcatch\s*\(\s*(\w+)\s*:\s*([\w.]+)\s*\)\s*\{""")
private val CATCH_ALL = setOf("Throwable", "Exception", "kotlin.Throwable", "kotlin.Exception", "java.lang.Exception")
private val CANCELLATION = setOf(
    "CancellationException", "kotlinx.coroutines.CancellationException", "kotlin.coroutines.cancellation.CancellationException",
)

private fun matchingClose(code: String, open: Int): Int {
    var depth = 0
    for (k in open until code.length) {
        when (code[k]) {
            '{' -> depth++
            '}' -> if (--depth == 0) return k
        }
    }
    return code.length - 1
}

private fun matchingOpen(code: String, close: Int): Int {
    var depth = 0
    for (k in close downTo 0) {
        when (code[k]) {
            '}' -> depth++
            '{' -> if (--depth == 0) return k
        }
    }
    return 0
}

/** The type caught by the clause right before [catchStart] in the same try chain, or null. */
private fun previousCaughtType(code: String, catchStart: Int): String? {
    val before = code.substring(0, catchStart).trimEnd()
    if (!before.endsWith("}")) return null
    val open = matchingOpen(code, before.length - 1)
    val header = code.substring(0, open).trimEnd()
    return Regex("""catch\s*\(\s*\w+\s*:\s*([\w.]+)\s*\)$""").find(header)?.groupValues?.get(1)
}

/**
 * G-R2 over one file's text: `runCatching` is banned; a `catch` of `Throwable`/`Exception` must
 * not swallow cancellation — it passes when a `CancellationException` clause precedes it in the
 * same chain, when its body rethrows the caught value, or when the body itself handles
 * `CancellationException` / calls `ensureActive()`; a `CancellationException` clause must rethrow.
 */
fun cancellationFindings(text: String): List<SourceFinding> {
    val code = blankCommentsAndStrings(text)
    val out = mutableListOf<SourceFinding>()
    Regex("""\brunCatching\b""").findAll(code).forEach {
        out += SourceFinding("G-R2", lineOf(code, it.range.first), "runCatching swallows CancellationException (use try/catch that rethrows it)")
    }
    for (m in CATCH.findAll(code)) {
        val (name, type) = m.destructured
        val open = m.range.last
        val body = code.substring(open + 1, matchingClose(code, open))
        val rethrows = name != "_" && Regex("""\bthrow\s+$name\b""").containsMatchIn(body)
        when (type) {
            in CANCELLATION -> if (!rethrows) {
                out += SourceFinding("G-R2", lineOf(code, m.range.first), "catch ($name: $type) must rethrow it")
            }
            in CATCH_ALL -> {
                val guarded = previousCaughtType(code, m.range.first) in CANCELLATION ||
                    rethrows || "CancellationException" in body || Regex("""\bensureActive\s*\(""").containsMatchIn(body)
                if (!guarded) {
                    out += SourceFinding(
                        "G-R2", lineOf(code, m.range.first),
                        "catch ($name: $type) swallows CancellationException (catch CancellationException first and rethrow it)",
                    )
                }
            }
        }
    }
    return out.sortedBy { it.line }
}

private val EXPRESSION_BODIED_TEST = Regex(
    """@(?:[\w.]+\.)?Test\b[^\n]*\n(?:\s*@[\w.]+(?:\([^)]*\))?\s*\n)*\s*(?:(?:public|internal|private|open|override)\s+)*fun\s+(`[^`]+`|\w+)\s*\(\s*\)\s*=\s*+(?!runTest\b)""",
)

/**
 * G-T1 over one JUnit 5 test file: a `@Test` function with an expression body must declare `: Unit`
 * (`fun x(): Unit = runBlocking { … }`). Without it the function returns whatever the block's last
 * expression is, and JUnit 5 silently skips a non-void test method (JUnit 4 fails it instead). `= runTest { … }` is exempt:
 * it returns `TestResult`, which is `Unit` on the JVM.
 */
fun testBodyFindings(text: String): List<SourceFinding> {
    val code = blankCommentsAndStrings(text)
    return EXPRESSION_BODIED_TEST.findAll(code).map {
        SourceFinding("G-T1", lineOf(code, it.range.first), "test ${it.groupValues[1]} has an expression body without `: Unit` (JUnit 5 skips non-void tests)")
    }.toList()
}

/** One line of `gradle/source-rules-baseline.txt`: a known hit tolerated until [until]. */
data class BaselineEntry(val project: String, val path: String, val rule: String, val until: java.time.LocalDate, val line: String)

/** Parses `<project> <module-relative path> <rule> <YYYY-MM-DD>  # owner, reason`. */
fun parseBaseline(lines: List<String>): List<BaselineEntry> = lines.mapNotNull { raw ->
    val line = raw.substringBefore('#').trim()
    if (line.isEmpty()) return@mapNotNull null
    val parts = line.split(Regex("\\s+"))
    require(parts.size == 4) { "gradle/source-rules-baseline.txt: expected '<project> <path> <rule> <YYYY-MM-DD>', got '$raw'" }
    BaselineEntry(parts[0], parts[1], parts[2], java.time.LocalDate.parse(parts[3]), raw.trim())
}

/**
 * Applies the baseline to one module's findings (`path` → findings): returns the errors. A finding
 * listed for its file and rule passes until the entry's date; an expired entry fails; an entry that
 * no longer matches anything fails too (remove it: the ratchet only goes down).
 */
fun applyBaseline(
    project: String,
    findings: Map<String, List<SourceFinding>>,
    baseline: List<BaselineEntry>,
    today: java.time.LocalDate,
): List<String> {
    val mine = baseline.filter { it.project == project }
    val errors = mutableListOf<String>()
    for ((path, hits) in findings) for (f in hits) {
        val entry = mine.firstOrNull { it.path == path && it.rule == f.rule }
        when {
            entry == null -> errors += "  $path:${f.line}: ${f.rule} ${f.what}"
            entry.until < today -> errors += "  $path:${f.line}: ${f.rule} ${f.what} (baseline entry expired ${entry.until})"
        }
    }
    for (e in mine) {
        if (findings[e.path].orEmpty().none { it.rule == e.rule }) {
            errors += "  gradle/source-rules-baseline.txt: '${e.line}' matches nothing any more — remove the line"
        }
    }
    return errors
}

/**
 * `checkSourceRules` (ARCH-CORE G-R1, G-R2) over the main sources of a library: no ambient
 * dispatcher, clock, randomness, platform locale or time zone outside `stapel-runtime`; no
 * swallowed `CancellationException` anywhere. Test source sets are not scanned.
 */
@CacheableTask
abstract class CheckSourceRules : StapelSourceGate() {
    /** Main Kotlin sources (every `src/<x>Main`). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** Test Kotlin sources (every `src/<x>Test`, `src/test`), for G-T1 only. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSources: ConfigurableFileCollection

    /** `gradle/source-rules-baseline.txt`. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val baselineFile: ConfigurableFileCollection

    /** Gradle project name, the baseline's first column. */
    @get:Input
    abstract val projectName: Property<String>

    /** False only for stapel-runtime, the one module that reads the platform. */
    @get:Input
    abstract val ambientRule: Property<Boolean>

    /** False for unpublished modules (apps, tools, end-to-end tests): G-R1/G-R2 do not apply. */
    @get:Input
    abstract val libraryRules: Property<Boolean>

    /**
     * True for modules whose tests run on JUnit 5 (tools, end-to-end tests): G-T1 applies. Kotlin
     * multiplatform tests run on JUnit 4, which fails a non-void test loudly instead of skipping it.
     */
    @get:Input
    abstract val junit5Rules: Property<Boolean>

    /** True for facades, bricks and `stapel-app` (substrate and brick families): G-X2 vendor identifiers. */
    @get:Input
    abstract val vendorRule: Property<Boolean>

    /** The module's `build.gradle.kts`, for the G-X2 native-binding rule (libraries only). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val buildFile: ConfigurableFileCollection

    /** Today, for baseline expiry (an input so a new day re-runs the gate). */
    @get:Input
    abstract val today: Property<String>

    /** Module directory, for messages. */
    @get:Internal
    abstract val moduleDir: DirectoryProperty

    @TaskAction
    fun run() {
        val dir = moduleDir.get().asFile
        val findings = mutableMapOf<String, List<SourceFinding>>()
        if (libraryRules.get()) {
            sources.files.filter { it.extension == "kt" }.forEach { f ->
                val text = f.readText()
                val hits = (if (ambientRule.get()) ambientFindings(text) else emptyList()) + cancellationFindings(text) +
                    (if (vendorRule.get()) vendorFindings(text) else emptyList())
                if (hits.isNotEmpty()) findings[f.relativeTo(dir).invariantSeparatorsPath] = hits
            }
        }
        if (libraryRules.get()) buildFile.files.filter { it.isFile }.forEach { f ->
            val hits = nativeBindingFindings(f.readText())
            if (hits.isNotEmpty()) findings[f.relativeTo(dir).invariantSeparatorsPath] = hits
        }
        if (junit5Rules.get()) testSources.files.filter { it.extension == "kt" }.forEach { f ->
            val hits = testBodyFindings(f.readText())
            if (hits.isNotEmpty()) findings[f.relativeTo(dir).invariantSeparatorsPath] = findings[f.relativeTo(dir).invariantSeparatorsPath].orEmpty() + hits
        }
        val baseline = parseBaseline(baselineFile.files.filter { it.isFile }.flatMap { it.readLines() })
        val errors = applyBaseline(projectName.get(), findings.toSortedMap(), baseline, java.time.LocalDate.parse(today.get()))
        if (errors.isNotEmpty()) {
            throw GradleException(
                "checkSourceRules (${projectPath.get()}): G-R1 time, dispatchers, randomness, locale and time zone " +
                    "come from stapel-runtime seams (injected); G-R2 cancellation is never swallowed; G-T1 tests " +
                    "with expression bodies declare `: Unit`; G-X2 no vendor name in a facade, brick or stapel-app " +
                    "identifier and no native binding in a library build file (docs/gates.md):\n" + errors.joinToString("\n"),
            )
        }
        pass("ok")
    }
}
