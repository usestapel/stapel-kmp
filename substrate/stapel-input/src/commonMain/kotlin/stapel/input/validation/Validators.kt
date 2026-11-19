package stapel.input.validation

import kotlinx.datetime.LocalDate
import stapel.input.DateFormat
import stapel.input.InputFormat
import stapel.input.PhoneMetadata
import stapel.input.GenericPhoneMetadata
import stapel.wire.Decimal

/**
 * The built-in validators. Every one except [required] accepts the empty value, so optional fields
 * compose as `required() then x` or just `x`. Messages are [InputIssueKeys] (overridable by `key`).
 */
public object Validators {
    /** Refuses an empty or blank value. */
    public fun required(key: String = InputIssueKeys.REQUIRED): Validator<String> =
        Validator { v -> if (v.isBlank()) ValidationResult.invalid(key) else ValidationResult.Valid }

    /** Length (in characters) within [min]..[max]; either bound may be null. Params `min` / `max`. */
    public fun length(min: Int? = null, max: Int? = null): Validator<String> = Validator { v ->
        when {
            v.isEmpty() -> ValidationResult.Valid
            min != null && v.length < min -> ValidationResult.invalid(InputIssueKeys.TOO_SHORT, mapOf("min" to min.toString()))
            max != null && v.length > max -> ValidationResult.invalid(InputIssueKeys.TOO_LONG, mapOf("max" to max.toString()))
            else -> ValidationResult.Valid
        }
    }

    /** The whole value matches [regex]. */
    public fun pattern(regex: Regex, key: String = InputIssueKeys.PATTERN): Validator<String> =
        Validator { v -> if (v.isEmpty() || regex.matches(v)) ValidationResult.Valid else ValidationResult.invalid(key) }

    /** A comparable value within [min]..[max] (inclusive; either may be null); params shown by [show]. */
    public fun <T : Comparable<T>> range(min: T? = null, max: T? = null, show: (T) -> String = { it.toString() }): Validator<T> =
        Validator { v ->
            when {
                min != null && v < min -> ValidationResult.invalid(InputIssueKeys.BELOW_MIN, mapOf("min" to show(min)))
                max != null && v > max -> ValidationResult.invalid(InputIssueKeys.ABOVE_MAX, mapOf("max" to show(max)))
                else -> ValidationResult.Valid
            }
        }

    /**
     * A decimal raw value (as `DecimalFormat`/`MoneyFormat` produce, `.` as the point) within
     * [min]..[max], compared exactly (no floating point). A value that is not a number is refused with
     * [InputIssueKeys.NOT_A_NUMBER].
     */
    public fun decimalRange(min: Decimal? = null, max: Decimal? = null): Validator<String> = Validator { raw ->
        val text = raw.removeSuffix(".")
        when {
            raw.isEmpty() -> ValidationResult.Valid
            !DECIMAL.matches(text) -> ValidationResult.invalid(InputIssueKeys.NOT_A_NUMBER)
            min != null && compareDecimals(text, min.text) < 0 -> ValidationResult.invalid(InputIssueKeys.BELOW_MIN, mapOf("min" to min.text))
            max != null && compareDecimals(text, max.text) > 0 -> ValidationResult.invalid(InputIssueKeys.ABOVE_MAX, mapOf("max" to max.text))
            else -> ValidationResult.Valid
        }
    }

    /** [format] considers the value finished ([InputFormat.isComplete]). */
    public fun complete(format: InputFormat, key: String = InputIssueKeys.INCOMPLETE): Validator<String> =
        Validator { v -> if (v.isEmpty() || format.isComplete(v)) ValidationResult.Valid else ValidationResult.invalid(key) }

    /**
     * A date raw value of [format]: complete ([InputIssueKeys.INCOMPLETE]), a real day
     * ([InputIssueKeys.INVALID_DATE]) and within [min]..[max] (ISO dates as params).
     */
    public fun date(format: DateFormat, min: LocalDate? = null, max: LocalDate? = null): Validator<String> = Validator { raw ->
        if (raw.isEmpty()) return@Validator ValidationResult.Valid
        if (raw.length < DATE_DIGITS) return@Validator ValidationResult.invalid(InputIssueKeys.INCOMPLETE)
        val d = format.toDate(raw) ?: return@Validator ValidationResult.invalid(InputIssueKeys.INVALID_DATE)
        range(min, max) { it.toString() }.validate(d)
    }

    /** A phone raw value (`+` and digits) of a possible length per [metadata]. */
    public fun phone(metadata: PhoneMetadata = GenericPhoneMetadata): Validator<String> = Validator { raw ->
        val ok = raw.isEmpty() || (raw.startsWith('+') && raw.length > 1 && raw.drop(1).all { it in '0'..'9' } && metadata.isPossible(raw.drop(1)))
        if (ok) ValidationResult.Valid else ValidationResult.invalid(InputIssueKeys.INVALID_PHONE)
    }

    /**
     * The Luhn (mod 10) check digit over the value's digits — an example of a checksum validator,
     * used by payment-card and some identifier numbers.
     */
    public fun luhn(key: String = InputIssueKeys.CHECKSUM): Validator<String> = Validator { v ->
        val digits = v.filter { it in '0'..'9' }
        if (v.isEmpty() || (digits.length >= 2 && luhnValid(digits))) ValidationResult.Valid else ValidationResult.invalid(key)
    }

    /** A rule of your own: [accept] false reports [key] with [params]. */
    public fun <T> custom(key: String, params: Map<String, String> = emptyMap(), accept: (T) -> Boolean): Validator<T> =
        Validator { v -> if (accept(v)) ValidationResult.Valid else ValidationResult.invalid(key, params) }

    private const val DATE_DIGITS = 8
    private val DECIMAL = Regex("-?[0-9]+(\\.[0-9]+)?")

    internal fun luhnValid(digits: String): Boolean {
        var sum = 0
        var double = false
        for (i in digits.indices.reversed()) {
            var d = digits[i] - '0'
            if (double) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            double = !double
        }
        return sum % 10 == 0
    }

    /** Exact comparison of two decimal texts (`-?digits(.digits)?`). */
    internal fun compareDecimals(a: String, b: String): Int {
        val negA = a.startsWith('-')
        val negB = b.startsWith('-')
        val ma = magnitude(a.removePrefix("-"))
        val mb = magnitude(b.removePrefix("-"))
        val zeroA = ma.first.isEmpty() && ma.second.isEmpty()
        val zeroB = mb.first.isEmpty() && mb.second.isEmpty()
        val signA = if (zeroA) 0 else if (negA) -1 else 1
        val signB = if (zeroB) 0 else if (negB) -1 else 1
        if (signA != signB) return signA.compareTo(signB)
        if (signA == 0) return 0
        val cmp = compareMagnitudes(ma, mb)
        return if (signA < 0) -cmp else cmp
    }

    private fun magnitude(text: String): Pair<String, String> {
        val intPart = text.substringBefore('.').trimStart('0')
        val frac = text.substringAfter('.', "").trimEnd('0')
        return intPart to frac
    }

    private fun compareMagnitudes(a: Pair<String, String>, b: Pair<String, String>): Int {
        if (a.first.length != b.first.length) return a.first.length.compareTo(b.first.length)
        val i = a.first.compareTo(b.first)
        if (i != 0) return i.coerceIn(-1, 1)
        val width = maxOf(a.second.length, b.second.length)
        return a.second.padEnd(width, '0').compareTo(b.second.padEnd(width, '0')).coerceIn(-1, 1)
    }
}
