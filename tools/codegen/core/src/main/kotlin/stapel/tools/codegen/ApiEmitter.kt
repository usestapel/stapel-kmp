package stapel.tools.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.UNIT
import stapel.tools.codegen.ir.ApiModel
import stapel.tools.codegen.ir.AuthRequirement
import stapel.tools.codegen.ir.Body
import stapel.tools.codegen.ir.EnumDecl
import stapel.tools.codegen.ir.ErrorBinding
import stapel.tools.codegen.ir.ErrorCode
import stapel.tools.codegen.ir.HttpMethod
import stapel.tools.codegen.ir.Operation
import stapel.tools.codegen.ir.PageStyle
import stapel.tools.codegen.ir.Parameter
import stapel.tools.codegen.ir.ScalarKind
import stapel.tools.codegen.ir.Success
import stapel.tools.codegen.ir.TypeDecl
import stapel.tools.codegen.ir.TypeRef
import stapel.tools.codegen.RuntimeNames as R

/** Operation client, operation specs, errors, pagers, keys and invalidation (§6, §7). */
internal class ApiEmitter(
    private val model: ApiModel,
    private val apiPkg: String,
    private val models: ModelEmitter,
    private val types: Map<String, TypeDecl>,
    private val plan: CachePlan,
) {
    private val brick = model.brick
    private val pascal = Names.pascal(brick)
    private val errorPkg = "$apiPkg.error"
    private val apiName = ClassName(apiPkg, "${pascal}Api")
    private val clientName = ClassName(apiPkg, "${pascal}ApiClient")
    private val opsName = ClassName(apiPkg, "${pascal}Operations")
    private val urlsName = ClassName(apiPkg, "${pascal}Urls")
    private val brickError = ClassName(errorPkg, "${pascal}ApiError")
    private val clientOps = model.operations.filter { it.success !is Success.Redirect }
    private val redirectOps = model.operations.filter { it.success is Success.Redirect }
    private val errorByCode = model.errors.associateBy { it.code }

    /** Kotlin class name of each error code, unique within the brick. */
    private val errorClassOf: Map<String, String> = run {
        val reserved = setOf("Transport", "Unknown", "Companion")
        val base = model.errors.associate { it.code to Names.errorClass(it.code) }
        val counts = base.values.groupingBy { it }.eachCount()
        base.mapValues { (code, n) ->
            val status = code.split('.').getOrNull(1).orEmpty()
            when {
                n in reserved -> n + status
                (counts[n] ?: 0) > 1 -> n + status
                n.isEmpty() -> "Code$status"
                else -> n
            }
        }
    }

    fun opErrorName(op: Operation): ClassName = ClassName(errorPkg, Names.pascal(op.name) + "Error")

    private fun boundCodes(op: Operation): List<String>? = (op.errors as? ErrorBinding.Bound)?.codes

    // ---- shared helpers -----------------------------------------------------------------------------------

    private fun successType(op: Operation): TypeName = when (val s = op.success) {
        is Success.Json -> models.typeName(s.type)
        is Success.Text -> STRING
        else -> UNIT
    }

    private fun bodyType(op: Operation): TypeName = when (val b = op.body) {
        is Body.Json -> models.typeName(b.type, requestSide = true)
        else -> UNIT
    }

    private fun resultOf(value: TypeName, op: Operation): TypeName = R.StapelResult.parameterizedBy(value, opErrorName(op))

    private fun paramType(p: Parameter): TypeName {
        val t = models.typeName(p.type, requestSide = true)
        return if (p.required) t else t.copy(nullable = true)
    }

    /** Expression converting a value of [type] named [v] to its wire text (non-null receiver). */
    private fun textOf(type: TypeRef, v: String): String = when {
        type is TypeRef.Named && types[type.name] is EnumDecl -> "$v.wire"
        type is TypeRef.Scalar && type.kind == ScalarKind.STRING -> v
        type is TypeRef.Scalar && type.kind == ScalarKind.DECIMAL -> "$v.text"
        type is TypeRef.Scalar && type.kind == ScalarKind.CURSOR -> "$v.text"
        type is TypeRef.ListOf -> "$v.joinToString(\",\") { " + textOf(type.item, "it") + " }"
        else -> "$v.toString()"
    }

    private fun optionalText(type: TypeRef, v: String, required: Boolean): String {
        if (required) return textOf(type, v)
        val conv = textOf(type, "it")
        return if (conv == "it") v else "$v?.let { $conv }"
    }

    private fun queryEntries(params: List<Parameter>, ignore: Set<String> = emptySet()): CodeBlock? {
        val list = params.filter { it.location == Parameter.Location.QUERY && it.wireName !in ignore }
        if (list.isEmpty()) return null
        val b = CodeBlock.builder().add("buildList<%T> {\n", PAIR_STRING_NSTRING).indent()
        for (p in list) {
            val type = p.type
            if (type is TypeRef.ListOf) {
                val each = textOf(type.item, "it")
                if (p.required) b.add("%N.forEach { add(%S to %L) }\n", p.name, p.wireName, each)
                else b.add("%N?.forEach { add(%S to %L) }\n", p.name, p.wireName, each)
            } else if (p.required) {
                b.add("add(%S to %L)\n", p.wireName, textOf(type, p.name))
            } else {
                val conv = textOf(type, "it")
                if (conv == "it") b.add("add(%S to %N)\n", p.wireName, p.name)
                else b.add("add(%S to %N?.let { %L })\n", p.wireName, p.name, conv)
            }
        }
        return b.unindent().add("}").build()
    }

    private fun callArgs(op: Operation, bodyArg: String?): CodeBlock {
        val args = mutableListOf<CodeBlock>()
        val path = op.parameters.filter { it.location == Parameter.Location.PATH }
        if (path.isNotEmpty()) {
            args += CodeBlock.of("pathParams = listOf(%L)", path.joinToString(", ") { textOf(it.type, it.name) })
        }
        queryEntries(op.parameters)?.let { args += CodeBlock.of("query = %L", it) }
        val headers = op.parameters.filter { it.location == Parameter.Location.HEADER }
        if (headers.isNotEmpty()) {
            args += CodeBlock.of(
                "headers = listOf(%L)",
                headers.joinToString(", ") { h ->
                    "\"${h.wireName}\" to " + optionalText(h.type, h.name, h.required)
                },
            )
        }
        when (val b = op.body) {
            is Body.Json -> if (bodyArg != null) args += CodeBlock.of("body = %N", bodyArg)
            is Body.Parts -> {
                val parts = CodeBlock.builder().add("buildList<%T> {\n", R.FormPart).indent()
                for (p in b.parts) {
                    val isFile = (p.type as? TypeRef.Scalar)?.kind == ScalarKind.BINARY
                    val required = p.required && !p.nullable
                    when {
                        isFile && required -> parts.add("add(%T(%S, %N))\n", R.FormPartFile, p.wireName, p.name)
                        isFile -> parts.add("%N?.let { add(%T(%S, it)) }\n", p.name, R.FormPartFile, p.wireName)
                        required -> parts.add("add(%T(%S, %L))\n", R.FormPartText, p.wireName, textOf(p.type, p.name))
                        else -> parts.add("%N?.let { add(%T(%S, %L)) }\n", p.name, R.FormPartText, p.wireName, textOf(p.type, "it"))
                    }
                }
                args += CodeBlock.of("parts = %L", parts.unindent().add("}").build())
                if (b.multipart) args += CodeBlock.of("onProgress = onProgress")
            }
            null -> Unit
        }
        val type = R.OperationCall.parameterizedBy(bodyType(op))
        if (args.isEmpty()) return CodeBlock.of("%T()", type)
        val b = CodeBlock.builder().add("%T(\n", type).indent()
        args.forEach { b.add("%L,\n", it) }
        return b.unindent().add(")").build()
    }

    /** Parameters of an operation function, in signature order (path, required body, query, headers, optional body). */
    private fun functionParams(op: Operation, withDefaults: Boolean, skipQuery: Set<String> = emptySet()): List<ParameterSpec> {
        val out = mutableListOf<ParameterSpec>()
        fun add(name: String, type: TypeName, doc: String?, optional: Boolean) {
            val p = ParameterSpec.builder(name, type)
            if (optional && withDefaults) p.defaultValue("null")
            doc?.let { p.addKdoc("%L", KDoc.escape(it)) }
            out += p.build()
        }
        op.parameters.filter { it.location == Parameter.Location.PATH }.forEach { add(it.name, paramType(it), it.doc, false) }
        val body = op.body
        if (body is Body.Json && body.required) add("body", bodyType(op), body.doc, false)
        if (body is Body.Parts) {
            body.parts.forEach { p ->
                val t = models.typeName(p.type, requestSide = true)
                val required = p.required && !p.nullable
                add(p.name, if (required) t else t.copy(nullable = true), p.doc, !required)
            }
        }
        op.parameters.filter { it.location != Parameter.Location.PATH && it.wireName !in skipQuery }.forEach { add(it.name, paramType(it), it.doc, !it.required) }
        if (body is Body.Json && !body.required) add("body", bodyType(op).copy(nullable = true), body.doc, true)
        if (body is Body.Parts && body.multipart) {
            val progress = LambdaTypeName.get(parameters = listOf(ParameterSpec("sent", com.squareup.kotlinpoet.LONG), ParameterSpec("total", com.squareup.kotlinpoet.LONG.copy(nullable = true))), returnType = UNIT).copy(nullable = true)
            add("onProgress", progress, "Upload progress `(sent, total)` in bytes.", true)
        }
        return out
    }

    // ---- KDoc --------------------------------------------------------------------------------------------

    private fun operationDoc(op: Operation): String {
        val parts = mutableListOf<String>()
        val first = op.summary ?: op.description?.let(KDoc::firstSentence)
        first?.let { parts += KDoc.escape(it) }
        op.description?.let { d -> if (op.summary != null || KDoc.firstSentence(d) != d.trim()) parts += KDoc.escape(d) }
        parts += "Wire: `${op.method} ${op.path}` · `${op.id}`"
        if (op.permissions.isNotEmpty()) parts += "Permissions: " + op.permissions.joinToString(", ")
        parts += "Auth: " + op.auth.name.lowercase()
        if (op.gatedBy.isNotEmpty()) parts += "Available only when " + op.gatedBy.joinToString(" and ") { "`$it`" } + " is enabled."
        op.capability?.let { parts += "Capability: `$it`" }
        if (op.flows.isNotEmpty()) parts += "Flow: " + op.flows.joinToString(", ") { "`$it`" }
        op.verificationScope?.let { parts += "Step-up verification: scope `$it`" }
        op.captcha?.let { parts += "Captcha: action `${it.action}`" }
        op.pagination?.let { parts += "Pagination: ${it.style.name.lowercase().replace('_', ' ')} (`${it.cursorParam}`); see `${op.name}Pager`." }
        parts += errorsDoc(op)
        op.requestExample?.let { parts += "Example request" + (it.title?.let { t -> " (${KDoc.escape(t)})" } ?: "") + ":\n" + KDoc.jsonBlock(it.json) }
        op.responseExample?.let { parts += "Example response" + (it.title?.let { t -> " (${KDoc.escape(t)})" } ?: "") + ":\n" + KDoc.jsonBlock(it.json) }
        val returns = when (val s = op.success) {
            is Success.Json -> "`" + typeLabel(s.type) + "` (" + s.statuses.joinToString(", ") + ")" + unionVariants(s.type)
            is Success.Empty -> "nothing (" + s.statuses.joinToString(", ") + ")"
            is Success.Text -> "the `${s.mediaType}` body as text (" + s.statuses.joinToString(", ") + ")"
            is Success.Binary -> "the value of `consume` over the `${s.mediaTypes.joinToString(" | ")}` body"
            is Success.Redirect -> "a URL to open in a browser"
        }
        parts += "@return $returns"
        return parts.joinToString("\n\n")
    }

    private fun typeLabel(t: TypeRef): String = when (t) {
        is TypeRef.Named -> t.name
        is TypeRef.ListOf -> "List<" + typeLabel(t.item) + ">"
        is TypeRef.MapOf -> "Map<String, " + typeLabel(t.value) + ">"
        is TypeRef.Scalar -> t.kind.name.lowercase()
        TypeRef.Json -> "JsonElement"
        TypeRef.JsonObj -> "JsonObject"
        TypeRef.JsonScalar -> "JsonPrimitive"
    }

    private fun unionVariants(t: TypeRef): String {
        val u = (t as? TypeRef.Named)?.let { types[it.name] } as? stapel.tools.codegen.ir.UnionDecl ?: return ""
        return ": " + u.variants.joinToString(", ") { "`$it`" }
    }

    private fun errorsDoc(op: Operation): String = when (val e = op.errors) {
        is ErrorBinding.Unbound ->
            if (e.statuses.isEmpty()) "Errors: the contract declares no error response; transport failures arrive as `${brickError.simpleName}.Transport`."
            else "Errors: statuses ${e.statuses.joinToString(", ")} (codes not bound in the contract)."
        is ErrorBinding.Bound -> buildString {
            append("Errors (bound by ${e.source}):")
            e.codes.forEach { c ->
                val spec = errorByCode[c]
                append("\n- `").append(errorClassOf[c]).append("` (").append(spec?.status).append(") — ")
                append(KDoc.escape(spec?.en.orEmpty()).replace("\n", " "))
            }
            if (e.unboundStatuses.isNotEmpty()) append("\n- statuses ${e.unboundStatuses.joinToString(", ")} (codes not bound in the contract)")
        }
    }

    // ---- files -----------------------------------------------------------------------------------------

    fun apiInterface(): TypeSpec {
        val b = TypeSpec.interfaceBuilder(apiName).addModifiers(KModifier.PUBLIC)
            .addKdoc(
                "%L",
                "Every client operation of `${model.stamp.module}` ${model.stamp.version}, one suspend function each.\n\n" +
                    "Functions never throw for an HTTP, transport or decoding outcome: they return " +
                    "`StapelResult.Err` with the operation's error type. Implemented by [${clientName.simpleName}].",
            )
        for (op in clientOps.sortedBy { it.name }) b.addFunction(function(op, abstract = true))
        return b.build()
    }

    private fun function(op: Operation, abstract: Boolean): FunSpec {
        val f = FunSpec.builder(op.name).addModifiers(KModifier.SUSPEND)
        if (abstract) f.addModifiers(KModifier.PUBLIC, KModifier.ABSTRACT).addKdoc("%L", operationDoc(op)) else f.addModifiers(KModifier.OVERRIDE)
        functionParams(op, withDefaults = abstract).forEach { p -> f.addParameter(if (abstract) p else p.toBuilder().apply { kdoc.clear() }.build()) }
        if (op.success is Success.Binary) {
            val r = TypeVariableName("R")
            f.addTypeVariable(r)
            f.addParameter(
                ParameterSpec.builder("consume", LambdaTypeName.get(parameters = arrayOf(R.BinaryBody), returnType = r).copy(suspending = true))
                    .apply { if (abstract) addKdoc("%L", "Reads the body; it is valid only inside this block.") }.build(),
            )
            f.returns(resultOf(r, op))
            if (!abstract) f.addStatement("return transport.download(%T.%N, %L, consume)", opsName, op.name, callArgs(op, "body"))
        } else {
            f.returns(resultOf(successType(op), op))
            if (!abstract) f.addStatement("return transport.execute(%T.%N, %L)", opsName, op.name, callArgs(op, "body"))
        }
        return f.build()
    }

    fun apiClient(): TypeSpec {
        val b = TypeSpec.classBuilder(clientName).addModifiers(KModifier.PUBLIC)
            .addSuperinterface(apiName)
            .addKdoc("%L", "[${apiName.simpleName}] over an [ApiTransport]; holds no state of its own.\n\n@param transport executes the operation specs.")
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("transport", R.ApiTransport).build())
            .addProperty(PropertySpec.builder("transport", R.ApiTransport, KModifier.PRIVATE).initializer("transport").build())
        for (op in clientOps.sortedBy { it.name }) b.addFunction(function(op, abstract = false))
        return b.build()
    }

    fun urls(): TypeSpec? {
        if (redirectOps.isEmpty()) return null
        val b = TypeSpec.classBuilder(urlsName).addModifiers(KModifier.PUBLIC)
            .addKdoc("%L", "Browser-redirect operations of `${model.stamp.module}`: URLs to open in a browser or custom tab; no request is made.\n\n@param transport resolves the host of each URL.")
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("transport", R.ApiTransport).build())
            .addProperty(PropertySpec.builder("transport", R.ApiTransport, KModifier.PRIVATE).initializer("transport").build())
        for (op in redirectOps.sortedBy { it.name }) {
            val f = FunSpec.builder(op.name + "Url").addModifiers(KModifier.PUBLIC).returns(STRING)
                .addKdoc("%L", operationDoc(op))
            functionParams(op, withDefaults = true).forEach { f.addParameter(it) }
            f.addStatement("return transport.url(%T.%N, %L)", opsName, op.name, callArgs(op, "body"))
            b.addFunction(f.build())
        }
        return b.build()
    }

    fun operations(): TypeSpec {
        val b = TypeSpec.objectBuilder(opsName).addModifiers(KModifier.INTERNAL)
            .addKdoc("%L", "One [OperationSpec] per operation of `${model.stamp.module}`, by function name.")
        val e = TypeVariableName("E", brickError)
        b.addFunction(
            FunSpec.builder("errors").addModifiers(KModifier.PRIVATE, KModifier.INLINE).addTypeVariable(e.copy(reified = true))
                .returns(R.ErrorDecoder.parameterizedBy(e))
                .addCode(
                    CodeBlock.builder()
                        .add("return object : %T<E> {\n", R.ErrorDecoder).indent()
                        .add("override fun domain(envelope: %T): E =\n", R.ErrorEnvelope).indent()
                        .add("%T.decode(envelope) as? E ?: %T(envelope) as E\n", brickError, brickError.nestedClass("Unknown")).unindent()
                        .add("\noverride fun transport(failure: %T): E = %T(failure) as E\n", R.TransportFailure, brickError.nestedClass("Transport"))
                        .add("\noverride fun binds(code: %T): Boolean =\n", R.ErrorCode).indent()
                        .add("%T.decode(%T(code, 0)).let { it is E && it !is %T }\n", brickError, R.ErrorEnvelope, brickError.nestedClass("Unknown")).unindent()
                        .unindent().add("}\n").build(),
                ).build(),
        )
        for (op in model.operations.sortedBy { it.name }) {
            val bodyT = bodyType(op)
            val successT = if (op.success is Success.Binary || op.success is Success.Redirect) UNIT else successType(op)
            val type = R.OperationSpec.parameterizedBy(bodyT, successT, opErrorName(op))
            val init = CodeBlock.builder().add("%T(\n", R.OperationSpec).indent()
            init.add("id = %S,\n", op.id)
            init.add("module = %S,\n", brick)
            init.add("method = %S,\n", op.method.name)
            init.add("path = %S,\n", op.path)
            init.add("kind = %T.%L,\n", R.OperationKind, if (op.method == HttpMethod.GET) "Query" else "Mutation")
            init.add("auth = %T.%L,\n", R.AuthRequirement, when (op.auth) { AuthRequirement.REQUIRED -> "Required"; AuthRequirement.OPTIONAL -> "Optional"; AuthRequirement.NONE -> "None" })
            val (encoding, bodySer) = when (val body = op.body) {
                is Body.Json -> "Json" to CodeBlock.of("%M<%T>()", R.serializer, bodyT)
                is Body.Parts -> (if (body.multipart) "Multipart" else "Form") to CodeBlock.of("null")
                null -> "None" to CodeBlock.of("null")
            }
            init.add("bodyEncoding = %T.%L,\n", R.BodyEncoding, encoding)
            init.add("body = %L,\n", bodySer)
            val (kind, success) = when (val s = op.success) {
                is Success.Json -> "Json" to s.statuses.joinToString(", ") { "$it to %M<%T>()" }.let { fmt ->
                    CodeBlock.of("mapOf($fmt)", *s.statuses.flatMap { listOf(R.serializer, successT) }.toTypedArray())
                }
                is Success.Empty -> "Empty" to CodeBlock.of("mapOf(" + s.statuses.joinToString(", ") { "$it to null" } + ")")
                is Success.Text -> "Text" to s.statuses.joinToString(", ") { "$it to %M<%T>()" }.let { fmt ->
                    CodeBlock.of("mapOf($fmt)", *s.statuses.flatMap { listOf(R.serializer, STRING) }.toTypedArray())
                }
                is Success.Binary -> "Binary" to CodeBlock.of("mapOf(" + s.statuses.joinToString(", ") { "$it to null" } + ")")
                is Success.Redirect -> "Empty" to CodeBlock.of("mapOf(" + s.statuses.joinToString(", ") { "$it to null" } + ")")
            }
            init.add("responseKind = %T.%L,\n", R.ResponseKind, kind)
            init.add("success = %L,\n", success)
            init.add("errors = errors(),\n")
            op.verificationScope?.let { init.add("verificationScope = %S,\n", it) }
            op.captcha?.let { init.add("captcha = %T(%S, %S, %S),\n", R.CaptchaRequirement, it.action, it.header, it.field) }
            op.capability?.let { init.add("capability = %S,\n", it) }
            init.unindent().add(")")
            b.addProperty(PropertySpec.builder(op.name, type).initializer(init.build()).build())
        }
        return b.build()
    }

    // ---- errors ----------------------------------------------------------------------------------------

    fun brickErrors(): TypeSpec {
        val boundOps = model.operations.filter { boundCodes(it) != null }
        val opsByCode = java.util.TreeMap<String, MutableList<ClassName>>()
        boundOps.forEach { op -> boundCodes(op)!!.forEach { c -> opsByCode.getOrPut(c) { mutableListOf() } += opErrorName(op) } }
        val allOpErrors = boundOps.map { opErrorName(it) }.sortedBy { it.simpleName }.distinct()
        val b = TypeSpec.interfaceBuilder(brickError).addModifiers(KModifier.PUBLIC, KModifier.SEALED)
            .addSuperinterface(R.StapelError)
            .addKdoc(
                "%L",
                "Errors of `${model.stamp.module}`: one class per code of its `errors.json` (${model.errors.size} codes, " +
                    "core codes included), `Unknown` for a code this version does not know, and `Transport` for the shared " +
                    "transport family. Cross-brick handling goes through `code`.",
            )
        for (spec in model.errors) {
            val cls = TypeSpec.classBuilder(errorClassOf.getValue(spec.code)).addModifiers(KModifier.PUBLIC, KModifier.DATA)
                .addKdoc("%L", KDoc.escape(spec.en.ifEmpty { spec.code }) + "\n\nCode `${spec.code}` · status ${spec.status}.\n\n@property envelope the server's answer.")
                .addSuperinterface(brickError).addSuperinterface(R.DomainError)
            opsByCode[spec.code]?.distinct()?.sortedBy { it.simpleName }?.forEach { cls.addSuperinterface(it) }
            cls.primaryConstructor(FunSpec.constructorBuilder().addParameter("envelope", R.ErrorEnvelope).build())
            cls.addProperty(PropertySpec.builder("envelope", R.ErrorEnvelope, KModifier.OVERRIDE).initializer("envelope").build())
            cls.addProperty(
                PropertySpec.builder("remediation", R.Remediation, KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addStatement("return %T.%L", R.Remediation, remediationName(spec)).build()).build(),
            )
            val taken = setOf("envelope", "remediation", "code", "status", "params")
            for (param in spec.params) {
                var n = Names.member(param)
                if (n in taken) n += "Param"
                val integer = spec.paramTypes[param] == "integer"
                cls.addProperty(
                    PropertySpec.builder(n, (if (integer) INT else STRING).copy(nullable = true), KModifier.PUBLIC)
                        .addKdoc("%L", "Envelope param `$param`" + (if (integer) " as an integer" else "") + ", when present.")
                        .getter(FunSpec.getterBuilder().addStatement(if (integer) "return envelope.params.int(%S)" else "return envelope.params[%S]", param).build())
                        .build(),
                )
            }
            b.addType(cls.build())
        }
        val unknown = TypeSpec.classBuilder("Unknown").addModifiers(KModifier.PUBLIC, KModifier.DATA)
            .addKdoc("A code this client version does not know; its remediation is the backend's heuristic.\n\n@property envelope the server's answer.")
            .addSuperinterface(brickError).addSuperinterface(R.DomainError)
        allOpErrors.forEach { unknown.addSuperinterface(it) }
        unknown.primaryConstructor(FunSpec.constructorBuilder().addParameter("envelope", R.ErrorEnvelope).build())
            .addProperty(PropertySpec.builder("envelope", R.ErrorEnvelope, KModifier.OVERRIDE).initializer("envelope").build())
            .addProperty(
                PropertySpec.builder("remediation", R.Remediation, KModifier.OVERRIDE)
                    .getter(FunSpec.getterBuilder().addStatement("return %T.default(envelope.code.value, envelope.status, envelope.params.names)", R.Remediation).build()).build(),
            )
        b.addType(unknown.build())
        val transport = TypeSpec.classBuilder("Transport").addModifiers(KModifier.PUBLIC, KModifier.DATA)
            .addKdoc("A failure of the shared transport family (no response, session gone, step-up unresolved, throttled, server down, contract violated).\n\n@property failure the carried failure.")
            .addSuperinterface(brickError).addSuperinterface(R.TransportCarrier)
        allOpErrors.forEach { transport.addSuperinterface(it) }
        transport.primaryConstructor(FunSpec.constructorBuilder().addParameter("failure", R.TransportFailure).build())
            .addProperty(PropertySpec.builder("failure", R.TransportFailure, KModifier.OVERRIDE).initializer("failure").build())
        b.addType(transport.build())
        val decode = CodeBlock.builder().beginControlFlow("return when (envelope.code.value)")
        model.errors.forEach { decode.addStatement("%S -> %N(envelope)", it.code, errorClassOf.getValue(it.code)) }
        decode.addStatement("else -> Unknown(envelope)").endControlFlow()
        b.addType(
            TypeSpec.companionObjectBuilder().addModifiers(KModifier.PUBLIC)
                .addKdoc("Envelope decoding.")
                .addFunction(
                    FunSpec.builder("decode").addModifiers(KModifier.PUBLIC).addParameter("envelope", R.ErrorEnvelope).returns(brickError)
                        .addKdoc("The typed error for [envelope]; `Unknown` for a code outside the table.")
                        .addCode(decode.build()).build(),
                ).build(),
        )
        return b.build()
    }

    private fun remediationName(spec: ErrorCode): String = when (spec.remediation) {
        "retry" -> "Retry"
        "wait_and_retry" -> "WaitAndRetry"
        "reauthenticate" -> "Reauthenticate"
        "verify" -> "Verify"
        "fix_input" -> "FixInput"
        "contact_support" -> "ContactSupport"
        "bug" -> "Bug"
        else -> "Unknown"
    }

    /** `<Op>Error`: a sealed interface for bound operations, a typealias otherwise (ruling R3). */
    fun opError(op: Operation): Pair<TypeSpec?, com.squareup.kotlinpoet.TypeAliasSpec?> {
        val name = opErrorName(op)
        val codes = boundCodes(op)
        if (codes == null) {
            val doc = "Errors of `${op.id}`. The contract does not bind codes to this operation yet, so any code of the brick may arrive."
            return null to com.squareup.kotlinpoet.TypeAliasSpec.builder(name.simpleName, brickError).addModifiers(KModifier.PUBLIC).addKdoc("%L", doc).build()
        }
        val e = op.errors as ErrorBinding.Bound
        val doc = buildString {
            append("Errors of `${op.id}`: ")
            append(if (codes.isEmpty()) "no code is bound" else codes.joinToString(", ") { "`" + errorClassOf.getValue(it) + "`" })
            append(", plus `Unknown` and `Transport`. Bound by ${e.source}.")
            if (e.unboundStatuses.isNotEmpty()) append(" Statuses ${e.unboundStatuses.joinToString(", ")} are declared with no code bound to them.")
        }
        return TypeSpec.interfaceBuilder(name).addModifiers(KModifier.PUBLIC, KModifier.SEALED).addSuperinterface(brickError).addKdoc("%L", doc).build() to null
    }

    fun errorCatalog(): TypeSpec {
        val name = ClassName(errorPkg, "${pascal}ErrorCatalog")
        val entries = CodeBlock.builder().add("mapOf(\n").indent()
        for (e in model.errors) {
            entries.add(
                "%S to %T(%T(%S), %L, listOf(%L), %T.%L, %S),\n",
                e.code, R.ErrorSpec, R.ErrorCode, e.code, e.status,
                e.params.joinToString(", ") { "\"" + it + "\"" }, R.Remediation, remediationName(e), e.en,
            )
        }
        entries.unindent().add(")")
        return TypeSpec.objectBuilder(name).addModifiers(KModifier.PUBLIC).addSuperinterface(R.ErrorCatalog)
            .addKdoc("%L", "Canonical English texts of `${model.stamp.module}`'s error codes (`errors.json`); other locales are bundles of the UI library.")
            .addProperty(PropertySpec.builder("owner", STRING, KModifier.OVERRIDE).initializer("%S", brick).build())
            .addProperty(PropertySpec.builder("specs", MAP.parameterizedBy(STRING, R.ErrorSpec), KModifier.PRIVATE).initializer(entries.build()).build())
            .addFunction(FunSpec.builder("spec").addModifiers(KModifier.OVERRIDE).addParameter("code", R.ErrorCode).returns(R.ErrorSpec.copy(nullable = true)).addStatement("return specs[code.value]").build())
            .addFunction(
                FunSpec.builder("template").addModifiers(KModifier.OVERRIDE).addParameter("code", R.ErrorCode).addParameter("locale", R.LocaleTag)
                    .returns(STRING.copy(nullable = true)).addStatement("return if (locale.value == %S) specs[code.value]?.en else null", "en").build(),
            ).build()
    }

    // ---- pagers -----------------------------------------------------------------------------------------

    fun pagers(): List<FunSpec> = clientOps.filter { it.pagination != null }.sortedBy { it.name }.map { op ->
        val pg = op.pagination!!
        val skip = when (pg.style) {
            PageStyle.ANCHOR -> setOf("anchor", "direction")
            else -> setOf(pg.cursorParam)
        }
        val item = models.typeName(pg.itemType)
        val f = FunSpec.builder(op.name + "Pager").addModifiers(KModifier.PUBLIC).receiver(apiName)
            .returns(R.Pager.parameterizedBy(item, opErrorName(op)))
            .addKdoc("%L", "Pages of `${op.id}`, following the ${pg.style.name.lowercase().replace('_', ' ')} cursor until the last page.")
        val params = functionParams(op, withDefaults = true, skipQuery = skip)
        params.forEach { f.addParameter(it) }
        val cursorParam = op.parameters.first { it.wireName == pg.cursorParam }
        val cursorExpr = when ((cursorParam.type as? TypeRef.Scalar)?.kind) {
            ScalarKind.INT -> "cursor?.text?.toIntOrNull()"
            ScalarKind.LONG -> "cursor?.text?.toLongOrNull()"
            ScalarKind.CURSOR -> "cursor"
            else -> "cursor?.text"
        }
        val args = mutableListOf<CodeBlock>()
        params.forEach { args += CodeBlock.of("%N = %N", it.name, it.name) }
        args += CodeBlock.of("%N = %L", cursorParam.name, cursorExpr)
        if (pg.style == PageStyle.ANCHOR) {
            op.parameters.firstOrNull { it.wireName == "direction" }?.let { d ->
                val dt = (d.type as? TypeRef.Named)?.name
                val value = if (dt != null && types[dt] is EnumDecl) CodeBlock.of("%T.of(%S)", models.className(dt), "next") else CodeBlock.of("%S", "next")
                args += CodeBlock.of("%N = if (cursor == null) null else %L", d.name, value)
            }
        }
        f.addCode(CodeBlock.builder().add("return %T { cursor ->\n", R.Pager).indent()
            .add("%N(%L)\n", op.name, args.joinToCode())
            .unindent().add("}\n").build())
        f.build()
    }

    private fun List<CodeBlock>.joinToCode(): CodeBlock = CodeBlock.builder().apply {
        this@joinToCode.forEachIndexed { i, c -> if (i > 0) add(", "); add(c) }
    }.build()

    // ---- keys and invalidation ---------------------------------------------------------------------------

    fun keys(): TypeSpec {
        val name = ClassName(apiPkg, "${pascal}Keys")
        val b = TypeSpec.objectBuilder(name).addModifiers(KModifier.PUBLIC)
            .addKdoc("%L", "Cache keys of `${model.stamp.module}`'s queries, from `contracts/$brick/cache.json` (derived defaults where the plan is silent). Brick code takes keys only from here.")
            .addProperty(PropertySpec.builder("all", R.KeyPattern, KModifier.PUBLIC).initializer("%T.of(%S)", R.KeyPattern, brick).addKdoc("Every key of the brick.").build())
        val hints = CodeBlock.builder().add("mapOf(\n").indent()
        for (op in clientOps.sortedBy { it.name }) {
            val entry = plan.entries[op.id]
            if (op.method == HttpMethod.GET) {
                val segments = entry?.key ?: CachePlan.derivedKey(op)
                val f = FunSpec.builder(op.name).addModifiers(KModifier.PUBLIC).returns(R.CacheKey)
                    .addKdoc("%L", "Key of `${op.id}`" + if (entry?.key == null) " (derived)." else ".")
                val params = op.parameters.filter { it.location != Parameter.Location.HEADER }
                params.forEach { p -> f.addParameter(ParameterSpec.builder(p.name, paramType(p)).apply { if (!p.required) defaultValue("null") }.build()) }
                val segCode = segments.map { seg ->
                    if (seg.startsWith("{")) {
                        val p = op.parameters.first { it.wireName == seg.trim('{', '}') }
                        CodeBlock.of("%L", if (p.required) textOf(p.type, p.name) else "(" + optionalText(p.type, p.name, false) + " ?: \"\")")
                    } else {
                        CodeBlock.of("%S", seg)
                    }
                }
                val used = segments.filter { it.startsWith("{") }.map { it.trim('{', '}') }.toSet()
                val argsParams = params.filter { it.location == Parameter.Location.QUERY && it.wireName !in used }
                val call = CodeBlock.builder().add("return %T.of(%S", R.CacheKey, brick)
                segCode.forEach { call.add(", %L", it) }
                if (argsParams.isNotEmpty()) {
                    call.add(", args = %M {\n", R.buildJsonObject).indent()
                    argsParams.forEach { p ->
                        val v = if (p.required) textOf(p.type, p.name) else (if (textOf(p.type, "it") == "it") p.name else "${p.name}?.let { ${textOf(p.type, "it")} }")
                        call.add("%M(%S, %L)\n", R.put, p.wireName, v)
                    }
                    call.unindent().add("}")
                }
                if (entry?.scope == "app") call.add(", scope = %T.App", R.CacheKey.peerClass("CacheScope"))
                call.add(")\n")
                f.addCode(call.build())
                b.addFunction(f.build())
            }
            if (entry != null && (entry.policy != null || entry.persistence != null || entry.sensitive || entry.offline != null || entry.scope != "user")) {
                hints.add(
                    "%S to %T(policy = %S, persistence = %S, scope = %T.%L, sensitive = %L, offline = %S),\n",
                    op.id, R.CacheKey.peerClass("CacheHint"), entry.policy, entry.persistence,
                    R.CacheKey.peerClass("CacheScope"), if (entry.scope == "app") "App" else "User", entry.sensitive, entry.offline,
                )
            }
        }
        hints.unindent().add(")")
        b.addProperty(
            PropertySpec.builder("hints", MAP.parameterizedBy(STRING, R.CacheKey.peerClass("CacheHint")), KModifier.PUBLIC)
                .addKdoc("Plan choices by operationId, for operations whose plan entry sets any.")
                .initializer(hints.build()).build(),
        )
        return b.build()
    }

    fun invalidation(): TypeSpec {
        val name = ClassName(apiPkg, "${pascal}Invalidation")
        val table = CodeBlock.builder().add("mapOf(\n").indent()
        for (op in clientOps.filter { it.method != HttpMethod.GET }.sortedBy { it.id }) {
            val prefixes = plan.entries[op.id]?.invalidates ?: listOf(emptyList())
            table.add("%S to listOf<%T>(", op.id, R.KeyPattern)
            prefixes.forEachIndexed { i, prefix ->
                if (i > 0) table.add(", ")
                val fixed = prefix.takeWhile { !it.startsWith("{") }
                table.add("%T.of(%S", R.KeyPattern, brick)
                fixed.forEach { table.add(", %S", it) }
                table.add(")")
            }
            table.add("),\n")
        }
        table.unindent().add(")")
        return TypeSpec.objectBuilder(name).addModifiers(KModifier.PUBLIC)
            .addKdoc("%L", "What a successful mutation of `${model.stamp.module}` invalidates, from `contracts/$brick/cache.json`; a mutation the plan does not name invalidates the whole namespace. A `{param}` segment of a plan prefix widens the prefix to the segments before it.")
            .addProperty(
                PropertySpec.builder("table", MAP.parameterizedBy(STRING, LIST.parameterizedBy(R.KeyPattern)), KModifier.PUBLIC)
                    .addKdoc("operationId → key patterns, one entry per mutation, sorted by operationId.")
                    .initializer(table.build()).build(),
            )
            .addFunction(
                FunSpec.builder("after").addModifiers(KModifier.PUBLIC).addParameter("operationId", STRING).returns(LIST.parameterizedBy(R.KeyPattern))
                    .addKdoc("Patterns to invalidate after [operationId] succeeded; the whole namespace for an id not in [table].")
                    .addStatement("return table[operationId] ?: listOf(%T.of(%S))", R.KeyPattern, brick).build(),
            ).build()
    }

    fun contractObject(): TypeSpec {
        val name = ClassName(apiPkg, "${pascal}Contract")
        fun const(n: String, t: TypeName, fmt: String, v: Any, doc: String) =
            PropertySpec.builder(n, t, KModifier.PUBLIC, KModifier.CONST).initializer(fmt, v).addKdoc("%L", doc).build()
        val s = model.stamp
        return TypeSpec.objectBuilder(name).addModifiers(KModifier.PUBLIC)
            .addKdoc("%L", "Identity of the contract this package was generated from.")
            .addProperty(const("MODULE", STRING, "%S", s.module, "Backend module."))
            .addProperty(const("VERSION", STRING, "%S", s.version, "Release of the backend module."))
            .addProperty(const("REF", STRING, "%S", s.ref, "Commit of the backend module."))
            .addProperty(const("SCHEMA_SHA256", STRING, "%S", s.schemaSha256, "Digest of the vendored `schema.json`."))
            .addProperty(const("PROFILE", INT, "%L", s.profile, "Contract profile (`info.x-stapel-contract`)."))
            .addProperty(const("RUNTIME_ABI", INT, "%L", R.RUNTIME_ABI, "`stapel.wire.CODEGEN_RUNTIME_ABI` the code was generated for."))
            .build()
    }

    companion object {
        private val PAIR_STRING_NSTRING: TypeName = ClassName("kotlin", "Pair").parameterizedBy(STRING, STRING.copy(nullable = true))
    }
}
