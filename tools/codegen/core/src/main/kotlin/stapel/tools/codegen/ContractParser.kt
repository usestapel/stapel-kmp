package stapel.tools.codegen

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import stapel.tools.codegen.ir.ApiModel
import stapel.tools.codegen.ir.AuthRequirement
import stapel.tools.codegen.ir.Body
import stapel.tools.codegen.ir.Captcha
import stapel.tools.codegen.ir.ContractStamp
import stapel.tools.codegen.ir.Diagnostics
import stapel.tools.codegen.ir.EnumDecl
import stapel.tools.codegen.ir.ErrorBinding
import stapel.tools.codegen.ir.ErrorCode
import stapel.tools.codegen.ir.Example
import stapel.tools.codegen.ir.HttpMethod
import stapel.tools.codegen.ir.ModelRole
import stapel.tools.codegen.ir.ObjectDecl
import stapel.tools.codegen.ir.Operation
import stapel.tools.codegen.ir.PageInfo
import stapel.tools.codegen.ir.PageStyle
import stapel.tools.codegen.ir.Pagination
import stapel.tools.codegen.ir.Parameter
import stapel.tools.codegen.ir.Property
import stapel.tools.codegen.ir.ScalarKind
import stapel.tools.codegen.ir.Success
import stapel.tools.codegen.ir.TypeDecl
import stapel.tools.codegen.ir.TypeRef
import stapel.tools.codegen.ir.UnionDecl

/** Contract set → [ApiModel]: schemas, operations, errors, overlay, side files (§3–§7, §13). */
class ContractParser(private val set: ContractSet) {
    private val diagnostics = Diagnostics()
    private val schema = set.schema
    private val profile: Int = schema.obj("info")?.int("x-stapel-contract") ?: 1
    private val module: String = set.brick
    private val schemas = SchemaParser(schema.obj("components")?.obj("schemas") ?: JsonObject(emptyMap()), diagnostics)
    private val errorTable: List<ErrorCode> = parseErrors()
    private val errorCodes: Set<String> = errorTable.map { it.code }.toSet()

    fun parse(): ApiModel {
        checkTopLevelKeywords()
        schemas.parseAll()
        val operations = parseOperations()
        checkOverlay(operations)
        val types = finalizeTypes(operations)
        val finalOps = operations.map { op -> remapRequestTypes(op) }
            .map { attachPagination(it, types) }
        documentationDiagnostics(types, finalOps)
        val pin = set.pin
        return ApiModel(
            brick = module,
            stamp = ContractStamp(
                module = pin?.module ?: "stapel-$module",
                version = pin?.version ?: "unpinned",
                ref = pin?.ref ?: "0".repeat(40),
                schemaSha256 = sha256(set.schemaBytes),
                profile = profile,
            ),
            operations = finalOps.sortedBy { it.id },
            types = types.values.sortedBy { it.name },
            errors = errorTable,
            diagnostics = diagnostics,
        )
    }

    private fun checkTopLevelKeywords() {
        val known = setOf("openapi", "info", "paths", "components", "servers", "tags", "security", "externalDocs")
        schema.keys.filter { it !in known && !it.startsWith("x-") }.forEach {
            diagnostics.error("E-UNSUPPORTED-KEYWORD", "#/$it", "unknown top-level keyword")
        }
        val version = schema.str("openapi")
        if (version == null || !version.startsWith("3.0")) {
            diagnostics.error("E-UNSUPPORTED-KEYWORD", "#/openapi", "only OpenAPI 3.0.x is supported, got $version")
        }
    }

    // ---- errors.json -------------------------------------------------------------------------------

