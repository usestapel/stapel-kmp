package stapel.tools.codegen

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import stapel.tools.codegen.ir.Diagnostics
import stapel.tools.codegen.ir.HttpMethod
import stapel.tools.codegen.ir.Operation

/**
 * `contracts/<brick>/cache.json` (ruling R10; schema owned by CODEGEN, consumed by `stapel-cache`
 * through the generated `<Brick>Keys` / `<Brick>Invalidation`):
 *
 * ```json
 * {
 *   "module": "auth",
 *   "operations": {
 *     "<operationId>": {
 *       "key": ["sessions", "{session_id}"],          // query: path after the namespace; {param} = argument
 *       "policy": "stale_while_revalidate",          // network_only | network_first | cache_first | stale_while_revalidate
 *       "persistence": "durable",                    // durable | memory_only | never
 *       "scope": "user",                             // user | app
 *       "sensitive": false,                          // never persisted when true
 *       "invalidates": [["sessions"], ["me"]],       // mutation: key prefixes; [] = whole namespace
 *       "offline": "natural"                         // mutation: natural | keyed idempotency; absent = never queued
 *     }
 *   }
 * }
 * ```
 * Operations absent from the plan get derived defaults (`W-CLIENT-DERIVED`): a query's key is its path
 * after `/api/v<n>/` with path parameters as segments and query parameters as arguments; a mutation
 * invalidates the whole namespace (over-invalidation is safe, under-invalidation is a stale screen).
 */
data class CacheEntry(
    val key: List<String>?,
    val policy: String?,
    val persistence: String?,
    val scope: String,
    val sensitive: Boolean,
    val invalidates: List<List<String>>?,
    val offline: String?,
)

class CachePlan(val entries: Map<String, CacheEntry>) {
    companion object {
        private val FIELDS = setOf("key", "policy", "persistence", "scope", "sensitive", "invalidates", "offline", "note")
        private val POLICIES = setOf("network_only", "network_first", "cache_first", "stale_while_revalidate")
        private val PERSISTENCE = setOf("durable", "memory_only", "never")
        private val SCOPES = setOf("user", "app")
        private val OFFLINE = setOf("natural", "keyed")

        /** Parses and validates the plan against the model's operations; violations are `E-CACHE-PLAN`. */
        fun parse(plan: JsonObject?, brick: String, operations: List<Operation>, diagnostics: Diagnostics): CachePlan {
            if (plan == null) return CachePlan(emptyMap())
            val byId = operations.associateBy { it.id }
            val p = "cache.json"
            plan.str("module")?.takeIf { it != brick }?.let { diagnostics.error("E-CACHE-PLAN", p, "module $it is not $brick") }
            val out = java.util.TreeMap<String, CacheEntry>()
            for ((id, el) in plan.obj("operations").orEmptyObj()) {
                val e = el.jsonObject
                val at = "$p#/operations/$id"
                val op = byId[id]
                if (op == null) {
                    diagnostics.error("E-CACHE-PLAN", at, "unknown or excluded operation $id")
                    continue
                }
                e.keys.filter { it !in FIELDS }.forEach { diagnostics.error("E-CACHE-PLAN", at, "unknown field $it") }
                val query = op.method == HttpMethod.GET
                fun segs(a: JsonArray): List<String> = a.map { (it as? JsonPrimitive)?.content ?: "" }
                val key = e.arr("key")?.let(::segs)
                val invalidates = e.arr("invalidates")?.map { segs(it.jsonArray) }
                if (key != null && !query) diagnostics.error("E-CACHE-PLAN", at, "a key on a mutation")
                if (invalidates != null && query) diagnostics.error("E-CACHE-PLAN", at, "invalidates on a query")
                key?.filter { it.startsWith("{") }?.forEach { seg ->
                    val name = seg.removePrefix("{").removeSuffix("}")
                    if (op.parameters.none { it.wireName == name }) diagnostics.error("E-CACHE-PLAN", at, "key placeholder $seg is not a parameter")
                }
                e.str("policy")?.takeIf { it !in POLICIES }?.let { diagnostics.error("E-CACHE-PLAN", at, "unknown policy $it") }
                e.str("persistence")?.takeIf { it !in PERSISTENCE }?.let { diagnostics.error("E-CACHE-PLAN", at, "unknown persistence $it") }
                val scope = e.str("scope") ?: "user"
                if (scope !in SCOPES) diagnostics.error("E-CACHE-PLAN", at, "unknown scope $scope")
                val offline = e.str("offline")
                if (offline != null) {
                    if (query) diagnostics.error("E-CACHE-PLAN", at, "offline on a query")
                    if (offline !in OFFLINE) diagnostics.error("E-CACHE-PLAN", at, "offline needs an idempotency class (natural | keyed), got $offline")
                }
                if (e.bool("sensitive") && e.str("persistence") == "durable") {
                    diagnostics.error("E-CACHE-PLAN", at, "a sensitive operation cannot be durable")
                }
                out[id] = CacheEntry(key, e.str("policy"), e.str("persistence"), scope, e.bool("sensitive"), invalidates, offline)
            }
            // Every invalidation prefix must reach at least one query key.
            val queryKeys = operations.filter { it.method == HttpMethod.GET }.map { op ->
                out[op.id]?.key ?: derivedKey(op)
            }
            for ((id, entry) in out) {
                entry.invalidates?.forEach { prefix ->
                    val fixed = prefix.takeWhile { !it.startsWith("{") }
                    if (queryKeys.none { k -> k.size >= fixed.size && k.subList(0, fixed.size) == fixed }) {
                        diagnostics.error("E-CACHE-PLAN", "$p#/operations/$id", "invalidates $prefix reaches no query key")
                    }
                }
            }
            return CachePlan(out)
        }

        /** Path segments after `/api/v<n>/`, `{param}` kept as placeholders. */
        fun derivedKey(op: Operation): List<String> {
            val m = Regex("/api/v\\d+/(.*)$").find(op.path)
            val rest = m?.groupValues?.get(1) ?: op.path.trim('/').split('/').drop(1).joinToString("/")
            return rest.split('/').filter { it.isNotEmpty() }
        }
    }
}
