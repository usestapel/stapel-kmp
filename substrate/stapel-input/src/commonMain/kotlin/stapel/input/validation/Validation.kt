package stapel.input.validation

/**
 * One reason a value is refused: an i18n key and its parameters — never display text. The UI resolves
 * it through `StapelStrings.t(issue.key, issue.params)`; a server-declared key (`error.400.…`) passes
 * through unchanged.
 */
public class FieldIssue(
    /** The i18n key ([InputIssueKeys] or a key from data). */
    public val key: String,
    /** Named parameters for the key's `{placeholders}`. */
    public val params: Map<String, String> = emptyMap(),
) {
    /** Value equality. */
    override fun equals(other: Any?): Boolean = other is FieldIssue && other.key == key && other.params == params

    /** Consistent with [equals]. */
    override fun hashCode(): Int = key.hashCode() * 31 + params.hashCode()

    /** For diagnostics. */
    override fun toString(): String = if (params.isEmpty()) key else "$key$params"
}

/** The verdict of a [Validator]. */
public sealed interface ValidationResult {
    /** The issues found; empty when valid. */
    public val issues: List<FieldIssue>

    /** True when there are no issues. */
    public val isValid: Boolean get() = issues.isEmpty()

    /** The value is accepted. */
    public data object Valid : ValidationResult {
        override val issues: List<FieldIssue> get() = emptyList()
    }

    /** The value is refused for [issues] (never empty). */
    public class Invalid(override val issues: List<FieldIssue>) : ValidationResult {
        /** Value equality. */
        override fun equals(other: Any?): Boolean = other is Invalid && other.issues == issues

        /** Consistent with [equals]. */
        override fun hashCode(): Int = issues.hashCode()

        /** For diagnostics. */
        override fun toString(): String = "Invalid($issues)"
    }

    /** Factories. */
    public companion object {
        /** [Valid] for no issues, else [Invalid]. */
        public fun of(issues: List<FieldIssue>): ValidationResult = if (issues.isEmpty()) Valid else Invalid(issues)

        /** [Invalid] with one issue. */
        public fun invalid(key: String, params: Map<String, String> = emptyMap()): ValidationResult =
            Invalid(listOf(FieldIssue(key, params)))
    }
}

/** Checks a value. Must not throw; a validator that cannot judge returns [ValidationResult.Valid]. */
public fun interface Validator<in T> {
    /** The verdict on [value]. */
    public fun validate(value: T): ValidationResult
}

/** Both validators run; the issues of both are reported. */
public infix fun <T> Validator<T>.and(other: Validator<T>): Validator<T> =
    Validator { v -> ValidationResult.of(validate(v).issues + other.validate(v).issues) }

/** [other] runs only when this one passes (the first failure is reported alone). */
public infix fun <T> Validator<T>.then(other: Validator<T>): Validator<T> =
    Validator { v -> validate(v).let { if (it.isValid) other.validate(v) else it } }

/** All [validators], issues of every one reported; no validators means always valid. */
public fun <T> allOf(validators: List<Validator<T>>): Validator<T> =
    Validator { v -> ValidationResult.of(validators.flatMap { it.validate(v).issues }) }

/** The i18n keys of the built-in validators (bundle: `InputBundle`, namespace `stapel.input`). */
public object InputIssueKeys {
    /** A value is needed. */
    public const val REQUIRED: String = "stapel.input.required"

    /** Shorter than `{min}` characters. */
    public const val TOO_SHORT: String = "stapel.input.too_short"

    /** Longer than `{max}` characters. */
    public const val TOO_LONG: String = "stapel.input.too_long"

    /** Does not match the expected format. */
    public const val PATTERN: String = "stapel.input.pattern"

    /** Below `{min}`. */
    public const val BELOW_MIN: String = "stapel.input.below_min"

    /** Above `{max}`. */
    public const val ABOVE_MAX: String = "stapel.input.above_max"

    /** Not a number. */
    public const val NOT_A_NUMBER: String = "stapel.input.not_a_number"

    /** Not all positions are filled. */
    public const val INCOMPLETE: String = "stapel.input.incomplete"

    /** Not a real date. */
    public const val INVALID_DATE: String = "stapel.input.invalid_date"

    /** Check digit does not match. */
    public const val CHECKSUM: String = "stapel.input.checksum"

    /** Not a possible phone number. */
    public const val INVALID_PHONE: String = "stapel.input.invalid_phone"
}
