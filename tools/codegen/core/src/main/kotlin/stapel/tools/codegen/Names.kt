package stapel.tools.codegen

/** Naming rules of ARCH-CODEGEN §4. Wire names are never altered; only Kotlin names derive from them. */
object Names {
    private val KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is",
        "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof",
        "val", "var", "when", "while",
    )

    /** Kotlin identifiers that would clash with generated members of data classes or interfaces. */
    private val MEMBER_CLASHES = setOf("copy", "equals", "hashCode", "toString", "serializer", "Companion")

    fun words(text: String): List<String> {
        val out = mutableListOf<String>()
        text.split(Regex("[^A-Za-z0-9]+")).filter { it.isNotEmpty() }.forEach { chunk ->
            // Split camelCase boundaries but keep acronym runs ("TOTPChallenge" -> TOTP, Challenge).
            val parts = chunk.split(Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])"))
            out += parts.filter { it.isNotEmpty() }
        }
        return out
    }

    /** `is_email_verified` → `IsEmailVerified`; acronym casing of the source is kept. */
    fun pascal(text: String): String = words(text).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }

    /** `is_email_verified` → `isEmailVerified`; already-camel names pass through. */
    fun camel(text: String): String {
        val w = words(text)
        if (w.isEmpty()) return "value"
        val first = w.first().let { if (it.length > 1 && it.all { c -> c.isUpperCase() || c.isDigit() }) it.lowercase() else it.replaceFirstChar(Char::lowercaseChar) }
        return first + w.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    }

    /** Component name → type name: leading `_` dropped, `_` folded (`Patched_Org` → `PatchedOrg`). */
    fun typeName(component: String): String {
        val trimmed = component.trimStart('_')
        val parts = trimmed.split('_').filter { it.isNotEmpty() }
        val name = parts.joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        return if (name.first().isDigit()) "V$name" else name
    }

    /** Property or parameter identifier; keywords and member clashes get a trailing `_`. */
    fun member(wire: String): String {
        var n = camel(wire)
        if (n.first().isDigit()) n = "v$n"
        return if (n in KEYWORDS || n in MEMBER_CLASHES) n + "_" else n
    }

    /** Enum entry: PascalCase of the wire value; non-identifiers sanitized; a leading digit gets `V`. */
    fun enumEntry(wire: String): String {
        if (wire.isEmpty()) return "Blank"
        var n = words(wire).joinToString("") { w ->
            if (w.length > 1 && w.all { it.isUpperCase() || it.isDigit() }) w.lowercase().replaceFirstChar(Char::uppercaseChar)
            else w.replaceFirstChar(Char::uppercaseChar)
        }
        if (n.isEmpty()) n = "_" + wire.map { if (it.isLetterOrDigit()) it else '_' }.joinToString("")
        if (n.first().isDigit()) n = "V$n"
        if (n == "Unknown" || n == "Companion" || n == "Serializer") n += "Value"
        return n
    }

    /** `error.400.invalid_code_attempts` → `InvalidCodeAttempts`. */
    fun errorClass(code: String): String = pascal(code.split('.').drop(2).joinToString("_"))

    /**
     * Mechanical function name from an operationId (§4.1): module prefix stripped, DRF action suffix
     * mapped, positional `_<n>` replaced by `By<PathParams>`.
     */
    fun operationName(
        operationId: String,
        module: String,
        pathParams: List<String>,
        stemShared: (String) -> Boolean,
    ): String {
        var id = operationId
        val prefix = Regex("^${Regex.escape(module)}_api_v(\\d+)_")
        var version: String? = null
        prefix.find(id)?.let { m ->
            if (m.groupValues[1] != "1") version = "V" + m.groupValues[1]
            id = id.substring(m.range.last + 1)
        }
        var positional = false
        Regex("_(\\d+)$").find(id)?.let {
            positional = true
            id = id.substring(0, it.range.first)
        }
        val actions = listOf(
            "_partial_update" to "patch", "_list" to "list", "_retrieve" to "get", "_update" to "update",
            "_destroy" to "delete", "_create" to "create",
        )
        var name: String? = null
        for ((suffix, verb) in actions) {
            if (id.endsWith(suffix)) {
                val stem = id.removeSuffix(suffix)
                name = when {
                    verb == "create" && !stemShared(stem) -> camel(stem)
                    else -> verb + pascal(stem)
                }
                break
            }
        }
        var result = name ?: camel(id)
        if (positional) result += "By" + pathParams.joinToString("") { pascal(it) }
        version?.let { result += it }
        return member(result).removeSuffix("_").let { if (it in KEYWORDS) it + "_" else it }
    }

    /** DRF action stem of an operationId (for the shared-stem test of `_create`). */
    fun stem(operationId: String, module: String): String {
        var id = Regex("^${Regex.escape(module)}_api_v\\d+_").replace(operationId, "")
        id = Regex("_\\d+$").replace(id, "")
        for (suffix in listOf("_partial_update", "_list", "_retrieve", "_update", "_destroy", "_create")) {
            if (id.endsWith(suffix)) return id.removeSuffix(suffix)
        }
        return id
    }
}
