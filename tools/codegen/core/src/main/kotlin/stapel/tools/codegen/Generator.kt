package stapel.tools.codegen

import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.TypeAliasSpec
import com.squareup.kotlinpoet.TypeSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import stapel.tools.codegen.ir.ApiModel
import stapel.tools.codegen.ir.Diagnostics
import stapel.tools.codegen.ir.ModelRole
import stapel.tools.codegen.ir.ObjectDecl
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.relativeTo

/** Thrown for anything that must stop generation; the message is the whole report. */
class GenerationFailed(message: String) : RuntimeException(message)

/** Output of one run: files relative to the generated root, plus the model they came from. */
class Generation(val model: ApiModel, val files: java.util.SortedMap<String, String>, val report: String)

/**
 * `gen` for one brick (§9): pinned contracts + overlay + cache plan → `src/commonMain/generated/`.
 *
 * The generated root holds `kotlin/` (the sources), `api-names.lock.json` (the name lock, read on
 * every run) and `codegen-report.json` (diagnostics, the input of the ratchet).
 */
class Generator(
    private val brick: String,
    private val contractDir: Path,
    private val acceptRenames: Boolean = false,
    /** Root package of the output; a brick's is `stapel.<brick>.api`, an app's own module names its own. */
    apiPackage: String? = null,
) {
    private val apiPkg = apiPackage ?: ("stapel." + brick.replace('-', '.') + ".api")

    /** Builds the model and every output file in memory; nothing is written. */
    fun run(previousLock: JsonObject? = null): Generation {
        contractDir.resolve(ContractSet.PIN).takeIf { it.exists() }?.let { verifyPin(Pin.parse(parseJsonObject(Files.readString(it)))) }
        val set = ContractSet.load(brick, contractDir)
        val model = ContractParser(set).parse()
        val d = model.diagnostics
        val plan = CachePlan.parse(set.cachePlan, brick, model.operations, d)
        failOnErrors(d)
        val typesByName = model.types.associateBy { it.name }
        val models = ModelEmitter("$apiPkg.model", typesByName, d)
        val api = ApiEmitter(model, apiPkg, models, typesByName, plan)
        val files = java.util.TreeMap<String, String>()

        fun put(pkg: String, name: String, build: FileSpec.Builder.() -> Unit) {
            val spec = FileSpec.builder(pkg, name).indent("    ").addFileComment("%L", header(model)).apply(build).build()
            val path = "kotlin/" + pkg.replace('.', '/') + "/" + name + ".kt"
            check(path !in files) { "two outputs at $path" }
            files[path] = normalize(spec.toString())
        }
        fun type(pkg: String, spec: TypeSpec) = put(pkg, spec.name!!) { addType(spec) }
        fun alias(pkg: String, spec: TypeAliasSpec) = put(pkg, spec.name) { addTypeAlias(spec) }

        model.types.forEach { decl ->
            val specs = models.emit(decl)
            put("$apiPkg.model", decl.name) { specs.forEach { addType(it) } }
        }
        type(apiPkg, api.apiInterface())
        type(apiPkg, api.apiClient())
        type(apiPkg, api.operations())
        api.urls()?.let { type(apiPkg, it) }
        type(apiPkg, api.keys())
        type(apiPkg, api.invalidation())
        type(apiPkg, api.contractObject())
        val pagers = api.pagers()
        if (pagers.isNotEmpty()) put(apiPkg, Names.pascal(brick) + "Pagers") { pagers.forEach { addFunction(it) } }
        type("$apiPkg.error", api.brickErrors())
        type("$apiPkg.error", api.errorCatalog())
        model.operations.forEach { op ->
            val (sealed, aliasSpec) = api.opError(op)
            sealed?.let { type("$apiPkg.error", it) }
            aliasSpec?.let { alias("$apiPkg.error", it) }
        }
        val examples = Examples.fileSpec(set.schema, model, apiPkg)
        files[TEST_PREFIX + "kotlin/" + apiPkg.replace('.', '/') + "/" + examples.name + ".kt"] =
            normalize(examples.toBuilder().indent("    ").addFileComment("%L", header(model)).build().toString())

        val lock = nameLock(model, previousLock)
        files[LOCK] = prettyJson(lock)
        val report = report(model, d)
        files[REPORT] = report
        failOnErrors(d)
        return Generation(model, files, report)
    }

    private fun failOnErrors(d: Diagnostics) {
        if (d.errors.isNotEmpty()) {
            throw GenerationFailed(
                "stapel-codegen: ${d.errors.size} contract error(s) for $brick; nothing generated:\n" +
                    d.errors.joinToString("\n") { "  ${it.code} ${it.pointer}: ${it.message}" },
            )
        }
    }

    private fun header(model: ApiModel): String {
        val s = model.stamp
        return "Generated by stapel-codegen ${RuntimeNames.GENERATOR_VERSION} (runtime ABI ${RuntimeNames.RUNTIME_ABI}) — DO NOT EDIT.\n" +
            "Contract: ${s.module} ${s.version} @ ${s.ref.take(12)} | schema sha256:${s.schemaSha256.take(12)} | profile ${s.profile}\n" +
            "Regenerate: ./gradlew stapelGenerate | Drift gate: ./gradlew stapelGenerateCheck"
    }

    /** `\n` line endings, no trailing spaces, one trailing newline. */
    private fun normalize(text: String): String =
        text.replace("\r\n", "\n").lines().joinToString("\n") { it.trimEnd() }.trimEnd() + "\n"

    private fun verifyPin(pin: Pin) {
        val problems = mutableListOf<String>()
        for ((file, pinned) in pin.files) {
            val path = contractDir.resolve(file)
            if (!path.exists()) {
                problems += "$file is pinned but not vendored"
                continue
            }
            val actual = sha256(Files.readAllBytes(path))
            if (actual != pinned.sha256) problems += "$file: sha256 $actual != pinned ${pinned.sha256}"
        }
        if (problems.isNotEmpty()) {
            throw GenerationFailed("stapel-codegen: vendored contracts of $brick do not match contracts/$brick/pin.json:\n" + problems.joinToString("\n") { "  E-PIN-DIGEST $it" })
        }
    }

    /** The lock: operationId → function name, component → type name. A locked name never changes silently. */
    private fun nameLock(model: ApiModel, previous: JsonObject?): JsonObject {
        val ops = model.operations.associate { it.id to it.name }.toSortedMap()
        val byPointer = model.types.groupBy { it.pointer }
        val types = model.types.associate { t ->
            val twin = (byPointer[t.pointer]?.size ?: 0) > 1 && (t as? ObjectDecl)?.role == ModelRole.REQUEST
            t.pointer.removePrefix("#/components/schemas/") + (if (twin) " (request)" else "") to t.name
        }.toSortedMap()
        if (previous != null && !acceptRenames) {
            val problems = mutableListOf<String>()
            val oldOps = previous.obj("operations").orEmptyObj().mapValues { it.value.jsonPrimitive.content }
            for ((id, old) in oldOps) {
                val now = ops[id]
                when {
                    now == null -> problems += "operation $id ($old) left the contract; delete its lock entry in the same change (gen --accept-renames)"
                    now != old -> problems += "operation $id would be renamed $old -> $now (gen --accept-renames)"
                }
            }
            val oldTypes = previous.obj("types").orEmptyObj().mapValues { it.value.jsonPrimitive.content }
            for ((component, old) in oldTypes) {
                val now = types[component]
                if (now != null && now != old) problems += "type of $component would be renamed $old -> $now (gen --accept-renames)"
            }
            if (problems.isNotEmpty()) throw GenerationFailed("stapel-codegen: name lock of $brick:\n" + problems.joinToString("\n") { "  E-NAME-LOCK $it" })
        }
        return JsonObject(
            mapOf(
                "operations" to JsonObject(ops.mapValues { JsonPrimitive(it.value) }),
                "types" to JsonObject(types.mapValues { JsonPrimitive(it.value) }),
            ),
        )
    }

    private fun report(model: ApiModel, d: Diagnostics): String {
        val counts = d.counts()
        return prettyJson(
            JsonObject(
                mapOf(
                    "brick" to JsonPrimitive(brick),
                    "contract" to JsonObject(
                        mapOf(
                            "module" to JsonPrimitive(model.stamp.module),
                            "version" to JsonPrimitive(model.stamp.version),
                            "ref" to JsonPrimitive(model.stamp.ref),
                            "profile" to JsonPrimitive(model.stamp.profile),
                            "schema_sha256" to JsonPrimitive(model.stamp.schemaSha256),
                        ),
                    ),
                    "operations" to JsonPrimitive(model.operations.size),
                    "types" to JsonPrimitive(model.types.size),
                    "counts" to JsonObject(counts.mapValues { JsonPrimitive(it.value) }),
                    "diagnostics" to JsonArray(
                        d.all.map { JsonObject(mapOf("code" to JsonPrimitive(it.code), "pointer" to JsonPrimitive(it.pointer), "message" to JsonPrimitive(it.message))) },
                    ),
                ),
            ),
        )
    }

    companion object {
        const val LOCK = "api-names.lock.json"
        const val REPORT = "codegen-report.json"

        /** Prefix of outputs that belong to the test generated root (`src/commonTest/generated/`). */
        const val TEST_PREFIX = "test/"

        /** Every file currently under [root], relative, `/`-separated, sorted. */
        fun existingFiles(root: Path): java.util.SortedMap<String, String> {
            val out = java.util.TreeMap<String, String>()
            if (!root.exists()) return out
            Files.walk(root).use { stream ->
                stream.filter { it.isRegularFile() }.forEach { out[it.relativeTo(root).toString().replace('\\', '/')] = it.readText() }
            }
            return out
        }

        /** Differences between what is on disk and what a run produced (the `--check` verdict). */
        fun diff(existing: Map<String, String>, produced: Map<String, String>): List<String> {
            val out = mutableListOf<String>()
            (existing.keys + produced.keys).toSortedSet().forEach { path ->
                val a = existing[path]
                val b = produced[path]
                when {
                    a == null -> out += "missing: $path"
                    b == null -> out += "not produced by the generator (delete it): $path"
                    a != b -> out += "differs: $path (first difference at line ${firstDiffLine(a, b)})"
                }
            }
            return out
        }

        private fun firstDiffLine(a: String, b: String): Int {
            val x = a.lines()
            val y = b.lines()
            for (i in 0 until maxOf(x.size, y.size)) if (x.getOrNull(i) != y.getOrNull(i)) return i + 1
            return 0
        }

        /**
         * Ratchet (R11, §8.3, §13): counts per diagnostic code may not exceed `ratchet.json` and must be
         * lowered there when they fall; no undocumented element may appear that the previous report did
         * not list; the overlay entry count may not exceed its budget.
         */
        fun ratchet(
            set: ContractSet,
            report: String,
            previousReport: String?,
            update: Boolean,
        ): Pair<List<String>, JsonObject?> {
            val now = parseJson.parseToJsonElement(report).jsonObject
            val counts = now.obj("counts").orEmptyObj().mapValues { it.value.jsonPrimitive.int }
            val overlay = set.overlay.entryCount
            val baseline = set.ratchet
            val problems = mutableListOf<String>()
            if (previousReport != null) {
                val before = parseJson.parseToJsonElement(previousReport).jsonObject.arr("diagnostics").orEmpty()
                    .map { it.jsonObject.str("code") + " " + it.jsonObject.str("pointer") }.toSet()
                now.arr("diagnostics").orEmpty().map { it.jsonObject }
                    .filter { it.str("code") in Diagnostics.DOC_CODES }
                    .filter { (it.str("code") + " " + it.str("pointer")) !in before }
                    .forEach { problems += "new undocumented element: ${it.str("code")} ${it.str("pointer")} — document it at the source" }
            }
            val allowed = baseline?.obj("diagnostics").orEmptyObj().mapValues { it.value.jsonPrimitive.int }
            val budget = baseline?.int("overlay_entries")
            if (update) {
                val lowered = (allowed.keys + counts.keys).toSortedSet().associateWith { code ->
                    val c = counts[code] ?: 0
                    val a = allowed[code]
                    if (a != null && c > a) problems += "$code: $c > baseline $a (the ratchet never rises)"
                    if (a == null && baseline != null && c > 0) problems += "$code: $c new findings (the ratchet never rises)"
                    c
                }.filterValues { it > 0 }
                if (budget != null && overlay > budget) problems += "overlay: $overlay entries > budget $budget"
                val next = JsonObject(
                    mapOf(
                        "diagnostics" to JsonObject(lowered.mapValues { JsonPrimitive(it.value) }),
                        "overlay_entries" to JsonPrimitive(overlay),
                    ),
                )
                return problems to next
            }
            if (baseline == null) {
                problems += "no contracts/${set.brick}/${ContractSet.RATCHET}; create it with gen --update-ratchet"
                return problems to null
            }
            for (code in (allowed.keys + counts.keys).toSortedSet()) {
                val c = counts[code] ?: 0
                val a = allowed[code] ?: 0
                if (c > a) problems += "$code: $c > baseline $a"
                if (c < a) problems += "$code: $c < baseline $a — lower the baseline (gen --update-ratchet)"
            }
            if (budget == null || overlay > budget) problems += "overlay: $overlay entries > budget ${budget ?: 0}"
            if (budget != null && overlay < budget) problems += "overlay: $overlay entries < budget $budget — lower it (gen --update-ratchet)"
            return problems to null
        }

        private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
    }
}
