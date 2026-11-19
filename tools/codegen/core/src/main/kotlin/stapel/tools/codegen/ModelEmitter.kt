package stapel.tools.codegen

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.LIST
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.MAP
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STRING
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import stapel.tools.codegen.ir.BlankMode
import stapel.tools.codegen.ir.Diagnostics
import stapel.tools.codegen.ir.EnumDecl
import stapel.tools.codegen.ir.ModelRole
import stapel.tools.codegen.ir.ObjectDecl
import stapel.tools.codegen.ir.PageStyle
import stapel.tools.codegen.ir.Property
import stapel.tools.codegen.ir.ScalarKind
import stapel.tools.codegen.ir.TypeDecl
import stapel.tools.codegen.ir.TypeRef
import stapel.tools.codegen.ir.UnionDecl
import stapel.tools.codegen.ir.UnionSelector
import stapel.tools.codegen.RuntimeNames as R

/** Model declarations (§5.1): data classes, wire enums, unions. */
internal class ModelEmitter(
    private val modelPkg: String,
    private val types: Map<String, TypeDecl>,
    private val diagnostics: Diagnostics,
) {
    fun className(name: String): ClassName = ClassName(modelPkg, name)

    fun typeName(ref: TypeRef, nullable: Boolean = false, requestSide: Boolean = false): TypeName {
        val t: TypeName = when (ref) {
            is TypeRef.Named -> className(ref.name)
            is TypeRef.Scalar -> when (ref.kind) {
                ScalarKind.STRING -> STRING
                ScalarKind.BOOLEAN -> BOOLEAN
                ScalarKind.INT -> INT
                ScalarKind.LONG -> LONG
                ScalarKind.DOUBLE -> DOUBLE
                ScalarKind.DECIMAL -> R.Decimal
                ScalarKind.INSTANT -> R.Instant
                ScalarKind.LOCAL_DATE -> R.LocalDate
                ScalarKind.BINARY -> if (requestSide) R.FilePart else STRING
                ScalarKind.CURSOR -> R.PageCursor
            }
            is TypeRef.ListOf -> LIST.parameterizedBy(typeName(ref.item, requestSide = requestSide))
            is TypeRef.MapOf -> MAP.parameterizedBy(STRING, typeName(ref.value, requestSide = requestSide))
            TypeRef.Json -> R.JsonElement
            TypeRef.JsonObj -> R.JsonObject
            TypeRef.JsonScalar -> R.JsonPrimitive
        }
        return if (nullable) t.copy(nullable = true) else t
    }

    /** The declaration and its internal file-level helpers (serializers), in file order. */
    fun emit(decl: TypeDecl): List<TypeSpec> = when (decl) {
        is ObjectDecl -> listOf(objectSpec(decl))
        is EnumDecl -> enumSpecs(decl)
        is UnionDecl -> unionSpecs(decl)
    }

    private fun helper(owner: String, suffix: String): ClassName = ClassName(modelPkg, owner + suffix)

    // ---- objects ---------------------------------------------------------------------------------------

    private fun propertyDoc(p: Property, role: ModelRole): String? {
        val parts = mutableListOf<String>()
        p.doc?.let { parts += KDoc.escape(it) }
        constraintsLine(p)?.let { parts += it }
        if (p.readOnly && role != ModelRole.REQUEST && role != ModelRole.PATCH) parts += "Read-only."
        p.default?.let { parts += "Default: `${it.replace("`", "'")}`." }
        p.example?.let { ex -> if (!ex.contains('\n') && ex.length <= 200) parts += "Example: `${ex.replace("`", "'")}`" }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    private fun constraintsLine(p: Property): String? {
        val c = p.constraints
        val items = listOfNotNull(
            c.maxLength?.let { "max length $it" },
            c.minLength?.let { "min length $it" },
            c.minimum?.let { "minimum $it" },
            c.maximum?.let { "maximum $it" },
            c.maxItems?.let { "max items $it" },
            c.pattern?.let { "pattern `" + it.replace("`", "'").replace("*/", "*&#47;").replace("/*", "/&#42;") + "`" },
        )
        return items.takeIf { it.isNotEmpty() }?.joinToString("; ", prefix = "Constraints: ", postfix = ".")
    }

    private fun objectSpec(d: ObjectDecl): TypeSpec {
        val name = className(d.name)
        val page = d.page
        val reserved = setOf("items", "nextCursor", "prevCursor", "total")
        val pageUsable = page != null && d.properties.none { it.name in reserved && !(it.name == "items" && page.style == PageStyle.ANCHOR) }
        if (page != null && !pageUsable) {
            diagnostics.warn("W-PAGINATION-UNRECOGNIZED", d.pointer, "envelope field names clash with Page members")
        }
        val builder = if (d.properties.isEmpty()) {
            TypeSpec.objectBuilder(name).addModifiers(KModifier.PUBLIC, KModifier.DATA)
        } else {
            TypeSpec.classBuilder(name).addModifiers(KModifier.PUBLIC, KModifier.DATA)
        }
        builder.addAnnotation(R.Serializable)
        (d.doc?.let(KDoc::escape) ?: d.generatedDoc)?.let { builder.addKdoc("%L", it) }
        d.implements.forEach { builder.addSuperinterface(className(it)) }
        if (pageUsable) builder.addSuperinterface(R.Page.parameterizedBy(typeName(page.itemType)))
        if (d.properties.isNotEmpty()) {
            val ctor = FunSpec.constructorBuilder()
            for (p in d.properties) {
                val type = propertyType(p, d.role)
                val param = ParameterSpec.builder(p.name, type)
                defaultOf(p, d.role)?.let { param.defaultValue(it) }
                ctor.addParameter(param.build())
                val prop = PropertySpec.builder(p.name, type).initializer(p.name)
                if (pageUsable && page.style == PageStyle.ANCHOR && p.name == "items") prop.addModifiers(KModifier.OVERRIDE) else prop.addModifiers(KModifier.PUBLIC)
                prop.addAnnotation(AnnotationSpec.builder(R.SerialName).addMember("%S", p.wireName).build())
                blankSerializer(p, d.role)?.let { prop.addAnnotation(AnnotationSpec.builder(R.Serializable).addMember("with = %T::class", it).build()) }
                propertyDoc(p, d.role)?.let { prop.addKdoc("%L", it) }
                builder.addProperty(prop.build())
            }
            builder.primaryConstructor(ctor.build())
        }
        if (pageUsable) addPageMembers(builder, d, page.style)
        if (d.role == ModelRole.REQUEST || d.role == ModelRole.PATCH) constraintConstants(d)?.let { builder.addType(it) }
        return builder.build()
    }

    private fun propertyType(p: Property, role: ModelRole): TypeName {
        val requestSide = role == ModelRole.REQUEST || role == ModelRole.PATCH
        val base = typeName(p.type, requestSide = requestSide)
        return when {
            role == ModelRole.PATCH -> R.Maybe.parameterizedBy(base.copy(nullable = p.nullable))
            p.required -> base.copy(nullable = p.nullable)
            else -> base.copy(nullable = true)
        }
    }

    private fun defaultOf(p: Property, role: ModelRole): CodeBlock? = when {
        role == ModelRole.PATCH -> CodeBlock.of("%T", R.MaybeAbsent)
        p.required -> null
        else -> CodeBlock.of("null")
    }

    private fun blankSerializer(p: Property, role: ModelRole): ClassName? {
        if (p.blank == BlankMode.NONE || role == ModelRole.PATCH) return null
        val enum = (p.type as? TypeRef.Named)?.name ?: return null
        if (types[enum] !is EnumDecl) return null
        return helper(enum, if (p.blank == BlankMode.BLANK_OR_NULL) "BlankOrNull" else "BlankForNull")
    }

    private fun constraintConstants(d: ObjectDecl): TypeSpec? {
        val companion = TypeSpec.companionObjectBuilder().addModifiers(KModifier.PUBLIC)
        var any = false
        for (p in d.properties) {
            val prefix = Names.words(p.wireName).joinToString("_") { it.uppercase() }
            val c = p.constraints
            fun add(suffix: String, type: TypeName, format: String, value: Any, what: String) {
                companion.addProperty(
                    PropertySpec.builder("${prefix}_$suffix", type, KModifier.PUBLIC, KModifier.CONST)
                        .initializer(format, value).addKdoc("%L", "$what of `${p.wireName}`.").build(),
                )
                any = true
            }
            c.maxLength?.let { add("MAX_LENGTH", INT, "%L", it, "Maximum length") }
            c.minLength?.let { add("MIN_LENGTH", INT, "%L", it, "Minimum length") }
            c.maxItems?.let { add("MAX_ITEMS", INT, "%L", it, "Maximum number of items") }
            c.pattern?.let { add("PATTERN", STRING, "%S", it, "Pattern") }
            c.minimum?.let { numberConst(it)?.let { (t, v) -> add("MINIMUM", t, "%L", v, "Minimum") } }
            c.maximum?.let { numberConst(it)?.let { (t, v) -> add("MAXIMUM", t, "%L", v, "Maximum") } }
        }
        return if (any) companion.addKdoc("%L", "Validation limits declared by the contract.").build() else null
    }

    private fun numberConst(text: String): Pair<TypeName, String>? {
        text.toLongOrNull()?.let { return LONG to "${it}L" }
        text.toDoubleOrNull()?.let { if (it.isFinite()) return DOUBLE to it.toString() }
        return null
    }

    private fun addPageMembers(builder: TypeSpec.Builder, d: ObjectDecl, style: PageStyle) {
        val props = d.properties.associateBy { it.wireName }
        fun ref(wire: String): Property? = props[wire]
        fun boolExpr(p: Property): String = if (p.nullable || !p.required) "${p.name} == true" else p.name
        fun intExpr(p: Property): String = if (p.nullable || !p.required) "(${p.name} ?: 0)" else p.name
        fun cursorOf(p: Property): String {
            val nullable = p.nullable || !p.required
            return when ((p.type as? TypeRef.Scalar)?.kind) {
                ScalarKind.CURSOR -> p.name
                ScalarKind.STRING -> if (nullable) "${p.name}?.let { PageCursor(it) }" else "PageCursor(${p.name})"
                else -> if (nullable) "${p.name}?.let { PageCursor(it.toString()) }" else "PageCursor(${p.name}.toString())"
            }
        }
        val item = typeName(d.page!!.itemType)
        val items = LIST.parameterizedBy(item)
        val cursor = R.PageCursor.copy(nullable = true)
        var next = "null"
        var prev = "null"
        var total = "null"
        var itemsExpr: String? = "results"
        when (style) {
            PageStyle.ANCHOR -> {
                itemsExpr = null
                val hasNext = ref("has_next")
                next = ref("next_anchor")?.let { a -> if (hasNext != null) "if (${boolExpr(hasNext)}) ${cursorOf(a)} else null" else cursorOf(a) } ?: "null"
                prev = ref("prev_anchor")?.let { a -> ref("has_prev")?.let { "if (${boolExpr(it)}) ${cursorOf(a)} else null" } ?: cursorOf(a) } ?: "null"
                total = ref("count")?.name ?: "null"
            }
            PageStyle.PAGE_NUMBER -> {
                next = ref("next")?.let(::cursorOf) ?: "null"
                total = ref("count")?.name ?: "null"
            }
            PageStyle.REVISION_PAGE -> {
                val pg = ref("pagination")!!
                val inner = (types[(pg.type as TypeRef.Named).name] as ObjectDecl).properties.associateBy { it.wireName }
                val page = inner.getValue("page")
                val pageExpr = if (page.nullable || !page.required) "(pagination.${page.name} ?: 0)" else "pagination.${page.name}"
                next = "if (pagination.${boolExpr(inner.getValue("has_next"))}) PageCursor(($pageExpr + 1).toString()) else null"
                prev = "if (pagination.${boolExpr(inner.getValue("has_previous"))}) PageCursor(($pageExpr - 1).toString()) else null"
                total = "pagination." + inner.getValue("total_count").name
            }
            PageStyle.LIMIT_OFFSET -> {
                val offset = intExpr(ref("offset")!!)
                val limit = intExpr(ref("limit")!!)
                val count = intExpr(ref("count")!!)
                next = "if ($offset + $limit < $count) PageCursor(($offset + $limit).toString()) else null"
                prev = "if ($offset > 0) PageCursor(maxOf($offset - $limit, 0).toString()) else null"
                total = ref("count")!!.name
            }
        }
        fun getter(expr: String) = FunSpec.getterBuilder().addCode("return %L", expr.replace("PageCursor(", "%T(").let { CodeBlock.of(it, *Array(Regex("%T").findAll(it).count()) { R.PageCursor }) }).build()
        itemsExpr?.let { builder.addProperty(PropertySpec.builder("items", items, KModifier.OVERRIDE).getter(getter(it)).build()) }
        builder.addProperty(PropertySpec.builder("nextCursor", cursor, KModifier.OVERRIDE).getter(getter(next)).build())
        builder.addProperty(PropertySpec.builder("prevCursor", cursor, KModifier.OVERRIDE).getter(getter(prev)).build())
        builder.addProperty(PropertySpec.builder("total", INT.copy(nullable = true), KModifier.OVERRIDE).getter(getter(total)).build())
    }

    // ---- enums -------------------------------------------------------------------------------------------

    private fun enumSpecs(d: EnumDecl): List<TypeSpec> {
        val name = className(d.name)
        val wireProp = { v: String ->
            PropertySpec.builder("wire", STRING, KModifier.OVERRIDE)
                .getter(FunSpec.getterBuilder().addStatement("return %S", v).build()).build()
        }
        val builder = TypeSpec.interfaceBuilder(name).addModifiers(KModifier.PUBLIC, KModifier.SEALED)
            .addSuperinterface(R.WireEnum)
            .addAnnotation(AnnotationSpec.builder(R.Serializable).addMember("with = %T::class", helper(d.name, "Serializer")).build())
        d.doc?.let { builder.addKdoc("%L", KDoc.escape(it)) }
        for (e in d.entries) {
            val obj = TypeSpec.objectBuilder(e.name).addModifiers(KModifier.PUBLIC, KModifier.DATA)
                .addSuperinterface(name).addProperty(wireProp(e.wire))
            e.doc?.let { obj.addKdoc("%L", KDoc.escape(it)) }
            builder.addType(obj.build())
        }
        builder.addType(
            TypeSpec.classBuilder("Unknown").addModifiers(KModifier.PUBLIC, KModifier.DATA)
                .addKdoc("A value this client version does not know.")
                .addSuperinterface(name)
                .primaryConstructor(FunSpec.constructorBuilder().addParameter("wire", STRING).build())
                .addProperty(PropertySpec.builder("wire", STRING, KModifier.OVERRIDE).initializer("wire").build())
                .build(),
        )
        val known = CodeBlock.builder().add("listOf(")
        d.entries.forEachIndexed { i, e -> if (i > 0) known.add(", "); known.add("%N", e.name) }
        known.add(")")
        builder.addType(
            TypeSpec.companionObjectBuilder().addModifiers(KModifier.PUBLIC)
                .addKdoc("Known values and the wire-value lookup.")
                .addProperty(
                    PropertySpec.builder("known", LIST.parameterizedBy(name), KModifier.PUBLIC)
                        .addKdoc("Every value this client version knows, in contract order.")
                        .initializer(known.build()).build(),
                )
                .addFunction(
                    FunSpec.builder("of").addModifiers(KModifier.PUBLIC).addParameter("wire", STRING).returns(name)
                        .addKdoc("The value for [wire]; `Unknown` when this client version does not know it.")
                        .addStatement("return known.firstOrNull { it.wire == wire } ?: Unknown(wire)").build(),
                )
                .build(),
        )
        val out = mutableListOf(builder.build())
        out += TypeSpec.objectBuilder(helper(d.name, "Serializer")).addModifiers(KModifier.INTERNAL)
            .superclass(R.WireEnumSerializer.parameterizedBy(name))
            .addSuperclassConstructorParameter("%S", "$modelPkg.${d.name}")
            .addSuperclassConstructorParameter("{ %T.of(it) }", name)
            .build()
        for ((suffix, nullAsBlank) in listOf("BlankOrNull" to false, "BlankForNull" to true)) {
            out += TypeSpec.objectBuilder(helper(d.name, suffix)).addModifiers(KModifier.INTERNAL)
                .superclass(R.BlankAsNullSerializer.parameterizedBy(name))
                .addSuperclassConstructorParameter("%T", helper(d.name, "Serializer"))
                .addSuperclassConstructorParameter("nullAsBlank = %L", nullAsBlank)
                .build()
        }
        return out
    }

    // ---- unions ------------------------------------------------------------------------------------------

    private fun unionSpecs(d: UnionDecl): List<TypeSpec> {
        val name = className(d.name)
        val builder = TypeSpec.interfaceBuilder(name).addModifiers(KModifier.PUBLIC, KModifier.SEALED)
            .addAnnotation(AnnotationSpec.builder(R.Serializable).addMember("with = %T::class", helper(d.name, "Serializer")).build())
        val by = when (val s = d.selector) {
            is UnionSelector.ByProperty -> "selected by `${s.wireProperty}`"
            is UnionSelector.ByKeys -> "selected by the keys present"
        }
        val doc = (d.doc?.let { KDoc.escape(it) + "\n\n" } ?: "") +
            "One of " + d.variants.joinToString(", ") { "[$it]" } + ", $by."
        builder.addKdoc("%L", doc)
        if (d.hasUnknown) {
            builder.addType(
                TypeSpec.classBuilder("Unknown").addModifiers(KModifier.PUBLIC, KModifier.DATA)
                    .addKdoc("A variant this client version does not know.")
                    .addSuperinterface(name)
                    .primaryConstructor(FunSpec.constructorBuilder().addParameter("raw", R.JsonObject).build())
                    .addProperty(PropertySpec.builder("raw", R.JsonObject, KModifier.PUBLIC).initializer("raw").addKdoc("The object as received.").build())
                    .build(),
            )
        }
        val ser = R.KSerializer.parameterizedBy(com.squareup.kotlinpoet.WildcardTypeName.producerOf(name))
        val select = CodeBlock.builder()
        when (val s = d.selector) {
            is UnionSelector.ByProperty -> {
                select.beginControlFlow("return when ((obj[%S] as? %T)?.content)", s.wireProperty, R.JsonPrimitive)
                s.values.groupBy({ it.second }, { it.first }).forEach { (variant, values) ->
                    select.addStatement("%L -> %T.serializer()", values.joinToString(", ") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\"" }, className(variant))
                }
                select.addStatement("else -> %L", s.fallback?.let { CodeBlock.of("%T.serializer()", className(it)) } ?: CodeBlock.of("null"))
                select.endControlFlow()
            }
            is UnionSelector.ByKeys -> {
                select.beginControlFlow("return when")
                s.probes.forEach { (key, variant) -> select.addStatement("%S in obj -> %T.serializer()", key, className(variant)) }
                select.addStatement("else -> %L", s.fallback?.let { CodeBlock.of("%T.serializer()", className(it)) } ?: CodeBlock.of("null"))
                select.endControlFlow()
            }
        }
        val serializerOf = CodeBlock.builder().beginControlFlow("return when (value)")
        d.variants.forEach { serializerOf.addStatement("is %T -> %T.serializer()", className(it), className(it)) }
        if (d.hasUnknown) serializerOf.addStatement("is %T -> null", name.nestedClass("Unknown"))
        serializerOf.endControlFlow()
        val serializer = TypeSpec.objectBuilder(helper(d.name, "Serializer")).addModifiers(KModifier.INTERNAL)
            .superclass(R.UnionSerializer.parameterizedBy(name))
            .addSuperclassConstructorParameter("%S", "$modelPkg.${d.name}")
            .addFunction(FunSpec.builder("select").addModifiers(KModifier.OVERRIDE).addParameter("obj", R.JsonObject).returns(ser.copy(nullable = true)).addCode(select.build()).build())
            .addFunction(FunSpec.builder("serializerOf").addModifiers(KModifier.OVERRIDE).addParameter("value", name).returns(ser.copy(nullable = true)).addCode(serializerOf.build()).build())
        if (d.hasUnknown) {
            serializer.addFunction(FunSpec.builder("unknown").addModifiers(KModifier.OVERRIDE).addParameter("obj", R.JsonObject).returns(name).addStatement("return %T(obj)", name.nestedClass("Unknown")).build())
            serializer.addFunction(FunSpec.builder("rawOf").addModifiers(KModifier.OVERRIDE).addParameter("value", name).returns(R.JsonObject.copy(nullable = true)).addStatement("return (value as? %T)?.raw", name.nestedClass("Unknown")).build())
        }
        return listOf(builder.build(), serializer.build())
    }

    companion object {
        val unitName: ClassName = Unit::class.asClassName()
    }
}
