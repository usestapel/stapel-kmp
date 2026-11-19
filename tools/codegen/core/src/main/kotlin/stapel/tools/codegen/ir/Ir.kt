package stapel.tools.codegen.ir

/**
 * Intermediate model of one brick's contract. The emitter never reads JSON; the parser never writes
 * Kotlin. Every list is in a deterministic order (operations by id, types by name).
 */
data class ApiModel(
    val brick: String,
    val stamp: ContractStamp,
    val operations: List<Operation>,
    val types: List<TypeDecl>,
    val errors: List<ErrorCode>,
    val diagnostics: Diagnostics,
)

/** Identity of the contract the model was built from; stamped into every generated header. */
data class ContractStamp(
    val module: String,
    val version: String,
    val ref: String,
    val schemaSha256: String,
    val profile: Int,
)

sealed interface TypeRef {
    /** A generated declaration in the model package. */
    data class Named(val name: String) : TypeRef

    data class Scalar(val kind: ScalarKind) : TypeRef

    data class ListOf(val item: TypeRef) : TypeRef

    /** JSON object with typed values; keys are always strings. */
    data class MapOf(val value: TypeRef) : TypeRef

    /** Any JSON value. */
    data object Json : TypeRef

    /** Any JSON object. */
    data object JsonObj : TypeRef

    /** Any JSON scalar. */
    data object JsonScalar : TypeRef
}

enum class ScalarKind { STRING, BOOLEAN, INT, LONG, DOUBLE, DECIMAL, INSTANT, LOCAL_DATE, BINARY, CURSOR }

/** How a `oneOf [XEnum, BlankEnum, NullEnum]` field treats `""`. */
enum class BlankMode { NONE, BLANK_OR_NULL, BLANK_FOR_NULL }

data class Constraints(
    val maxLength: Int? = null,
    val minLength: Int? = null,
    val pattern: String? = null,
    val minimum: String? = null,
    val maximum: String? = null,
    val maxItems: Int? = null,
) {
    val isEmpty: Boolean get() = this == Constraints()
}

data class Property(
    val name: String,
    val wireName: String,
    val type: TypeRef,
    val nullable: Boolean,
    val required: Boolean,
    val readOnly: Boolean,
    val writeOnly: Boolean,
    val doc: String?,
    val constraints: Constraints,
    val example: String?,
    val default: String?,
    val blank: BlankMode = BlankMode.NONE,
    val pointer: String,
)

sealed interface TypeDecl {
    val name: String
    val wireName: String
    val doc: String?
    val pointer: String
}

/** Pagination envelope recognized on an object. */
data class PageInfo(val style: PageStyle, val itemType: TypeRef)

enum class PageStyle { ANCHOR, PAGE_NUMBER, REVISION_PAGE, LIMIT_OFFSET }

enum class ModelRole { RESPONSE, REQUEST, PATCH, BOTH }

data class ObjectDecl(
    override val name: String,
    override val wireName: String,
    override val doc: String?,
    override val pointer: String,
    val properties: List<Property>,
    val role: ModelRole = ModelRole.BOTH,
    val implements: List<String> = emptyList(),
    val page: PageInfo? = null,
    /** Generator text for constructs the contract does not document (pagination envelopes). */
    val generatedDoc: String? = null,
) : TypeDecl

data class EnumEntry(val name: String, val wire: String, val doc: String?)

data class EnumDecl(
    override val name: String,
    override val wireName: String,
    override val doc: String?,
    override val pointer: String,
    val entries: List<EnumEntry>,
) : TypeDecl

sealed interface UnionSelector {
    /** Variant by the string value of one property. */
    data class ByProperty(val wireProperty: String, val values: List<Pair<String, String>>, val fallback: String?) : UnionSelector

    /** Variant by the presence of a key, probed in order. */
    data class ByKeys(val probes: List<Pair<String, String>>, val fallback: String?) : UnionSelector
}

data class UnionDecl(
    override val name: String,
    override val wireName: String,
    override val doc: String?,
    override val pointer: String,
    val variants: List<String>,
    val selector: UnionSelector,
    val hasUnknown: Boolean,
) : TypeDecl

enum class HttpMethod { GET, POST, PUT, PATCH, DELETE }

enum class AuthRequirement { REQUIRED, OPTIONAL, NONE }

data class Parameter(
    val name: String,
    val wireName: String,
    val location: Location,
    val type: TypeRef,
    val required: Boolean,
    val doc: String?,
    val pointer: String,
) {
    enum class Location { PATH, QUERY, HEADER }
}

sealed interface Body {
    val required: Boolean

    data class Json(val type: TypeRef, override val required: Boolean, val doc: String?) : Body

    /** Multipart or urlencoded body sent part by part. */
    data class Parts(val parts: List<Property>, val multipart: Boolean, override val required: Boolean) : Body
}

sealed interface Success {
    val statuses: List<Int>

    data class Json(val type: TypeRef, override val statuses: List<Int>) : Success

    data class Empty(override val statuses: List<Int>) : Success

    data class Text(val mediaType: String, override val statuses: List<Int>) : Success

    data class Binary(val mediaTypes: List<String>, override val statuses: List<Int>) : Success

    /** Browser redirect (3xx only): no request is made, a URL is built. */
    data class Redirect(override val statuses: List<Int>) : Success
}

/** Error codes the operation may return, and where that knowledge came from. */
sealed interface ErrorBinding {
    data class Bound(val codes: List<String>, val source: String, val unboundStatuses: List<Int>) : ErrorBinding

    data class Unbound(val statuses: List<Int>) : ErrorBinding
}

data class Pagination(val style: PageStyle, val cursorParam: String, val itemType: TypeRef)

data class Captcha(val action: String, val header: String?, val field: String?)

data class Example(val title: String?, val json: String)

data class Operation(
    val id: String,
    val name: String,
    val method: HttpMethod,
    val path: String,
    val tag: String,
    val summary: String?,
    val description: String?,
    val permissions: List<String>,
    val auth: AuthRequirement,
    val parameters: List<Parameter>,
    val body: Body?,
    val success: Success,
    val errors: ErrorBinding,
    val pagination: Pagination?,
    val verificationScope: String?,
    val captcha: Captcha?,
    val capability: String?,
    val flows: List<String>,
    val gatedBy: List<String>,
    val requestExample: Example?,
    val responseExample: Example?,
    val pointer: String,
)

data class ErrorCode(
    val code: String,
    val status: Int,
    val params: List<String>,
    val paramTypes: Map<String, String>,
    val remediation: String,
    val en: String,
    val owner: String,
)
