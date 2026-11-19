package stapel.tools.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.MemberName

/**
 * Fully-qualified names of the runtime the generated code compiles against (`stapel-wire`,
 * `stapel-result`, kotlinx.serialization). A rename in the runtime is a one-line change here plus
 * regeneration.
 */
object RuntimeNames {
    /** Value of `stapel.wire.CODEGEN_RUNTIME_ABI` this generator targets. */
    const val RUNTIME_ABI: Int = 1

    /** Version of the generator, stamped into every generated header. */
    const val GENERATOR_VERSION: String = "0.1.0"

    private const val WIRE = "stapel.wire"
    private const val RESULT = "stapel.result"
    private const val SER = "kotlinx.serialization"
    private const val JSON = "kotlinx.serialization.json"

    val Maybe = ClassName(WIRE, "Maybe")
    val MaybeAbsent = ClassName(WIRE, "Maybe", "Absent")
    val Decimal = ClassName(WIRE, "Decimal")
    val PageCursor = ClassName(WIRE, "PageCursor")
    val Page = ClassName(WIRE, "Page")
    val Pager = ClassName(WIRE, "Pager")
    val WireEnum = ClassName(WIRE, "WireEnum")
    val WireEnumSerializer = ClassName(WIRE, "WireEnumSerializer")
    val BlankAsNullSerializer = ClassName(WIRE, "BlankAsNullSerializer")
    val UnionSerializer = ClassName(WIRE, "UnionSerializer")
    val FilePart = ClassName(WIRE, "FilePart")
    val FormPart = ClassName(WIRE, "FormPart")
    val FormPartText = ClassName(WIRE, "FormPart", "Text")
    val FormPartFile = ClassName(WIRE, "FormPart", "File")
    val BinaryBody = ClassName(WIRE, "BinaryBody")
    val CacheKey = ClassName(WIRE, "CacheKey")
    val KeyPattern = ClassName(WIRE, "KeyPattern")
    val OperationSpec = ClassName(WIRE, "OperationSpec")
    val OperationCall = ClassName(WIRE, "OperationCall")
    val ApiTransport = ClassName(WIRE, "ApiTransport")
    val AuthRequirement = ClassName(WIRE, "AuthRequirement")
    val OperationKind = ClassName(WIRE, "OperationKind")
    val BodyEncoding = ClassName(WIRE, "BodyEncoding")
    val ResponseKind = ClassName(WIRE, "ResponseKind")
    val ErrorDecoder = ClassName(WIRE, "ErrorDecoder")
    val CaptchaRequirement = ClassName(WIRE, "CaptchaRequirement")

    val StapelResult = ClassName(RESULT, "StapelResult")
    val StapelError = ClassName(RESULT, "StapelError")
    val DomainError = ClassName(RESULT, "DomainError")
    val TransportCarrier = ClassName(RESULT, "TransportCarrier")
    val TransportFailure = ClassName(RESULT, "TransportFailure")
    val ErrorEnvelope = ClassName(RESULT, "ErrorEnvelope")
    val ErrorCode = ClassName(RESULT, "ErrorCode")
    val Remediation = ClassName(RESULT, "Remediation")
    val ErrorCatalog = ClassName("$RESULT.text", "ErrorCatalog")
    val ErrorSpec = ClassName("$RESULT.text", "ErrorSpec")
    val LocaleTag = ClassName("$RESULT.text", "LocaleTag")

    val Serializable = ClassName(SER, "Serializable")
    val SerialName = ClassName(SER, "SerialName")
    val KSerializer = ClassName(SER, "KSerializer")
    val serializer = MemberName(SER, "serializer")
    val JsonElement = ClassName(JSON, "JsonElement")
    val JsonObject = ClassName(JSON, "JsonObject")
    val JsonPrimitive = ClassName(JSON, "JsonPrimitive")
    val buildJsonObject = MemberName(JSON, "buildJsonObject")
    val put = MemberName(JSON, "put")

    val Instant = ClassName("kotlin.time", "Instant")
    val LocalDate = ClassName("kotlinx.datetime", "LocalDate")
}
