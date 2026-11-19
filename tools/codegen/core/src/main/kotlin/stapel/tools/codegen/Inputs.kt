package stapel.tools.codegen

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes

/** One vendored contract file and the digest the pin records for it. */
data class PinnedFile(val source: String, val sha256: String)

/**
 * `contracts/<brick>/pin.json`: which commit of which brick repository the vendored files come from.
 * Moving to another contract (a release, or contract profile 2) is a change of this file plus the
 * vendored bytes, nothing else.
 */
data class Pin(
    val module: String,
    val repo: String,
    val tag: String,
    val ref: String,
    val version: String,
    val files: Map<String, PinnedFile>,
) {
    companion object {
        fun parse(o: JsonObject): Pin = Pin(
            module = o.str("module") ?: error("pin.json: module"),
            repo = o.str("repo") ?: error("pin.json: repo"),
            tag = o.str("tag") ?: error("pin.json: tag"),
            ref = o.str("ref") ?: error("pin.json: ref"),
            version = o.str("version") ?: error("pin.json: version"),
            files = (o.obj("files") ?: JsonObject(emptyMap())).mapValues { (_, v) ->
                val f = v.jsonObject
                PinnedFile(f.str("source") ?: error("pin.json: source"), f.str("sha256") ?: error("pin.json: sha256"))
            }.toSortedMap(),
        )
    }
}

/** One overlay entry: an interim correction of the contract, naming the source patch it stands in for. */
data class OverlayOperation(
    val fixes: String?,
    val source: String?,
    val errors: Map<Int, List<String>>?,
    val paramTypes: Map<String, String>,
    val auth: String?,
    val exclude: Boolean,
    val alias: String?,
    /** Step-up scope (`x-stapel-verification.scope`). */
    val verification: String?,
    /** Captcha requirement (`x-stapel-captcha`): action, header, field. */
    val captcha: Triple<String, String?, String?>?,
)

data class Overlay(val module: String?, val forRef: String?, val operations: Map<String, OverlayOperation>) {
    /** Entries counted by the overlay budget ratchet (one per corrected aspect of an operation). */
    val entryCount: Int
        get() = operations.values.sumOf { o ->
            listOf(o.errors != null, o.auth != null, o.exclude, o.alias != null, o.verification != null, o.captcha != null).count { it }
        }

    companion object {
        val EMPTY: Overlay = Overlay(null, null, emptyMap())

        fun parse(o: JsonObject): Overlay = Overlay(
            module = o.str("module"),
            forRef = o.str("for_ref"),
            operations = (o.obj("operations") ?: JsonObject(emptyMap())).mapValues { (_, v) ->
                val e = v.jsonObject
                OverlayOperation(
                    fixes = e.str("fixes"),
                    source = e.str("source"),
                    errors = e.obj("errors")?.entries?.associate { (k, codes) ->
                        k.toInt() to codes.jsonArray.map { it.jsonPrimitive.content }
                    },
                    paramTypes = e.obj("param_types")?.mapValues { it.value.jsonPrimitive.content }.orEmpty(),
                    auth = e.str("auth"),
                    exclude = e.bool("exclude"),
                    alias = e.str("alias"),
                    verification = e.str("verification"),
                    captcha = e.obj("captcha")?.let { c -> Triple(c.str("action") ?: "", c.str("header"), c.str("field")) },
                )
            }.toSortedMap(),
        )
    }
}

/** Everything the generator reads for one brick. */
class ContractSet(
    val brick: String,
    val schema: JsonObject,
    val schemaBytes: ByteArray,
    val errors: JsonArray?,
    val flows: JsonArray?,
    val capabilities: JsonObject?,
    val overlay: Overlay,
    val cachePlan: JsonObject?,
    val pin: Pin?,
    val ratchet: JsonObject?,
) {
    companion object {
        const val SCHEMA = "schema.json"
        const val ERRORS = "errors.json"
        const val FLOWS = "flows.json"
        const val CAPABILITIES = "capabilities.json"
        const val OVERLAY = "overlay.json"
        const val CACHE_PLAN = "cache.json"
        const val PIN = "pin.json"
        const val RATCHET = "ratchet.json"

        /** Reads `contracts/<brick>/`; only `schema.json` is mandatory. */
        fun load(brick: String, dir: Path): ContractSet {
            val schemaFile = dir.resolve(SCHEMA)
            require(schemaFile.exists()) { "No $SCHEMA in $dir" }
            val bytes = schemaFile.readBytes()
            fun objOf(name: String): JsonObject? = dir.resolve(name).takeIf { it.exists() }
                ?.let { parseJson.parseToJsonElement(Files.readString(it)).jsonObject }
            fun arrOf(name: String): JsonArray? = dir.resolve(name).takeIf { it.exists() }
                ?.let { parseJson.parseToJsonElement(Files.readString(it)).jsonArray }
            return ContractSet(
                brick = brick,
                schema = parseJson.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject,
                schemaBytes = bytes,
                errors = arrOf(ERRORS),
                flows = arrOf(FLOWS),
                capabilities = objOf(CAPABILITIES),
                overlay = objOf(OVERLAY)?.let(Overlay::parse) ?: Overlay.EMPTY,
                cachePlan = objOf(CACHE_PLAN),
                pin = objOf(PIN)?.let(Pin::parse),
                ratchet = objOf(RATCHET),
            )
        }
    }
}
