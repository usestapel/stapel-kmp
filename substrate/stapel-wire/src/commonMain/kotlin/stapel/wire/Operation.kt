package stapel.wire

import kotlinx.serialization.KSerializer
import stapel.result.ErrorEnvelope
import stapel.result.StapelError
import stapel.result.TransportFailure

/** Whether an operation needs a session, according to the contract's `security` declaration. */
public enum class AuthRequirement {
    /** Only with a session: `security: [{scheme}]`. */
    Required,

    /** Works both ways; the credential is attached when a session exists: `security: [{scheme}, {}]`. */
    Optional,

    /** Public; no credential is attached: `security: [{}]`. */
    None,
}

/** Whether an operation reads (cacheable) or changes state. */
public enum class OperationKind {
    /** A `GET`: no side effects, cacheable. */
    Query,

    /** Any other method. */
    Mutation,
}

/** How the request body is encoded. */
public enum class BodyEncoding {
    /** No body. */
    None,

    /** `application/json` with [OperationSpec.body]. */
    Json,

    /** `multipart/form-data` from [OperationCall.parts]. */
    Multipart,

    /** `application/x-www-form-urlencoded` from [OperationCall.parts] (text parts only). */
    Form,
}

/** What a successful response carries. */
public enum class ResponseKind {
    /** A JSON body decoded with the status's serializer. */
    Json,

    /** No body; the value is `Unit`. */
    Empty,

    /** A text body (`text/csv`, feeds, YAML) returned as a `String`. */
    Text,

    /** A binary body handed to a `consume` block. */
    Binary,
}

/** The captcha a protected operation needs (`x-stapel-captcha`), sent by the transport's captcha seam. */
public class CaptchaRequirement(
    /** Action name the captcha token is minted for. */
    public val action: String,
    /** Header that carries the token, when the operation takes it as a header. */
    public val header: String?,
    /** Body field that carries the token, when the operation takes it in the body. */
    public val field: String?,
)

/**
 * Everything a transport needs to execute one contract operation. Generated code holds one value per
 * operation in `<Brick>Operations`; a transport never inspects anything else.
 *
 * @param B request body type (`Unit` when there is none).
 * @param T success value type.
 * @param E the operation's error type (`<Op>Error`).
 */
public class OperationSpec<B, T, out E : StapelError>(
    /** Contract `operationId`, the stable identity. */
    public val id: String,
    /** Brick key, e.g. `"auth"`; selects the host prefix in the client configuration. */
    public val module: String,
    /** HTTP method in upper case. */
    public val method: String,
    /** Canonical path template with `{name}` placeholders, e.g. `/auth/api/v1/sessions/{session_id}/`. */
    public val path: String,
    /** Read or write. */
    public val kind: OperationKind,
    /** Session requirement declared by the contract. */
    public val auth: AuthRequirement,
    /** Body encoding. */
    public val bodyEncoding: BodyEncoding,
    /** JSON serializer of the body when [bodyEncoding] is [BodyEncoding.Json]. */
    public val body: KSerializer<B>?,
    /** What a success carries. */
    public val responseKind: ResponseKind,
    /** Serializer per success status (for [ResponseKind.Json]); a status mapped to `null` has no body. */
    public val success: Map<Int, KSerializer<out T>?>,
    /** Builds the operation's error from a domain envelope or a transport failure. */
    public val errors: ErrorDecoder<E>,
    /** Step-up verification scope (`x-stapel-verification`), when the operation may demand one. */
    public val verificationScope: String? = null,
    /** Captcha the operation requires (`x-stapel-captcha`). */
    public val captcha: CaptchaRequirement? = null,
    /** Capability key (`x-stapel-capability`). */
    public val capability: String? = null,
)

/** One part of a multipart or form body. */
public sealed interface FormPart {
    /** Part name. */
    public val name: String

    /** A text field. */
    public class Text(
        override val name: String,
        /** The field's text. */
        public val value: String,
    ) : FormPart

    /** A file field. */
    public class File(
        override val name: String,
        /** The file sent. */
        public val file: FilePart,
    ) : FormPart
}

/**
 * The arguments of one call of an operation.
 *
 * @param B request body type.
 */
public class OperationCall<B>(
    /** Path parameter values in template order, already converted to text. */
    public val pathParams: List<String> = emptyList(),
    /** Query parameters in order; a `null` value is omitted; a list parameter repeats its key. */
    public val query: List<Pair<String, String?>> = emptyList(),
    /** Header parameters; a `null` value is omitted. */
    public val headers: List<Pair<String, String?>> = emptyList(),
    /** JSON body, for [BodyEncoding.Json]. */
    public val body: B? = null,
    /** Parts, for [BodyEncoding.Multipart] and [BodyEncoding.Form]. */
    public val parts: List<FormPart> = emptyList(),
    /** Upload progress callback `(sent, total)`, for multipart bodies. */
    public val onProgress: ((sent: Long, total: Long?) -> Unit)? = null,
)

/**
 * Maps the two failure families onto an operation's error type: a coded refusal of the server
 * ([domain]) and a failure of the shared transport family ([transport]). Generated per brick.
 *
 * @param E the operation's error type.
 */
public interface ErrorDecoder<out E : StapelError> {
    /** The error for a decoded Stapel envelope whose status is not a transport concern. */
    public fun domain(envelope: ErrorEnvelope): E

    /** The error carrying [failure]. */
    public fun transport(failure: TransportFailure): E
}