    private fun parseErrors(): List<ErrorCode> {
        val overlayTypes = set.overlay.operations.values.flatMap { it.paramTypes.entries }.associate { it.key to it.value }
        return set.errors.orEmpty().map { it.jsonObject }.map { e ->
            val declared = e.obj("param_types")?.mapValues { it.value.jsonPrimitive.content }.orEmpty()
            val params = e.arr("params")?.map { it.jsonPrimitive.content }.orEmpty()
            val pointer = "errors.json#${e.str("code")}"
            for ((name, type) in declared) {
                if (type !in ErrorCode.PARAM_TYPES) {
                    diagnostics.error("E-ERR-PARAM-TYPE", pointer, "param $name has type '$type'; known: ${ErrorCode.PARAM_TYPES.joinToString()}")
                }
                if (name !in params) diagnostics.error("E-ERR-PARAM-TYPE", pointer, "param_types names $name, which params does not list")
            }
            ErrorCode(
                code = e.str("code")!!,
                status = e.int("status") ?: 0,
                params = params,
                paramTypes = params.associateWith { p -> declared[p] ?: overlayTypes[p] ?: "string" },
                remediation = e.str("remediation") ?: "",
                en = e.str("en") ?: "",
                owner = e.str("owner") ?: "",
            )
        }.sortedBy { it.code }
    }

    // ---- operations ----------------------------------------------------------------------------------

    private data class RawOp(val id: String, val method: HttpMethod, val path: String, val op: JsonObject, val pathParams: List<JsonObject>)

    private fun parseOperations(): List<Operation> {
        val raw = mutableListOf<RawOp>()
        for ((path, itemEl) in schema.obj("paths").orEmpty()) {
            val item = itemEl.jsonObject
            val shared = item.arr("parameters")?.map { it.jsonObject }.orEmpty()
            for ((method, opEl) in item) {
                val m = runCatching { HttpMethod.valueOf(method.uppercase()) }.getOrNull() ?: continue
                val op = opEl.jsonObject
                val id = op.str("operationId") ?: run {
                    diagnostics.error("E-UNSUPPORTED-KEYWORD", "#/paths/$path/$method", "operation without operationId")
                    return@run null
                } ?: continue
                raw += RawOp(id, m, path, op, shared)
            }
        }
        val stems = raw.groupingBy { Names.stem(it.id, module) }.eachCount()
        val ops = raw.sortedBy { it.id }.mapNotNull { buildOperation(it, stems) }
        val collisions = ops.groupBy { it.name }.filterValues { it.size > 1 }
        collisions.forEach { (name, list) ->
            diagnostics.error("E-NAME-COLLISION", list.first().pointer, "operations ${list.map { it.id }} map to the function name $name")
        }
        return ops
    }

    private fun pointerOf(path: String, method: HttpMethod) =
        "#/paths/" + path.replace("~", "~0").replace("/", "~1") + "/" + method.name.lowercase()

