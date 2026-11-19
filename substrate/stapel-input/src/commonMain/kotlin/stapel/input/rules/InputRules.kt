package stapel.input.rules

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import stapel.input.CodeFormat
import stapel.input.DateFormat
import stapel.input.DecimalFormat
import stapel.input.GenericPhoneMetadata
import stapel.input.GroupedDigitsFormat
import stapel.input.InputFormat
import stapel.input.InputLocale
import stapel.input.MoneyFormat
import stapel.input.PatternFormat
import stapel.input.PhoneFormat
import stapel.input.PhoneMetadata
import stapel.input.PlainFormat
import stapel.input.validation.ValidationResult
import stapel.input.validation.Validator
import stapel.input.validation.Validators
import stapel.input.validation.allOf
import stapel.wire.Decimal

/** How a field's text is entered and shown, as data. */
public sealed interface FormatSpec {
    /** Plain text, optionally capped. */
    public class Text(
        /** Maximum length; null for none. */
        public val maxLength: Int? = null,
    ) : FormatSpec

    /** Grouped digits ([GroupedDigitsFormat]). */
    public class Digits(
        /** Group sizes. */
        public val groups: List<Int> = emptyList(),
        /** Separator between groups. */
        public val separator: String = " ",
        /** Maximum digits; null for none. */
        public val maxDigits: Int? = null,
        /** Digits needed to be complete; null means [maxDigits]. */
        public val minDigits: Int? = null,
    ) : FormatSpec

    /** A mask ([PatternFormat]). */
    public class Pattern(
        /** The mask (`#` digit, `A` letter, `*` either, `[…]` optional, `\` escape). */
        public val mask: String,
        /** Upper-case letters. */
        public val upperCase: Boolean = false,
    ) : FormatSpec

    /** A phone number in E.164 ([PhoneFormat]); region knowledge comes from the [RuleContext]. */
    public class Phone(
        /** Calling code for national input. */
        public val defaultCallingCode: String? = null,
        /** Keep the calling code fixed. */
        public val lockCallingCode: Boolean = false,
    ) : FormatSpec

    /** A date in the context locale's order ([DateFormat]). */
    public object Date : FormatSpec

    /** A decimal number ([DecimalFormat]). */
    public class Number(
        /** Digits after the point. */
        public val maxFractionDigits: Int = 0,
        /** Digits before the point. */
        public val maxIntegerDigits: Int = 15,
        /** Accept a minus sign. */
        public val allowNegative: Boolean = false,
    ) : FormatSpec

    /** A money amount ([MoneyFormat]). */
    public class Money(
        /** Minor-unit digits of the currency. */
        public val minorUnits: Int,
        /** Shown before the amount. */
        public val prefix: String = "",
        /** Shown after the amount. */
        public val suffix: String = "",
        /** Accept negative amounts. */
        public val allowNegative: Boolean = false,
    ) : FormatSpec

    /** A one-time code ([CodeFormat]). */
    public class Code(
        /** Number of digits. */
        public val length: Int,
    ) : FormatSpec
}

/** One validation rule, as data. Optional `key`s replace the built-in i18n key (e.g. a server error key). */
public sealed interface RuleSpec {
    /** A value is needed. */
    public class Required(
        /** Issue key override. */
        public val key: String? = null,
    ) : RuleSpec

    /** Length bounds. */
    public class Length(
        /** Minimum length. */
        public val min: Int? = null,
        /** Maximum length. */
        public val max: Int? = null,
    ) : RuleSpec

    /** The whole raw value matches [regex]. */
    public class Pattern(
        /** A regular expression (the common subset of the platforms' engines). */
        public val regex: String,
        /** Issue key override. */
        public val key: String? = null,
    ) : RuleSpec

    /** Numeric bounds on a decimal raw value. */
    public class Range(
        /** Lower bound. */
        public val min: Decimal? = null,
        /** Upper bound. */
        public val max: Decimal? = null,
    ) : RuleSpec

    /** Date bounds (only with [FormatSpec.Date]). */
    public class DateRange(
        /** Earliest date. */
        public val min: LocalDate? = null,
        /** Latest date. */
        public val max: LocalDate? = null,
    ) : RuleSpec

    /** Luhn check digit. */
    public class Luhn(
        /** Issue key override. */
        public val key: String? = null,
    ) : RuleSpec

    /** The format's completeness. */
    public class Complete(
        /** Issue key override. */
        public val key: String? = null,
    ) : RuleSpec

    /** A possible phone number (per the context's metadata). */
    public object Phone : RuleSpec
}

/** A field's format and rules, as data. */
public class FieldSpec(
    /** How text is entered and shown. */
    public val format: FormatSpec = FormatSpec.Text(),
    /** Rules, checked in order; all issues are reported. */
    public val rules: List<RuleSpec> = emptyList(),
)

/** What a rule set needs from the app: locale facts and phone region knowledge. */
public class RuleContext(
    /** Separators, digit shapes, date order. */
    public val locale: InputLocale = InputLocale.Neutral,
    /** Phone region knowledge. */
    public val phoneMetadata: PhoneMetadata = GenericPhoneMetadata,
)

