package stapel.tools.codegen

import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.SET
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Typed analytics events of a brick from its `flows.json` (ruling K10): `<Brick>Events` with one
 * constant per documented flow id (what a `FlowMachine` is constructed with, so its
 * `flow.<id>.<step>` events match the backend's funnels), and `events.json` at the generated root —
 * the same shape as the web's `gen:events` output (`flows[]` with `flow`, `event`, `titleKey`,
 * `descriptionKey`, the closed `phase` prop, `steps`), which the scaffold concatenates into the
 * app's analytics registry. Nothing is emitted for a brick without flows.
 */
object FlowEvents {
    /** The registry file, at the generated root next to the name lock. */
    const val FILE: String = "events.json"

    /** The closed phase set of a flow step (stapel.runtime.FlowStepPhase, the web's FLOW_PHASES). */
    val PHASES: List<String> = listOf("started", "completed", "failed")

    private data class Flow(val id: String, val title: String?, val titleKey: String?, val descriptionKey: String?, val steps: List<JsonObject>)

    private fun flows(raw: JsonArray?): List<Flow> = raw.orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = o.text("id") ?: return@mapNotNull null
        Flow(id, o.text("title"), o.text("title_key"), o.text("description_key"), (o["steps"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject })
    }.sortedBy { it.id }

    /** `<Brick>Events`, or null when the brick documents no flow. */
    fun typeSpec(brick: String, raw: JsonArray?): TypeSpec? {
        val flows = flows(raw)
        if (flows.isEmpty()) return null
        val type = TypeSpec.objectBuilder(Names.pascal(brick) + "Events")
            .addKdoc(
                "Analytics events of the `%L` brick, generated from its `flows.json`: one flow id per documented flow. " +
                    "A `FlowMachine` constructed with one of these ids reports `flow.<id>.<step>` with `phase` " +
                    "(started, completed, failed), the funnels the backend defines.",
                brick,
            )
        val used = mutableSetOf<String>()
        flows.forEach { f ->
            var name = constantName(f.id.removePrefix("$brick."))
            while (!used.add(name)) name += "_"
            type.addProperty(
                PropertySpec.builder(name, STRING, KModifier.CONST)
                    .initializer("%S", f.id)
                    .addKdoc("Flow `%L`%L.", f.id, f.title?.let { " — ${it.replace("%", "%%")}" } ?: "")
                    .build(),
            )
        }
        type.addProperty(
            PropertySpec.builder("flows", LIST.parameterizedBy(STRING))
                .initializer(CodeBlock.of("listOf(%L)", flows.joinToString(", ") { "\"${it.id}\"" }))
                .addKdoc("Every documented flow id of the brick, sorted.")
                .build(),
        )
        type.addProperty(
            PropertySpec.builder("registry", SET.parameterizedBy(STRING))
                .initializer("flows.map { \"flow.\$it.*\" }.toSet()")
                .addKdoc("Registry entries (`flow.<id>.*`) for `AnalyticsOptions.registry`.")
                .build(),
        )
        return type.build()
    }

    /** `events.json`, or null when the brick documents no flow. */
    fun registryJson(module: String, version: String, raw: JsonArray?): String? {
        val flows = flows(raw)
        if (flows.isEmpty()) return null
        val doc = buildJsonObject {
            put("\$generated", "by stapel-codegen from flows.json — do not edit; drift-gated (stapelGenerateCheck)")
            put("package", module)
            put("version", version)
            put("defined", JsonArray(emptyList()))
            put(
                "flows",
                buildJsonArray {
                    flows.forEach { f ->
                        add(
                            buildJsonObject {
                                put("flow", f.id)
                                put("event", "flow.${f.id}.<step>")
                                f.titleKey?.let { put("titleKey", it) }
                                f.descriptionKey?.let { put("descriptionKey", it) }
                                put(
                                    "props",
                                    buildJsonObject {
                                        put(
                                            "phase",
                                            buildJsonObject {
                                                put("type", "string")
                                                put("description", "Transition phase of the funnel step")
                                                put("options", JsonArray(PHASES.map(::JsonPrimitive)))
                                            },
                                        )
                                    },
                                )
                                put(
                                    "steps",
                                    buildJsonArray {
                                        f.steps.sortedBy { it["order"]?.jsonPrimitive?.intOrNull ?: 0 }.forEach { s ->
                                            add(
                                                buildJsonObject {
                                                    s["order"]?.jsonPrimitive?.intOrNull?.let { put("order", it) }
                                                    s.text("kind")?.let { put("kind", it) }
                                                    s.text("note_key")?.let { put("noteKey", it) }
                                                },
                                            )
                                        }
                                    },
                                )
                                put("source", "flows.json")
                            },
                        )
                    }
                },
            )
        }
        return prettyJson(doc)
    }

    private fun constantName(id: String): String =
        id.split('.', '_', '-').filter { it.isNotEmpty() }.joinToString("_") { it.uppercase() }.ifEmpty { "FLOW" }
            .let { if (it.first().isDigit()) "F_$it" else it }

    private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
}