    private fun buildOperation(raw: RawOp, stems: Map<String, Int>): Operation? {
        val overlay = set.overlay.operations[raw.id]
        val pointer = pointerOf(raw.path, raw.method)
        if (overlay?.exclude == true) return null
        if ("/internal/" in raw.path) return null
        val op = raw.op
        val params = (raw.pathParams + op.arr("parameters")?.map { it.jsonObject }.orEmpty())
            .associateBy { (it.str("in") ?: "") + ":" + (it.str("name") ?: "") }.values
        val pathOrder = Regex("\\{([^}]+)}").findAll(raw.path).map { it.groupValues[1] }.toList()
        val name = overlay?.alias ?: Names.operationName(raw.id, module, pathOrder) { (stems[it] ?: 0) > 1 }
        val opPascal = Names.pascal(name)

        val parameters = params.mapNotNull { p ->
            val location = when (p.str("in")) {
                "path" -> Parameter.Location.PATH
                "query" -> Parameter.Location.QUERY
                "header" -> Parameter.Location.HEADER
                else -> return@mapNotNull null
            }
            val wire = p.str("name")!!
            val ps = p.obj("schema") ?: JsonObject(emptyMap())
            val type = schemas.resolve(ps, opPascal + Names.pascal(wire.removePrefix("X-").removePrefix("x-")), "$pointer/parameters/$wire").type
            Parameter(
                name = Names.member(if (location == Parameter.Location.HEADER) wire.removePrefix("X-").removePrefix("x-") else wire),
                wireName = wire,
                location = location,
                type = type,
                required = p.bool("required") || location == Parameter.Location.PATH,
                doc = p.str("description")?.takeIf { it.isNotBlank() },
                pointer = "$pointer/parameters/$wire",
            )
        }.sortedWith(compareBy({ it.location.ordinal }, { if (it.location == Parameter.Location.PATH) pathOrder.indexOf(it.wireName) else 0 }, { it.wireName }))

        val body = op.obj("requestBody")?.let { rb -> parseBody(rb, opPascal, "$pointer/requestBody") }
        val success = parseSuccess(op.obj("responses").orEmptyObj(), opPascal, pointer)
        val (summary, description, permissions) = splitDoc(op)
        val security = op.arr("security")
        var auth = when {
            security == null -> {
                diagnostics.warn("W-SECURITY-ABSENT", pointer, "no security declaration")
                AuthRequirement.OPTIONAL
            }
            security.any { it.jsonObject.isEmpty() } && security.size > 1 -> AuthRequirement.OPTIONAL
            security.all { it.jsonObject.isEmpty() } -> AuthRequirement.NONE
            else -> AuthRequirement.REQUIRED
        }
        overlay?.auth?.let { a ->
            if (profile >= 2 && auth != AuthRequirement.REQUIRED) {
                diagnostics.error("E-OVERLAY-REDUNDANT", pointer, "overlay sets auth=$a but the contract already declares $auth")
            }
            auth = AuthRequirement.valueOf(a.uppercase())
        }
        return Operation(
            id = raw.id,
            name = name,
            method = raw.method,
            path = raw.path,
            tag = op.arr("tags")?.firstOrNull()?.jsonPrimitive?.content ?: "",
            summary = summary,
            description = description,
            permissions = permissions,
            auth = auth,
            parameters = parameters,
            body = body,
            success = success,
            errors = errorBinding(op, overlay, pointer),
            pagination = null,
            verificationScope = overlaid(op.obj("x-stapel-verification")?.str("scope"), overlay?.verification, pointer, "verification"),
            captcha = overlaid(
                op.obj("x-stapel-captcha")?.let { c -> Captcha(c.str("action") ?: "", c.obj("token")?.str("header"), c.obj("token")?.str("field")) },
                overlay?.captcha?.let { (a, h, f) -> Captcha(a, h, f) },
                pointer,
                "captcha",
            ),
            capability = when (val c = op["x-stapel-capability"]) {
                is JsonPrimitive -> c.content
                is JsonObject -> c.str("key") ?: c.str("capability")
                else -> null
            },
            flows = flowsOf(raw, op),
            gatedBy = gatesOf(raw.id),
            requestExample = op.obj("requestBody")?.obj("content")?.obj("application/json")?.let(::firstExample),
            responseExample = op.obj("responses").orEmptyObj().entries.filter { it.key.startsWith("2") }
                .firstNotNullOfOrNull { it.value.jsonObject.obj("content")?.obj("application/json")?.let(::firstExample) },
            pointer = pointer,
        )
    }

    /** The contract's value, else the overlay's; both present is `E-OVERLAY-REDUNDANT`. */
    private fun <T> overlaid(contract: T?, overlay: T?, pointer: String, what: String): T? {
        if (contract != null && overlay != null) {
            diagnostics.error("E-OVERLAY-REDUNDANT", pointer, "overlay sets $what but the contract already declares it")
        }
        return contract ?: overlay
    }

    private fun firstExample(media: JsonObject): Example? {
        media.obj("examples")?.entries?.firstOrNull()?.let { (key, ex) ->
            val value = ex.jsonObject["value"] ?: return null
            return Example(ex.jsonObject.str("summary") ?: key, canonicalJson(value))
        }
        return media["example"]?.let { Example(null, canonicalJson(it)) }
    }

