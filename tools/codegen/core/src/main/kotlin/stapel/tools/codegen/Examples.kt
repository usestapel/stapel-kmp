package stapel.tools.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeSpec
import kotlinx.serialization.json.jsonObject
import stapel.tools.codegen.ir.ApiModel
import stapel.tools.codegen.ir.Body
import stapel.tools.codegen.ir.Success

/**
 * Round-trip fixtures (§11): every named request/response example of the contract, paired with the
 * serializer the operation spec uses for it. Emitted into the brick's test sources only.
 */
internal object Examples {
    fun fileSpec(schema: kotlinx.serialization.json.JsonObject, model: ApiModel, apiPkg: String): FileSpec {
        val name = Names.pascal(model.brick) + "ContractExamples"
        val ops = ClassName(apiPkg, Names.pascal(model.brick) + "Operations")
        val example = ClassName(apiPkg, name, "Example")
        val byId = model.operations.associateBy { it.id }
        val list = CodeBlock.builder().add("listOf(\n").indent()
        var count = 0
        for ((_, itemEl) in schema.obj("paths").orEmptyObj()) {
            for ((_, opEl) in itemEl.jsonObject) {
                val op = (opEl as? kotlinx.serialization.json.JsonObject) ?: continue
                val id = op.str("operationId") ?: continue
                val m = byId[id] ?: continue
                if (m.body is Body.Json) {
                    op.obj("requestBody")?.obj("content")?.obj("application/json")?.obj("examples")?.forEach { (key, ex) ->
                        val value = ex.jsonObject["value"] ?: return@forEach
                        list.add("%T(%S, %S, %S, %T.%N.body!!),\n", example, id, "request $key", canonicalJson(value), ops, m.name)
                        count++
                    }
                }
                val success = m.success as? Success.Json ?: continue
                for ((status, r) in op.obj("responses").orEmptyObj()) {
                    if (status.toIntOrNull() !in success.statuses) continue
                    r.jsonObject.obj("content")?.obj("application/json")?.obj("examples")?.forEach { (key, ex) ->
                        val value = ex.jsonObject["value"] ?: return@forEach
                        list.add("%T(%S, %S, %S, %T.%N.success.getValue(%L)!!),\n", example, id, "response $status $key", canonicalJson(value), ops, m.name, status.toInt())
                        count++
                    }
                }
            }
        }
        list.unindent().add(")")
        val ser = ClassName("kotlinx.serialization", "KSerializer").parameterizedBy(STAR)
        val type = TypeSpec.objectBuilder(name).addModifiers(KModifier.INTERNAL)
            .addKdoc("%L", "The $count named examples of the pinned contract with the serializer each decodes with.")
            .addType(
                TypeSpec.classBuilder("Example").addModifiers(KModifier.INTERNAL)
                    .primaryConstructor(
                        FunSpec.constructorBuilder().addParameter("operationId", STRING).addParameter("label", STRING)
                            .addParameter("json", STRING).addParameter("serializer", ser).build(),
                    )
                    .addProperty(PropertySpec.builder("operationId", STRING).initializer("operationId").build())
                    .addProperty(PropertySpec.builder("label", STRING).initializer("label").build())
                    .addProperty(PropertySpec.builder("json", STRING).initializer("json").build())
                    .addProperty(PropertySpec.builder("serializer", ser).initializer("serializer").build())
                    .build(),
            )
            .addProperty(PropertySpec.builder("all", LIST.parameterizedBy(example)).initializer(list.build()).build())
            .build()
        return FileSpec.builder(apiPkg, name).addType(type).build()
    }
}