/**
 * A field compiled from data: its [format], one [validator] over raw values, and the [problems] met
 * while reading the data (unknown kinds, unreadable values, invalid regular expressions). A problem
 * never fails the build: the part is skipped — a rule the client cannot read is not enforced on the
 * client (the server still is the judge) — and listed here for logs.
 */
public class FieldRules(
    /** The format. */
    public val format: InputFormat,
    /** All rules over the raw value. */
    public val validator: Validator<String>,
    /** What was skipped, in words for logs (not for users). */
    public val problems: List<String>,
) {
    /** Shorthand for `validator.validate(raw)`. */
    public fun validate(raw: String): ValidationResult = validator.validate(raw)
}

/**
 * Builds formats and validators from data, so contract-declared formats and rules (e.g. a later
 * dynamic-forms artifact reading a form schema) are not compiled into the client.
 *
 * JSON vocabulary read by [fromJson] (snake_case; unknown members ignored):
 * ```
 * { "format": { "kind": "text" | "digits" | "pattern" | "phone" | "date" | "number" | "money" | "code", … },
 *   "required": true,
 *   "rules": [ { "kind": "required" | "length" | "pattern" | "range" | "date_range" | "luhn" | "complete" | "phone", … } ] }
 * ```
 * Format members: `max_length`; `groups`, `separator`, `max_digits`, `min_digits`; `mask`,
 * `upper_case`; `default_calling_code`, `lock_calling_code`; `max_fraction_digits`,
 * `max_integer_digits`, `allow_negative`; `minor_units`, `prefix`, `suffix`; `length`.
 * Rule members: `key`; `min`, `max` (numbers or decimal strings; ISO dates for `date_range`); `regex`.
 * A rule with an unreadable member is skipped as a whole (a range with a broken bound is not
 * half-enforced).
 */
public object InputRules {
    /** Reads [json] (vocabulary above) into a [FieldSpec]; what cannot be read is listed in the result. */
    public fun parse(json: JsonElement): ParsedFieldSpec {
        val problems = ArrayList<String>()
        val obj = json as? JsonObject
        if (obj == null) {
            problems += "field spec is not an object"
            return ParsedFieldSpec(FieldSpec(), problems)
        }
        val format = parseFormat(obj["format"], problems)
        val rules = ArrayList<RuleSpec>()
        if (obj.bool("required") == true) rules += RuleSpec.Required()
        when (val list = obj["rules"]) {
            null, JsonNull -> Unit
            is JsonArray -> list.forEachIndexed { i, r -> parseRule(r, i, problems)?.let { rules += it } }
            else -> problems += "rules is not an array"
        }
        return ParsedFieldSpec(FieldSpec(format, rules), problems)
    }

    /** Compiles [spec] in [context]. */
    public fun build(spec: FieldSpec, context: RuleContext = RuleContext()): FieldRules {
        val problems = ArrayList<String>()
        val format = buildFormat(spec.format, context)
        val validators = spec.rules.mapNotNull { buildRule(it, format, context, problems) }
        return FieldRules(format, allOf(validators), problems)
    }

    /** [parse] then [build]; problems of both steps are reported together. */
    public fun fromJson(json: JsonElement, context: RuleContext = RuleContext()): FieldRules {
        val parsed = parse(json)
        val built = build(parsed.spec, context)
        return FieldRules(built.format, built.validator, parsed.problems + built.problems)
    }

    private fun buildFormat(spec: FormatSpec, context: RuleContext): InputFormat = when (spec) {
        is FormatSpec.Text -> PlainFormat(spec.maxLength)
        is FormatSpec.Digits -> GroupedDigitsFormat(spec.groups, spec.separator, spec.maxDigits ?: Int.MAX_VALUE, spec.minDigits, context.locale.zeroDigit)
        is FormatSpec.Pattern -> PatternFormat(spec.mask, spec.upperCase, context.locale.zeroDigit)
        is FormatSpec.Phone -> PhoneFormat(context.phoneMetadata, spec.defaultCallingCode, spec.lockCallingCode, context.locale.zeroDigit)
        FormatSpec.Date -> DateFormat(context.locale)
        is FormatSpec.Number -> DecimalFormat(context.locale, spec.maxFractionDigits, spec.maxIntegerDigits, spec.allowNegative)
        is FormatSpec.Money -> MoneyFormat(context.locale, spec.minorUnits, spec.prefix, spec.suffix, spec.allowNegative)
        is FormatSpec.Code -> CodeFormat(spec.length)
    }