    private fun parseBody(rb: JsonObject, opPascal: String, pointer: String): Body? {
        val content = rb.obj("content") ?: return null
        val required = rb.bool("required")
        content.obj("application/json")?.let { media ->
            val s = media.obj("schema") ?: return null
            val r = schemas.resolve(s, opPascal + "Body", "$pointer/content/application~1json/schema")
            val doc = s.str("\$ref")?.let { ref ->
                schema.obj("components")?.obj("schemas")?.obj(ref.removePrefix("#/components/schemas/"))?.str("description")
            } ?: s.str("description")
            return Body.Json(r.type, required, doc)
        }
        val multipart = content.containsKey("multipart/form-data")
        val media = content.obj("multipart/form-data") ?: content.obj("application/x-www-form-urlencoded") ?: run {
            diagnostics.error("E-UNSUPPORTED-KEYWORD", pointer, "request media types ${content.keys} are not supported")
            return null
        }
        var s = media.obj("schema") ?: return null
        s.str("\$ref")?.let { s = schema.obj("components")!!.obj("schemas")!!.obj(it.removePrefix("#/components/schemas/"))!! }
        val requiredProps = s.arr("required")?.map { it.jsonPrimitive.content }.orEmpty().toSet()
        val parts = s.obj("properties").orEmpty().map { (wire, ps) ->
            schemas.property(opPascal + "Body", wire, ps.jsonObject, wire in requiredProps, "$pointer/properties/$wire")
        }.filterNot { it.readOnly }
        return Body.Parts(parts, multipart, required)
    }

    private fun parseSuccess(responses: JsonObject, opPascal: String, pointer: String): Success {
        val ok = responses.entries.filter { it.key.startsWith("2") }.sortedBy { it.key }
        if (ok.isEmpty()) {
            val redirects = responses.keys.filter { it.startsWith("3") }.map { it.toInt() }
            if (redirects.isNotEmpty()) return Success.Redirect(redirects)
            diagnostics.warn("W-BODY-UNDECLARED", pointer, "no 2xx response declared")
            return Success.Empty(listOf(200))
        }
        val statuses = ok.map { it.key.toInt() }
        val first = ok.first().value.jsonObject
        val content = first.obj("content")
        if (content == null || content.isEmpty()) {
            if (statuses.none { it == 204 || it == 202 }) {
                diagnostics.warn("W-BODY-UNDECLARED", pointer, "status ${statuses.first()} declares no body")
            }
            return Success.Empty(statuses)
        }
        val json = content.obj("application/json")
        if (json != null) {
            val s = json.obj("schema")
            if (s == null || s.isEmpty()) {
                diagnostics.warn("W-BODY-UNDECLARED", pointer, "JSON body without schema")
                return Success.Json(TypeRef.Json, statuses)
            }
            val types = ok.mapNotNull { it.value.jsonObject.obj("content")?.obj("application/json")?.obj("schema") }.toSet()
            if (types.size > 1) diagnostics.warn("W-MULTI-SUCCESS", pointer, "2xx statuses declare different schemas; the first is used")
            val type = schemas.resolve(s, opPascal + "Response", "$pointer/responses/${statuses.first()}").type
            if (type == TypeRef.JsonObj && !s.containsKey("\$ref")) {
                diagnostics.warn("W-BODY-UNDECLARED", pointer, "untyped object body")
            }
            return Success.Json(type, statuses)
        }
        val media = content.keys.toList()
        val binary = media.filter { it == "application/octet-stream" || it == "application/zip" || it.startsWith("image/") || it == "application/pdf" }
        if (binary.isNotEmpty()) return Success.Binary(media, statuses)
        return Success.Text(media.first(), statuses)
    }

    private fun splitDoc(op: JsonObject): Triple<String?, String?, List<String>> {
        val summary = op.str("summary")?.trim()?.takeIf { it.isNotEmpty() }
        var description = op.str("description")?.trim().orEmpty()
        var permissions = emptyList<String>()
        val perm = Regex("\\n*\\*\\*Permissions:\\*\\*\\s*`([^`]*)`\\s*$").find(description)
        if (perm != null) {
            permissions = perm.groupValues[1].split(Regex("[,&|]| and | or ")).map { it.trim() }.filter { it.isNotEmpty() }
            description = description.substring(0, perm.range.first).trim()
        }
        return Triple(summary, description.takeIf { it.isNotEmpty() }, permissions)
    }

