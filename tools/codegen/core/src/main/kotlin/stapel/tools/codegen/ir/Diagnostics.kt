package stapel.tools.codegen.ir

enum class Severity { WARNING, ERROR }

/** One finding about the contract, keyed by a stable code (`W-DOC-PROPERTY`, `E-UNION-AMBIGUOUS`). */
data class Diagnostic(val code: String, val pointer: String, val message: String) {
    val severity: Severity get() = if (code.startsWith("E-")) Severity.ERROR else Severity.WARNING
}

/** Collector shared by the parser and the emitter; read back sorted, duplicates removed. */
class Diagnostics {
    private val items = LinkedHashSet<Diagnostic>()

    fun warn(code: String, pointer: String, message: String) {
        require(code.startsWith("W-")) { code }
        items += Diagnostic(code, pointer, message)
    }

    fun error(code: String, pointer: String, message: String) {
        require(code.startsWith("E-")) { code }
        items += Diagnostic(code, pointer, message)
    }

    val all: List<Diagnostic> get() = items.sortedWith(compareBy({ it.code }, { it.pointer }, { it.message }))

    val errors: List<Diagnostic> get() = all.filter { it.severity == Severity.ERROR }

    fun counts(): Map<String, Int> = all.groupingBy { it.code }.eachCount().toSortedMap()

    companion object {
        /** Codes of undocumented elements: the KDoc ratchet (R11). */
        val DOC_CODES: Set<String> = setOf("W-DOC-OPERATION", "W-DOC-PARAM", "W-DOC-TYPE", "W-DOC-PROPERTY")
    }
}
