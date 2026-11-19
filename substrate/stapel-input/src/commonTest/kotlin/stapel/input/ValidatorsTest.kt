package stapel.input

import kotlinx.datetime.LocalDate
import stapel.input.validation.FieldIssue
import stapel.input.validation.InputBundle
import stapel.input.validation.InputIssueKeys
import stapel.input.validation.ValidationResult
import stapel.input.validation.Validator
import stapel.input.validation.Validators
import stapel.input.validation.and
import stapel.input.validation.then
import stapel.wire.Decimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Each rule: one value it accepts and one it refuses (the inversion), with the exact key and params. */
class ValidatorsTest {
    private fun <T> Validator<T>.issues(v: T): List<FieldIssue> = validate(v).issues

    private fun issue(key: String, vararg params: Pair<String, String>) = listOf(FieldIssue(key, mapOf(*params)))

    @Test
    fun required() {
        assertEquals(issue(InputIssueKeys.REQUIRED), Validators.required().issues(""))
        assertEquals(issue(InputIssueKeys.REQUIRED), Validators.required().issues("  "))
        assertTrue(Validators.required().validate("a").isValid)
        assertEquals(issue("error.400.name_required"), Validators.required("error.400.name_required").issues(""))
    }

    @Test
    fun length() {
        val v = Validators.length(min = 2, max = 4)
        assertEquals(issue(InputIssueKeys.TOO_SHORT, "min" to "2"), v.issues("a"))
        assertEquals(issue(InputIssueKeys.TOO_LONG, "max" to "4"), v.issues("abcde"))
        assertTrue(v.validate("abc").isValid)
        assertTrue(v.validate("").isValid, "empty is for `required` to judge")
    }

    @Test
    fun pattern() {
        val v = Validators.pattern(Regex("[A-Z]{2}[0-9]{2}"))
        assertTrue(v.validate("AB12").isValid)
        assertEquals(issue(InputIssueKeys.PATTERN), v.issues("AB1"))
        assertEquals(issue("custom.key"), Validators.pattern(Regex("x"), "custom.key").issues("y"))
    }

    @Test
    fun range() {
        val v = Validators.range(1, 10)
        assertEquals(issue(InputIssueKeys.BELOW_MIN, "min" to "1"), v.issues(0))
        assertEquals(issue(InputIssueKeys.ABOVE_MAX, "max" to "10"), v.issues(11))
        assertTrue(v.validate(10).isValid)
    }

    @Test
    fun decimalRangeIsExact() {
        val v = Validators.decimalRange(Decimal("0.5"), Decimal("100"))
        assertEquals(issue(InputIssueKeys.BELOW_MIN, "min" to "0.5"), v.issues("0.49999999999999999999"))
        assertEquals(issue(InputIssueKeys.ABOVE_MAX, "max" to "100"), v.issues("100.000000000000000001"))
        assertTrue(v.validate("100").isValid)
        assertTrue(v.validate("100.000").isValid)
        assertTrue(v.validate("1.").isValid)
        assertEquals(issue(InputIssueKeys.NOT_A_NUMBER), v.issues("1,5"))
        assertEquals(issue(InputIssueKeys.BELOW_MIN, "min" to "0.5"), v.issues("-1"))
        val signed = Validators.decimalRange(Decimal("-10"), Decimal("-0.5"))
        assertTrue(signed.validate("-0.5").isValid)
        assertEquals(issue(InputIssueKeys.ABOVE_MAX, "max" to "-0.5"), signed.issues("-0"))
        assertEquals(issue(InputIssueKeys.BELOW_MIN, "min" to "-10"), signed.issues("-10.01"))
    }

    @Test
    fun complete() {
        val v = Validators.complete(PatternFormat("###-###"))
        assertTrue(v.validate("123456").isValid)
        assertEquals(issue(InputIssueKeys.INCOMPLETE), v.issues("1234"))
    }

    @Test
    fun date() {
        val f = DateFormat(DateOrder.DayMonthYear, '.')
        val v = Validators.date(f, min = LocalDate(2000, 1, 1), max = LocalDate(2026, 12, 31))
        assertTrue(v.validate("19112026").isValid)
        assertEquals(issue(InputIssueKeys.INCOMPLETE), v.issues("1911"))
        assertEquals(issue(InputIssueKeys.INVALID_DATE), v.issues("31022026"))
        assertEquals(issue(InputIssueKeys.BELOW_MIN, "min" to "2000-01-01"), v.issues("31121999"))
        assertEquals(issue(InputIssueKeys.ABOVE_MAX, "max" to "2026-12-31"), v.issues("01012027"))
    }

    @Test
    fun phone() {
        val v = Validators.phone()
        assertTrue(v.validate("+15551234567").isValid)
        assertEquals(issue(InputIssueKeys.INVALID_PHONE), v.issues("+1555"))
        assertEquals(issue(InputIssueKeys.INVALID_PHONE), v.issues("15551234567"))
    }

    @Test
    fun luhn() {
        assertTrue(Validators.luhn().validate("4111111111111111").isValid)
        assertTrue(Validators.luhn().validate("79927398713").isValid)
        assertEquals(issue(InputIssueKeys.CHECKSUM), Validators.luhn().issues("4111111111111112"))
        assertEquals(issue(InputIssueKeys.CHECKSUM), Validators.luhn().issues("79927398710"))
    }

    @Test
    fun custom() {
        val even = Validators.custom<Int>("app.even", mapOf("n" to "2")) { it % 2 == 0 }
        assertTrue(even.validate(4).isValid)
        assertEquals(issue("app.even", "n" to "2"), even.issues(3))
    }

    @Test
    fun algebra() {
        val both = Validators.length(min = 5) and Validators.pattern(Regex("[0-9]*"))
        assertEquals(listOf(InputIssueKeys.TOO_SHORT, InputIssueKeys.PATTERN), both.issues("ab").map { it.key })
        val first = Validators.required() then Validators.length(min = 5)
        assertEquals(listOf(InputIssueKeys.REQUIRED), first.issues("").map { it.key })
        assertEquals(listOf(InputIssueKeys.TOO_SHORT), first.issues("ab").map { it.key })
        assertEquals(ValidationResult.Valid, first.validate("abcdef"))
    }

    @Test
    fun everyBuiltInKeyHasFloorCopyAndNoValidatorReturnsText() {
        val en = InputBundle.locales.getValue("en")()
        val keys = listOf(
            InputIssueKeys.REQUIRED, InputIssueKeys.TOO_SHORT, InputIssueKeys.TOO_LONG, InputIssueKeys.PATTERN,
            InputIssueKeys.BELOW_MIN, InputIssueKeys.ABOVE_MAX, InputIssueKeys.NOT_A_NUMBER, InputIssueKeys.INCOMPLETE,
            InputIssueKeys.INVALID_DATE, InputIssueKeys.CHECKSUM, InputIssueKeys.INVALID_PHONE,
        )
        assertEquals(keys.toSet(), en.keys)
        assertTrue(keys.all { it.startsWith("stapel.input.") && ' ' !in it })
        assertTrue(en.getValue(InputIssueKeys.TOO_SHORT).contains("{min}"))
    }
}