    private fun errorBinding(op: JsonObject, overlay: OverlayOperation?, pointer: String): ErrorBinding {
        val responses = op.obj("responses").orEmptyObj()
        val errorStatuses = responses.keys.filter { it.length == 3 && (it[0] == '4' || it[0] == '5') }.map { it.toInt() }.sorted()
        val declared = sortedSetOf<String>()
        responses.values.forEach { r -> r.jsonObject.arr("x-stapel-errors")?.forEach { declared += it.jsonPrimitive.content } }
        val overlayCodes = overlay?.errors?.values?.flatten()?.toSortedSet()
        if (overlayCodes != null && declared.isNotEmpty()) {
            diagnostics.error("E-OVERLAY-REDUNDANT", pointer, "overlay binds errors the contract already binds (x-stapel-errors)")
        }
        val codes = if (declared.isNotEmpty()) declared else overlayCodes
        if (codes == null) return ErrorBinding.Unbound(errorStatuses)
        codes.filter { it !in errorCodes }.forEach {
            diagnostics.error("E-ERR-UNKNOWN-CODE", pointer, "bound code $it is not in errors.json")
        }
        val bound = codes.filter { it in errorCodes }
        val statusesWithCodes = bound.mapNotNull { c -> errorTable.firstOrNull { it.code == c }?.status }.toSet()
        val unbound = errorStatuses.filter { it !in statusesWithCodes }
        unbound.filter { status ->
            val ref = responses.obj(status.toString())?.obj("content")?.obj("application/json")?.obj("schema")?.str("\$ref")
            ref?.endsWith("/StapelError") == true
        }.forEach { diagnostics.warn("W-ERR-STATUS-UNBOUND", "$pointer/responses/$it", "status $it has a StapelError body and no bound code") }
        return ErrorBinding.Bound(bound, if (declared.isNotEmpty()) "contract" else "overlay (${overlay?.fixes ?: "C2"})", unbound)
    }

    private fun flowsOf(raw: RawOp, op: JsonObject): List<String> {
        val out = sortedSetOf<String>()
        set.flows.orEmpty().forEach { flowEl ->
            val flow = flowEl.jsonObject
            val id = flow.str("id") ?: return@forEach
            flow.arr("steps").orEmpty().forEach { stepEl ->
                val step = stepEl.jsonObject
                step.arr("endpoints").orEmpty().forEach { epEl ->
                    val ep = epEl.jsonObject
                    if (ep.str("method")?.uppercase() == raw.method.name && ep.str("path") == raw.path) {
                        out += "$id step ${step.int("order") ?: 0}"
                    }
                }
            }
        }
        op.arr("x-stapel-flows")?.forEach { f ->
            val id = f.jsonPrimitive.content
            if (out.none { it.startsWith("$id ") }) out += id
        }
        return out.toList()
    }

    private fun gatesOf(id: String): List<String> =
        set.capabilities?.arr("axes").orEmpty().mapNotNull { axisEl ->
            val axis = axisEl.jsonObject
            val ops = axis.obj("gates")?.arr("operations").orEmpty().map { it.jsonPrimitive.content }
            axis.str("key")?.takeIf { id in ops }
        }.sorted()

    private fun checkOverlay(operations: List<Operation>) {
        val allIds = schema.obj("paths").orEmpty().values.flatMap { item ->
            item.jsonObject.values.mapNotNull { (it as? JsonObject)?.str("operationId") }
        }.toSet()
        set.overlay.operations.keys.filter { it !in allIds }.forEach {
            diagnostics.error("E-OVERLAY-STALE", "overlay.json#/operations/$it", "operation $it is not in the pinned contract")
        }
        val pin = set.pin
        if (pin != null && set.overlay.forRef != null && set.overlay.forRef != pin.ref) {
            diagnostics.warn("W-OVERLAY-REVIEW", "overlay.json#/for_ref", "overlay was written for ${set.overlay.forRef}, pin is ${pin.ref}")
        }
        val aliases = set.overlay.operations.mapNotNull { it.value.alias }
        aliases.groupingBy { it }.eachCount().filterValues { it > 1 }.forEach { (a, _) ->
            diagnostics.error("E-NAME-COLLISION", "overlay.json", "alias $a is used twice")
        }
        if (operations.isEmpty()) diagnostics.warn("W-EMPTY-CONTRACT", "#/paths", "no client operations")
    }

    // ---- request/response split, reachability, pagination ------------------------------------------

