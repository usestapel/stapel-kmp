package stapel.tools.codegen

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import stapel.tools.codegen.ir.Constraints
import stapel.tools.codegen.ir.Diagnostics
import stapel.tools.codegen.ir.EnumDecl
import stapel.tools.codegen.ir.EnumEntry
import stapel.tools.codegen.ir.ObjectDecl
import stapel.tools.codegen.ir.Property
import stapel.tools.codegen.ir.ScalarKind
import stapel.tools.codegen.ir.TypeDecl
import stapel.tools.codegen.ir.TypeRef
import stapel.tools.codegen.ir.UnionDecl
import stapel.tools.codegen.ir.UnionSelector
import stapel.tools.codegen.ir.BlankMode

/** A resolved schema at a use site: the type plus what the site learned about it. */
internal data class Resolved(val type: TypeRef, val nullable: Boolean = false, val blank: BlankMode = BlankMode.NONE)

/**
 * OpenAPI 3.0.3 component schemas → IR declarations (§5.1). Total: an unsupported construct is a
 * diagnostic, never a silent skip.
 */
internal class SchemaParser(
    private val components: JsonObject,
    private val diagnostics: Diagnostics,
) {
    /** Declarations by Kotlin name. */
    val decls: java.util.TreeMap<String, TypeDecl> = java.util.TreeMap()

    /** Union memberships: variant type name → union type names. */
    val implementsOf: MutableMap<String, java.util.TreeSet<String>> = java.util.TreeMap()

    private val kotlinNameOf: Map<String, String> = assignComponentNames()
    private val resolving = HashSet<String>()

    private fun assignComponentNames(): Map<String, String> {
        val base = components.keys.sorted().associateWith { Names.typeName(it) }
        val taken = base.values.groupingBy { it }.eachCount()
        val out = java.util.TreeMap<String, String>()
        for ((component, name) in base) {
            if ((taken[name] ?: 0) > 1) {
                val others = base.filter { it.value == name }.keys.sorted()
                diagnostics.error("E-NAME-COLLISION", "#/components/schemas/$component", "components $others fold to the type name $name")
            }
            val schema = components[component]?.jsonObject
            val isEnum = schema?.containsKey("enum") == true
            val stripped = if (isEnum && name.endsWith("Enum") && name.length > 4) name.removeSuffix("Enum") else name
            out[component] = if (stripped != name && stripped in base.values) name else stripped
        }
        return out
    }

    fun componentName(component: String): String = kotlinNameOf[component] ?: Names.typeName(component)

    private fun component(ref: String): Pair<String, JsonObject> {
        val name = ref.removePrefix("#/components/schemas/")
        val schema = components[name]?.jsonObject ?: error("Unresolved \$ref $ref")
        return name to schema
    }

    /** Parses every component; transparent ones (arrays, scalars, wrappers) produce no declaration. */
    fun parseAll() {
        for (name in components.keys.sorted()) declareComponent(name)
    }

    private fun isBlankEnum(s: JsonObject): Boolean {
        val values = s.arr("enum") ?: return false
        return values.size == 1 && (values[0] is JsonNull || (values[0] as? JsonPrimitive)?.content == "")
    }

    /** Declares [component] when it is a declaration-producing schema; returns its type. */
    fun declareComponent(component: String): Resolved {
        val schema = components[component]?.jsonObject ?: error("No component $component")
        val pointer = "#/components/schemas/$component"
        val name = componentName(component)
        if (decls.containsKey(name)) return Resolved(TypeRef.Named(name))
        if (!resolving.add(component)) return Resolved(TypeRef.Named(name))
        try {
            return when {
                schema.containsKey("properties") -> {
                    declareObject(name, component, schema, pointer)
                    Resolved(TypeRef.Named(name))
                }
                schema.containsKey("enum") && !isBlankEnum(schema) -> {
                    declareEnum(name, component, schema, pointer)
                    Resolved(TypeRef.Named(name))
                }
                schema.containsKey("oneOf") -> resolveOneOf(schema, name, pointer, component)
                schema.containsKey("allOf") && (schema.arr("allOf")?.size ?: 0) > 1 -> resolveAllOf(schema, name, pointer, component)
                else -> resolve(schema, name, pointer)
            }
        } finally {
            resolving.remove(component)
        }
    }

    /** Type of a schema at a use site. [inlineName] names inline declarations. */
    fun resolve(schema: JsonObject, inlineName: String, pointer: String): Resolved {
        if (schema.containsKey("not")) {
            diagnostics.warn("W-NOT-IGNORED", pointer, "`not` is not represented in types")
        }
        schema.str("\$ref")?.let { ref ->
            val (component, target) = component(ref)
            if (isBlankEnum(target)) return Resolved(TypeRef.Scalar(ScalarKind.STRING), nullable = true)
            return declareComponent(component)
        }
        schema.arr("allOf")?.let { parts ->
            if (parts.size == 1) return resolve(parts[0].jsonObject, inlineName, pointer)
            return resolveAllOf(schema, schema.str("title")?.let(Names::typeName) ?: inlineName, pointer, null)
        }
        if (schema.containsKey("oneOf")) return resolveOneOf(schema, schema.str("title")?.let(Names::typeName) ?: inlineName, pointer, null)
        if (schema.containsKey("anyOf")) {
            diagnostics.warn("W-UNTYPED-UNION", pointer, "anyOf is mapped to JsonElement")
            return Resolved(TypeRef.Json)
        }
        if (schema.containsKey("enum") || schema.containsKey("const")) {
            if (isBlankEnum(schema)) return Resolved(TypeRef.Scalar(ScalarKind.STRING), nullable = true)
            val name = schema.str("title")?.let(Names::typeName) ?: inlineName
            val enumSchema = if (schema.containsKey("const")) JsonObject(schema + ("enum" to JsonArray(listOf(schema["const"]!!)))) else schema
            declareEnum(name, name, enumSchema, pointer)
            return Resolved(TypeRef.Named(name))
        }
        return when (schema.str("type")) {
            "object", null -> when {
                schema.containsKey("properties") -> {
                    val name = schema.str("title")?.let(Names::typeName) ?: inlineName
                    declareObject(name, name, schema, pointer)
                    Resolved(TypeRef.Named(name))
                }
                schema["additionalProperties"] is JsonObject && (schema["additionalProperties"] as JsonObject).isNotEmpty() ->
                    Resolved(TypeRef.MapOf(resolve(schema.obj("additionalProperties")!!, inlineName + "Value", "$pointer/additionalProperties").type))
                schema.str("type") == "object" -> Resolved(TypeRef.JsonObj)
                else -> Resolved(TypeRef.Json)
            }
            "array" -> {
                val items = schema.obj("items")
                val item = if (items == null || items.isEmpty()) TypeRef.Json else resolve(items, inlineName + "Item", "$pointer/items").type
                Resolved(TypeRef.ListOf(item))
            }
            "string" -> Resolved(
                TypeRef.Scalar(
                    when (schema.str("format")) {
                        "decimal" -> ScalarKind.DECIMAL
                        "date-time" -> ScalarKind.INSTANT
                        "date" -> ScalarKind.LOCAL_DATE
                        "binary" -> ScalarKind.BINARY
                        else -> ScalarKind.STRING
                    },
                ),
            )
            "integer" -> Resolved(TypeRef.Scalar(if (schema.str("format") == "int64") ScalarKind.LONG else ScalarKind.INT))
            "number" -> Resolved(TypeRef.Scalar(ScalarKind.DOUBLE))
            "boolean" -> Resolved(TypeRef.Scalar(ScalarKind.BOOLEAN))
            else -> {
                diagnostics.error("E-UNSUPPORTED-KEYWORD", pointer, "unsupported type ${schema.str("type")}")
                Resolved(TypeRef.Json)
            }
        }
    }

    private fun resolveAllOf(schema: JsonObject, name: String, pointer: String, component: String?): Resolved {
        val parts = schema.arr("allOf")!!.map { it.jsonObject }
        val merged = LinkedHashMap<String, JsonElement>()
        val required = LinkedHashSet<String>()
        for (part in parts) {
            val target = part.str("\$ref")?.let { component(it).second } ?: part
            if (target.containsKey("oneOf")) {
                diagnostics.warn("W-INTERSECTION-UNTYPED", pointer, "allOf of a union and an object is mapped to JsonObject")
                return Resolved(TypeRef.JsonObj)
            }
            if (!target.containsKey("properties")) {
                diagnostics.warn("W-UNTYPED-UNION", pointer, "allOf part without properties is mapped to JsonObject")
                return Resolved(TypeRef.JsonObj)
            }
            for ((k, v) in target.obj("properties")!!) {
                val previous = merged[k]
                if (previous != null && previous != v) {
                    diagnostics.error("E-NAME-COLLISION", "$pointer/allOf", "property $k has two definitions")
                }
                merged[k] = v
            }
            target.arr("required")?.forEach { required += it.jsonPrimitive.content }
        }
        val flat = JsonObject(
            buildMap {
                put("type", JsonPrimitive("object"))
                put("properties", JsonObject(merged))
                put("required", JsonArray(required.map(::JsonPrimitive)))
                schema["description"]?.let { put("description", it) }
            },
        )
        declareObject(name, component ?: name, flat, pointer)
        return Resolved(TypeRef.Named(name))
    }

    private fun resolveOneOf(schema: JsonObject, name: String, pointer: String, component: String?): Resolved {
        val variants = schema.arr("oneOf")!!.map { it.jsonObject }
        val refs = variants.mapNotNull { it.str("\$ref") }
        if (refs.size == variants.size) {
            val targets = refs.map { component(it) }
            val blank = targets.filter { isBlankEnum(it.second) }
            val enums = targets.filter { it.second.containsKey("enum") && !isBlankEnum(it.second) }
            if (blank.isNotEmpty() && enums.size == 1 && blank.size + 1 == targets.size) {
                val hasNull = blank.any { it.second.arr("enum")!![0] is JsonNull }
                val type = declareComponent(enums[0].first).type
                return Resolved(type, nullable = true, blank = if (hasNull) BlankMode.BLANK_OR_NULL else BlankMode.BLANK_FOR_NULL)
            }
            if (targets.all { it.second.containsKey("properties") || it.second.containsKey("allOf") }) {
                return declareUnion(name, component ?: name, schema, targets.map { it.first }, pointer)
            }
            diagnostics.warn("W-UNTYPED-UNION", pointer, "oneOf of mixed references is mapped to JsonElement")
            return Resolved(TypeRef.Json)
        }
        val types = variants.map { it.str("type") }
        if (types.all { it == "string" } && variants.none { it.containsKey("enum") }) {
            // [string+format, string maxLength 0]: the string type; blank is preserved.
            val first = resolve(variants.first(), name, "$pointer/oneOf/0").type
            return Resolved(first)
        }
        if (types.none { it == null } && types.all { it in setOf("string", "integer", "number", "boolean") } && types.toSet().size >= 2) {
            return Resolved(TypeRef.JsonScalar)
        }
        diagnostics.warn("W-UNTYPED-UNION", pointer, "heterogeneous oneOf ${types.map { it ?: "?" }} is mapped to JsonElement")
        return Resolved(TypeRef.Json)
    }

    private fun objectOf(component: String): JsonObject {
        val s = components[component]!!.jsonObject
        if (s.containsKey("properties")) return s
        val merged = LinkedHashMap<String, JsonElement>()
        val required = mutableListOf<JsonElement>()
        s.arr("allOf")?.forEach { part ->
            val t = part.jsonObject.str("\$ref")?.let { component(it).second } ?: part.jsonObject
            t.obj("properties")?.let { merged.putAll(it) }
            t.arr("required")?.let { required.addAll(it) }
        }
        return JsonObject(mapOf("properties" to JsonObject(merged), "required" to JsonArray(required)))
    }

    /** The enum values a variant's property can take, following `$ref` / single `allOf`; null when unconstrained. */
    private fun enumValues(property: JsonObject?): List<String>? {
        if (property == null) return null
        property.arr("enum")?.let { values -> return values.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content } }
        property.str("\$ref")?.let { return enumValues(component(it).second) }
        property.arr("allOf")?.takeIf { it.size == 1 }?.let { return enumValues(it[0].jsonObject) }
        property["const"]?.let { return listOf(it.jsonPrimitive.content) }
        return null
    }

    private fun declareUnion(name: String, wireName: String, schema: JsonObject, variantComponents: List<String>, pointer: String): Resolved {
        if (decls.containsKey(name)) return Resolved(TypeRef.Named(name))
        val variantNames = variantComponents.map { declareComponent(it).type.let { t -> (t as TypeRef.Named).name } }
        val objects = variantComponents.map { objectOf(it) }
        val selector = selectorFor(schema, variantNames, objects, pointer)
        variantNames.forEach { implementsOf.getOrPut(it) { java.util.TreeSet() } += name }
        decls[name] = UnionDecl(
            name = name,
            wireName = wireName,
            doc = schema.str("description") ?: schema.str("title"),
            pointer = pointer,
            variants = variantNames,
            selector = selector,
            hasUnknown = true,
        )
        return Resolved(TypeRef.Named(name))
    }

    private fun selectorFor(schema: JsonObject, variants: List<String>, objects: List<JsonObject>, pointer: String): UnionSelector {
        val discriminator = schema.obj("discriminator")?.str("propertyName")
        if (discriminator != null) {
            byProperty(discriminator, variants, objects, pointer)?.let { selector ->
                val mapping = schema.obj("discriminator")?.obj("mapping")
                if (mapping != null) {
                    val wireValues = selector.values.toMap()
                    val consistent = mapping.all { (value, ref) ->
                        val target = componentName(ref.jsonPrimitive.content.removePrefix("#/components/schemas/"))
                        wireValues[value] == target
                    }
                    if (!consistent) {
                        diagnostics.warn(
                            "W-DISC-MAPPING",
                            "$pointer/discriminator/mapping",
                            "mapping keys ${mapping.keys.sorted()} are not the wire values of `$discriminator`; the variants' own enums are used",
                        )
                    }
                }
                return selector
            }
            diagnostics.error("E-UNION-AMBIGUOUS", pointer, "discriminator `$discriminator` does not separate the variants")
            return UnionSelector.ByKeys(emptyList(), null)
        }
        // (1) inferred property: present in every variant, pairwise-disjoint enum values.
        val common = objects.map { it.obj("properties")?.keys.orEmpty() }.reduce { a, b -> a intersect b }
        for (candidate in common.sorted()) {
            val selector = byProperty(candidate, variants, objects, pointer, allowFallback = false) ?: continue
            return selector
        }
        // (2) key probes: a key required in one variant and declared in no other.
        val probes = mutableListOf<Pair<String, String>>()
        val fallbacks = mutableListOf<String>()
        objects.forEachIndexed { i, o ->
            val others = objects.filterIndexed { j, _ -> j != i }.flatMap { it.obj("properties")?.keys.orEmpty() }.toSet()
            val required = o.arr("required")?.map { it.jsonPrimitive.content }.orEmpty()
            val ordered = o.obj("properties")?.keys.orEmpty().filter { it in required && it !in others }
            if (ordered.isEmpty()) fallbacks += variants[i] else probes += ordered.first() to variants[i]
        }
        if (fallbacks.size > 1) {
            diagnostics.error("E-UNION-AMBIGUOUS", pointer, "variants ${fallbacks.sorted()} have no distinguishing key")
        }
        return UnionSelector.ByKeys(probes, fallbacks.singleOrNull())
    }

    private fun byProperty(
        property: String,
        variants: List<String>,
        objects: List<JsonObject>,
        pointer: String,
        allowFallback: Boolean = true,
    ): UnionSelector.ByProperty? {
        val values = mutableListOf<Pair<String, String>>()
        val fallbacks = mutableListOf<String>()
        objects.forEachIndexed { i, o ->
            val enum = enumValues(o.obj("properties")?.obj(property))
            if (enum.isNullOrEmpty()) fallbacks += variants[i] else enum.forEach { values += it to variants[i] }
        }
        if (values.map { it.first }.toSet().size != values.size) return null
        if (fallbacks.size > (if (allowFallback) 1 else 0)) return null
        if (values.isEmpty()) return null
        return UnionSelector.ByProperty(property, values, fallbacks.singleOrNull()).also {
            if (it.fallback != null && !allowFallback) diagnostics.warn("W-UNION-FALLBACK", pointer, "fallback ${it.fallback}")
        }
    }

    private fun declareEnum(name: String, wireName: String, schema: JsonObject, pointer: String) {
        if (decls.containsKey(name)) return
        val labels = enumLabels(schema.str("description"))
        val values = schema.arr("enum")!!.mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.content }
        val entries = values.map { v ->
            EnumEntry(Names.enumEntry(v), v, labels[v]?.takeUnless { it == v })
        }
        val seen = entries.groupingBy { it.name }.eachCount().filterValues { it > 1 }
        if (seen.isNotEmpty()) diagnostics.error("E-NAME-COLLISION", pointer, "enum entries fold to the same name: ${seen.keys}")
        decls[name] = EnumDecl(name, wireName, stripEnumList(schema.str("description")), pointer, entries)
    }

    private fun declareObject(name: String, wireName: String, schema: JsonObject, pointer: String) {
        val existing = decls[name]
        if (existing != null) {
            if (existing.pointer != pointer && existing !is ObjectDecl) {
                diagnostics.error("E-NAME-COLLISION", pointer, "type name $name is already used by ${existing.pointer}")
            }
            return
        }
        // Reserve the name first so recursive references resolve.
        decls[name] = ObjectDecl(name, wireName, null, pointer, emptyList())
        val required = schema.arr("required")?.map { it.jsonPrimitive.content }.orEmpty().toSet()
        val props = schema.obj("properties").orEmpty().map { (wire, value) ->
            property(name, wire, value.jsonObject, wire in required, "$pointer/properties/$wire")
        }
        val clash = props.groupingBy { it.name }.eachCount().filterValues { it > 1 }
        if (clash.isNotEmpty()) diagnostics.error("E-NAME-COLLISION", pointer, "properties fold to the same name: ${clash.keys}")
        if (schema["additionalProperties"] != null && schema["additionalProperties"] != JsonPrimitive(false)) {
            diagnostics.warn("W-EXTRAS-DROPPED", pointer, "additionalProperties next to properties are not kept")
        }
        decls[name] = ObjectDecl(
            name = name,
            wireName = wireName,
            doc = schema.str("description") ?: schema.str("title"),
            pointer = pointer,
            properties = props,
        )
    }

    fun property(owner: String, wire: String, schema: JsonObject, required: Boolean, pointer: String): Property {
        val single = schema.arr("allOf")?.takeIf { it.size == 1 }?.get(0)?.jsonObject
        val resolved = resolve(schema, owner + Names.pascal(wire), pointer)
        val target = single ?: schema
        val doc = stripEnumList(schema.str("description"))?.takeIf { it.isNotBlank() } ?: schema.str("title")
        val example = schema["example"]?.let { if (it is JsonPrimitive && it.isString) it.content else canonicalJson(it) }
        val default = schema["default"]?.takeUnless { it is JsonNull }?.let { if (it is JsonPrimitive && it.isString) it.content else canonicalJson(it) }
        val nullable = schema.bool("nullable") || resolved.nullable
        var type = resolved.type
        if (type is TypeRef.JsonScalar && wire in setOf("next_anchor", "prev_anchor", "anchor")) type = TypeRef.Scalar(ScalarKind.CURSOR)
        return Property(
            name = Names.member(wire),
            wireName = wire,
            type = type,
            nullable = nullable,
            required = required,
            readOnly = schema.bool("readOnly"),
            writeOnly = schema.bool("writeOnly"),
            doc = doc,
            constraints = constraintsOf(schema, target),
            example = example,
            default = default,
            blank = resolved.blank,
            pointer = pointer,
        )
    }

    private fun constraintsOf(schema: JsonObject, target: JsonObject): Constraints {
        // For a [string, string maxLength 0] union the real limits are on the first variant.
        val first = schema.arr("oneOf")?.firstOrNull()?.jsonObject
        fun pick(key: String): JsonElement? = schema[key] ?: first?.get(key) ?: target[key].takeIf { target !== schema && !target.containsKey("\$ref") }
        return Constraints(
            maxLength = (pick("maxLength") as? JsonPrimitive)?.content?.toIntOrNull(),
            minLength = (pick("minLength") as? JsonPrimitive)?.content?.toIntOrNull(),
            pattern = (pick("pattern") as? JsonPrimitive)?.content,
            minimum = (pick("minimum") as? JsonPrimitive)?.content,
            maximum = (pick("maximum") as? JsonPrimitive)?.content,
            maxItems = (pick("maxItems") as? JsonPrimitive)?.content?.toIntOrNull(),
        )
    }

    companion object {
        private val ENUM_LINE = Regex("^\\* `([^`]*)` - (.*)$")

        /** drf-spectacular's ``* `V` - label`` lines → value to label. */
        fun enumLabels(description: String?): Map<String, String> =
            description.orEmpty().lines().mapNotNull { ENUM_LINE.matchEntire(it.trim()) }
                .associate { it.groupValues[1] to it.groupValues[2].trim() }

        /** The description without drf-spectacular's enum list. */
        fun stripEnumList(description: String?): String? =
            description?.lines()?.filterNot { ENUM_LINE.matches(it.trim()) }?.joinToString("\n")?.trim()?.takeIf { it.isNotEmpty() }
    }
}