    private fun buildRule(rule: RuleSpec, format: InputFormat, context: RuleContext, problems: MutableList<String>): Validator<String>? =
        when (rule) {
            is RuleSpec.Required -> rule.key?.let { Validators.required(it) } ?: Validators.required()
            is RuleSpec.Length -> Validators.length(rule.min, rule.max)
            is RuleSpec.Pattern -> {
                val regex = compile(rule.regex)
                if (regex == null) {
                    problems += "pattern rule: invalid regular expression"
                    null
                } else {
                    rule.key?.let { Validators.pattern(regex, it) } ?: Validators.pattern(regex)
                }
            }
            is RuleSpec.Range -> Validators.decimalRange(rule.min, rule.max)
            is RuleSpec.DateRange -> {
                if (format is DateFormat) {
                    Validators.date(format, rule.min, rule.max)
                } else {
                    problems += "date_range rule on a field that is not a date"
                    null
                }
            }
            is RuleSpec.Luhn -> rule.key?.let { Validators.luhn(it) } ?: Validators.luhn()
            is RuleSpec.Complete -> rule.key?.let { Validators.complete(format, it) } ?: Validators.complete(format)
            RuleSpec.Phone -> Validators.phone(context.phoneMetadata)
        }

    private fun compile(pattern: String): Regex? =
        try {
            Regex(pattern)
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun parseFormat(element: JsonElement?, problems: MutableList<String>): FormatSpec {
        if (element == null || element is JsonNull) return FormatSpec.Text()
        val o = element as? JsonObject
        if (o == null) {
            problems += "format is not an object"
            return FormatSpec.Text()
        }
        return when (val kind = o.string("kind")) {
            "text" -> FormatSpec.Text(o.int("max_length"))
            "digits" -> FormatSpec.Digits(
                groups = (o["groups"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.intOrNull } ?: emptyList(),
                separator = o.string("separator") ?: " ",
                maxDigits = o.int("max_digits"),
                minDigits = o.int("min_digits"),
            )
            "pattern" -> o.string("mask")?.let { FormatSpec.Pattern(it, o.bool("upper_case") ?: false) }
                ?: FormatSpec.Text().also { problems += "pattern format without a mask" }
            "phone" -> FormatSpec.Phone(o.string("default_calling_code"), o.bool("lock_calling_code") ?: false)
            "date" -> FormatSpec.Date
            "number" -> FormatSpec.Number(o.int("max_fraction_digits") ?: 0, o.int("max_integer_digits") ?: DEFAULT_INTEGER_DIGITS, o.bool("allow_negative") ?: false)
            "money" -> o.int("minor_units")?.let {
                FormatSpec.Money(it, o.string("prefix") ?: "", o.string("suffix") ?: "", o.bool("allow_negative") ?: false)
            } ?: FormatSpec.Text().also { problems += "money format without minor_units" }
            "code" -> o.int("length")?.let { FormatSpec.Code(it) } ?: FormatSpec.Text().also { problems += "code format without length" }
            else -> FormatSpec.Text().also { problems += "unknown format kind: ${kind ?: "(none)"}" }
        }
    }

    private fun parseRule(element: JsonElement, index: Int, problems: MutableList<String>): RuleSpec? {
        val o = element as? JsonObject
        if (o == null) {
            problems += "rule $index is not an object"
            return null
        }
        val key = o.string("key")
        return when (val kind = o.string("kind")) {
            "required" -> RuleSpec.Required(key)
            "length" -> RuleSpec.Length(o.int("min"), o.int("max"))
            "pattern" -> o.string("regex")?.let { RuleSpec.Pattern(it, key) } ?: null.also { problems += "rule $index: pattern without regex" }
            "range" -> {
                val before = problems.size
                val rule = RuleSpec.Range(o.decimal("min", index, problems), o.decimal("max", index, problems))
                rule.takeIf { problems.size == before }
            }
            "date_range" -> {
                val before = problems.size
                val rule = RuleSpec.DateRange(o.date("min", index, problems), o.date("max", index, problems))
                rule.takeIf { problems.size == before }
            }
            "luhn" -> RuleSpec.Luhn(key)
            "complete" -> RuleSpec.Complete(key)
            "phone" -> RuleSpec.Phone
            else -> null.also { problems += "rule $index: unknown kind ${kind ?: "(none)"}" }
        }
    }

    private fun JsonObject.primitive(name: String): JsonPrimitive? = (this[name] as? JsonPrimitive)?.takeIf { it !is JsonNull }

    private fun JsonObject.string(name: String): String? = primitive(name)?.takeIf { it.isString }?.content

    private fun JsonObject.int(name: String): Int? = primitive(name)?.intOrNull

    private fun JsonObject.bool(name: String): Boolean? = primitive(name)?.booleanOrNull

    private fun JsonObject.decimal(name: String, index: Int, problems: MutableList<String>): Decimal? {
        val text = primitive(name)?.content ?: return null
        return try {
            Decimal(text)
        } catch (e: IllegalArgumentException) {
            problems += "rule $index: $name is not a decimal"
            null
        }
    }

    private fun JsonObject.date(name: String, index: Int, problems: MutableList<String>): LocalDate? {
        val text = string(name) ?: return null
        return try {
            LocalDate.parse(text)
        } catch (e: IllegalArgumentException) {
            problems += "rule $index: $name is not an ISO date"
            null
        }
    }

    private const val DEFAULT_INTEGER_DIGITS = 15
}

/**
 * The result of [InputRules.parse].
 *
 * @property spec what could be read.
 * @property problems what could not.
 */
public class ParsedFieldSpec(public val spec: FieldSpec, public val problems: List<String>)