    private fun refsOf(type: TypeRef): List<String> = when (type) {
        is TypeRef.Named -> listOf(type.name)
        is TypeRef.ListOf -> refsOf(type.item)
        is TypeRef.MapOf -> refsOf(type.value)
        else -> emptyList()
    }

    private fun reach(roots: List<String>, decls: Map<String, TypeDecl>): Set<String> {
        val seen = java.util.TreeSet<String>()
        val queue = ArrayDeque(roots)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (!seen.add(n)) continue
            when (val d = decls[n]) {
                is ObjectDecl -> d.properties.forEach { queue += refsOf(it.type) }
                is UnionDecl -> queue += d.variants
                else -> Unit
            }
        }
        return seen
    }

    private fun bodyRefs(op: Operation): List<String> = when (val b = op.body) {
        is Body.Json -> refsOf(b.type)
        is Body.Parts -> b.parts.flatMap { refsOf(it.type) }
        null -> emptyList()
    }

    private fun successRefs(op: Operation): List<String> = (op.success as? Success.Json)?.let { refsOf(it.type) }.orEmpty()

    /** Names of request twins, by response model name. */
    private val requestTwin = java.util.TreeMap<String, String>()

    private fun finalizeTypes(operations: List<Operation>): java.util.TreeMap<String, TypeDecl> {
        val decls = java.util.TreeMap<String, TypeDecl>(schemas.decls)
        val requestSide = reach(operations.flatMap(::bodyRefs) + operations.flatMap { op ->
            op.parameters.flatMap { refsOf(it.type) }
        }, decls)
        val responseSide = reach(operations.flatMap(::successRefs), decls)
        val unionsInRequests = requestSide.filter { decls[it] is UnionDecl }.toSet()

        // Fixpoint: an object needs a request twin when its request view differs from its response view,
        // or when it references (on the request side) a type that has a twin.
        val split = java.util.TreeSet<String>()
        var changed = true
        while (changed) {
            changed = false
            for (n in requestSide) {
                val d = decls[n] as? ObjectDecl ?: continue
                if (n in split || n !in responseSide || d.name.startsWith("Patched")) continue
                val differs = d.properties.any { it.readOnly || it.writeOnly }
                val nested = d.properties.any { p -> refsOf(p.type).any { it in split } }
                if (differs || nested) {
                    if (schemas.implementsOf.containsKey(n) && n in unionsInRequests.flatMap { (decls[it] as UnionDecl).variants }) {
                        diagnostics.warn("W-UNION-REQUEST-SPLIT", d.pointer, "union variant $n differs between request and response; the response shape is used")
                        continue
                    }
                    split += n
                    changed = true
                }
            }
        }
        for (n in split) {
            var twin = if (n.endsWith("Request")) n.removeSuffix("Request") + "Write" + "Request" else n + "Request"
            if (twin in decls) twin = n + "Input"
            if (twin in decls) diagnostics.error("E-NAME-COLLISION", decls[n]!!.pointer, "request twin $twin of $n collides")
            requestTwin[n] = twin
        }
        fun remap(t: TypeRef): TypeRef = when (t) {
            is TypeRef.Named -> requestTwin[t.name]?.let { TypeRef.Named(it) } ?: t
            is TypeRef.ListOf -> TypeRef.ListOf(remap(t.item))
            is TypeRef.MapOf -> TypeRef.MapOf(remap(t.value))
            else -> t
        }
        val out = java.util.TreeMap<String, TypeDecl>()
        for ((n, d) in decls) {
            val used = n in requestSide || n in responseSide
            if (!used) continue
            when (d) {
                is ObjectDecl -> {
                    val implements = schemas.implementsOf[n]?.toList().orEmpty()
                    val inReq = n in requestSide
                    val inResp = n in responseSide
                    when {
                        n.startsWith("Patched") && inReq && !inResp -> out[n] = d.copy(
                            role = ModelRole.PATCH,
                            properties = d.properties.filterNot { it.readOnly }.map { it.copy(type = remap(it.type)) },
                            implements = implements,
                        )
                        n in split -> {
                            out[n] = d.copy(role = ModelRole.RESPONSE, properties = d.properties.filterNot { it.writeOnly }, implements = implements)
                            val twin = requestTwin.getValue(n)
                            out[twin] = d.copy(
                                name = twin,
                                role = ModelRole.REQUEST,
                                properties = d.properties.filterNot { it.readOnly }.map { it.copy(type = remap(it.type)) },
                                implements = emptyList(),
                            )
                        }
                        inReq && !inResp -> out[n] = d.copy(
                            role = ModelRole.REQUEST,
                            properties = d.properties.filterNot { it.readOnly }.map { it.copy(type = remap(it.type)) },
                            implements = implements,
                        )
                        inResp && !inReq -> out[n] = d.copy(role = ModelRole.RESPONSE, properties = d.properties.filterNot { it.writeOnly }, implements = implements)
                        else -> out[n] = d.copy(role = ModelRole.BOTH, implements = implements)
                    }
                }
                is UnionDecl -> out[n] = d.copy(hasUnknown = n in responseSide)
                is EnumDecl -> out[n] = d
            }
        }
        // Pagination envelopes.
        for ((n, d) in out.entries.toList()) {
            if (d is ObjectDecl && d.role != ModelRole.REQUEST && d.role != ModelRole.PATCH) {
                pageInfo(d, out)?.let { out[n] = d.copy(page = it, generatedDoc = PAGE_DOCS.getValue(it.style)) }
            }
        }
        return out
    }

    private fun pageInfo(d: ObjectDecl, decls: Map<String, TypeDecl>): PageInfo? {
        val props = d.properties.associateBy { it.wireName }
        fun listItem(wire: String): TypeRef? = (props[wire]?.takeIf { it.required && !it.nullable }?.type as? TypeRef.ListOf)?.item
        val declared = set.schema.obj("components")?.obj("schemas")?.obj(d.wireName)?.obj("x-stapel-pagination")?.str("style")
        val structural = when {
            listItem("items") != null && "next_anchor" in props && "has_next" in props -> PageStyle.ANCHOR
            listItem("results") != null && "pagination" in props -> {
                val inner = (props["pagination"]?.type as? TypeRef.Named)?.let { decls[it.name] as? ObjectDecl }
                if (inner?.properties?.map { it.wireName }?.containsAll(listOf("page", "has_next", "has_previous", "total_count")) == true) PageStyle.REVISION_PAGE else null
            }
            listItem("results") != null && setOf("count", "limit", "offset").all { it in props } -> PageStyle.LIMIT_OFFSET
            listItem("results") != null && "count" in props && "next" in props &&
                (props["next"]?.type as? TypeRef.Scalar)?.kind == ScalarKind.INT -> PageStyle.PAGE_NUMBER
            else -> null
        }
        val style = when (declared) {
            null -> structural
            "anchor" -> PageStyle.ANCHOR
            "page" -> PageStyle.PAGE_NUMBER
            "revision_page" -> PageStyle.REVISION_PAGE
            "offset" -> PageStyle.LIMIT_OFFSET
            else -> structural
        }
        if (declared != null && structural != style) {
            diagnostics.warn("W-PAGINATION-UNRECOGNIZED", d.pointer, "declared style $declared does not match the envelope's fields")
            return null
        }
        if (style == null) {
            if (("items" in props && "has_next" in props) || ("results" in props && "count" in props)) {
                diagnostics.warn("W-PAGINATION-UNRECOGNIZED", d.pointer, "looks like a page envelope but matches no known style")
            }
            return null
        }
        val item = listItem(if (style == PageStyle.ANCHOR) "items" else "results") ?: return null
        return PageInfo(style, item)
    }

    private fun remapRequestTypes(op: Operation): Operation {
        fun remap(t: TypeRef): TypeRef = when (t) {
            is TypeRef.Named -> requestTwin[t.name]?.let { TypeRef.Named(it) } ?: t
            is TypeRef.ListOf -> TypeRef.ListOf(remap(t.item))
            is TypeRef.MapOf -> TypeRef.MapOf(remap(t.value))
            else -> t
        }
        val body = when (val b = op.body) {
            is Body.Json -> b.copy(type = remap(b.type))
            is Body.Parts -> b.copy(parts = b.parts.map { it.copy(type = remap(it.type)) })
            null -> null
        }
        return op.copy(body = body)
    }

    private fun attachPagination(op: Operation, decls: Map<String, TypeDecl>): Operation {
        val type = (op.success as? Success.Json)?.type as? TypeRef.Named ?: return op
        val page = (decls[type.name] as? ObjectDecl)?.page ?: return op
        val names = op.parameters.filter { it.location == Parameter.Location.QUERY }.map { it.wireName }.toSet()
        val cursor = when (page.style) {
            PageStyle.ANCHOR -> "anchor".takeIf { it in names }
            PageStyle.PAGE_NUMBER, PageStyle.REVISION_PAGE -> "page".takeIf { it in names }
            PageStyle.LIMIT_OFFSET -> "offset".takeIf { it in names }
        }
        if (cursor == null) {
            diagnostics.warn("W-PAGINATION-UNRECOGNIZED", op.pointer, "returns a ${page.style} envelope without its cursor parameter")
            return op
        }
        return op.copy(pagination = Pagination(page.style, cursor, page.itemType))
    }

    // ---- documentation ratchet -------------------------------------------------------------------------

    private fun documentationDiagnostics(types: Map<String, TypeDecl>, ops: List<Operation>) {
        for (op in ops) {
            if (op.summary == null && op.description == null) {
                diagnostics.warn("W-DOC-OPERATION", op.pointer, "operation ${op.id} has no summary or description")
            }
            op.parameters.filter { it.doc == null }.forEach {
                diagnostics.warn("W-DOC-PARAM", it.pointer, "parameter ${it.wireName} of ${op.id} has no description")
            }
        }
        for (d in types.values) {
            val origin = d.pointer
            if (d.doc == null && (d as? ObjectDecl)?.generatedDoc == null) {
                // A request twin shares its component's pointer: count the component once.
                if (!(d is ObjectDecl && d.role == ModelRole.REQUEST && requestTwin.containsValue(d.name))) {
                    diagnostics.warn("W-DOC-TYPE", origin, "type ${d.name} has no description")
                }
            }
            if (d is ObjectDecl && !(d.role == ModelRole.REQUEST && requestTwin.containsValue(d.name))) {
                d.properties.filter { it.doc == null }.forEach {
                    diagnostics.warn("W-DOC-PROPERTY", it.pointer, "property ${d.name}.${it.wireName} has no description")
                }
            }
        }
        val money = Regex("(^|_)(price|amount|total|cost|fee|balance|rate)(_|$)")
        for (d in types.values) {
            if (d !is ObjectDecl) continue
            d.properties.filter {
                (it.type as? TypeRef.Scalar)?.kind == ScalarKind.DOUBLE && money.containsMatchIn(it.wireName) && !isRatio(it.constraints)
            }.forEach {
                diagnostics.error("E-MONEY-AS-DOUBLE", it.pointer, "money-like number ${d.name}.${it.wireName} must be format: decimal at the source")
            }
        }
    }

    /** A number the source bounds to [0, 1] (`minimum >= 0`, `maximum <= 1`) is a ratio, never money, whatever its name. */
    private fun isRatio(c: stapel.tools.codegen.ir.Constraints): Boolean {
        val min = c.minimum?.toBigDecimalOrNull() ?: return false
        val max = c.maximum?.toBigDecimalOrNull() ?: return false
        return min >= java.math.BigDecimal.ZERO && max <= java.math.BigDecimal.ONE
    }

    companion object {
        val PAGE_DOCS: Map<PageStyle, String> = mapOf(
            PageStyle.ANCHOR to "Anchor page envelope: `items`, `next_anchor`, `prev_anchor`, `has_next`, `has_prev`, `count`.",
            PageStyle.PAGE_NUMBER to "Page-number envelope: `results`, `count`, `next`.",
            PageStyle.REVISION_PAGE to "Revision page envelope: `results`, `pagination`, `revisions`.",
            PageStyle.LIMIT_OFFSET to "Limit/offset envelope: `results`, `count`, `limit`, `offset`.",
        )

    }
}
