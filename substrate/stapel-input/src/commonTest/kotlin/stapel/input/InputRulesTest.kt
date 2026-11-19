package stapel.input

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import stapel.input.rules.FieldSpec
import stapel.input.rules.FormatSpec
import stapel.input.rules.InputRules
import stapel.input.rules.RuleContext
import stapel.input.rules.RuleSpec
import stapel.input.validation.InputIssueKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InputRulesTest {
    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    @Test
    fun formatAndRulesComeFromData() {
        val rules = InputRules.fromJson(
            json(
                """
                {"format": {"kind": "pattern", "mask": "AA-####", "upper_case": true},
                 "required": true,
                 "rules": [{"kind": "complete"}, {"kind": "pattern", "regex": "[A-Z]{2}[0-9]{4}", "key": "error.400.feature_format"}]}
                """,
            ),
        )
        assertTrue(rules.problems.isEmpty(), rules.problems.toString())
        assertEquals("AB-1234", rules.format.format(rules.format.normalize("ab1234")).text)
        assertTrue(rules.validate("AB1234").isValid)
        assertEquals(listOf(InputIssueKeys.REQUIRED), rules.validate("").issues.map { it.key })
        assertEquals(listOf(InputIssueKeys.INCOMPLETE, "error.400.feature_format"), rules.validate("AB12").issues.map { it.key })
    }

    @Test
    fun aRuleOnlyAppliesWhenTheDataDeclaresIt() {
        val without = InputRules.fromJson(json("""{"format": {"kind": "digits", "groups": [4]}}"""))
        val with = InputRules.fromJson(json("""{"format": {"kind": "digits", "groups": [4]}, "rules": [{"kind": "luhn"}]}"""))
        assertTrue(without.validate("4111111111111112").isValid)
        assertEquals(listOf(InputIssueKeys.CHECKSUM), with.validate("4111111111111112").issues.map { it.key })
    }

    @Test
    fun codeLengthAndMoneyMinorUnitsAreConfiguration() {
        val code = InputRules.fromJson(json("""{"format": {"kind": "code", "length": 5}, "rules": [{"kind": "complete"}]}"""))
        assertEquals("12345", code.format.normalize("1234567"))
        assertEquals(listOf(InputIssueKeys.INCOMPLETE), code.validate("1234").issues.map { it.key })
        val money = InputRules.fromJson(
            json("""{"format": {"kind": "money", "minor_units": 3, "suffix": " KWD"}, "rules": [{"kind": "range", "min": 1, "max": "999.999"}]}"""),
            RuleContext(InputLocale(decimalSeparator = ',', groupSeparator = " ")),
        )
        assertEquals("1 234,567 KWD", money.format.format(money.format.normalize("1234,5678")).text)
        assertEquals(listOf(InputIssueKeys.ABOVE_MAX), money.validate("1234.567").issues.map { it.key })
        assertEquals(listOf(InputIssueKeys.BELOW_MIN), money.validate("0.5").issues.map { it.key })
    }

    @Test
    fun phoneAndDateTakeRegionAndLocaleFromTheContext() {
        val context = RuleContext(InputLocale(dateOrder = DateOrder.DayMonthYear, dateSeparator = '.'), SampleMetadata)
        val phone = InputRules.fromJson(json("""{"format": {"kind": "phone", "default_calling_code": "1"}, "rules": [{"kind": "phone"}]}"""), context)
        assertEquals("+1 555 123 4567", phone.format.format(phone.format.normalize("5551234567")).text)
        assertEquals(listOf(InputIssueKeys.INVALID_PHONE), phone.validate("+1555").issues.map { it.key })
        val date = InputRules.fromJson(json("""{"format": {"kind": "date"}, "rules": [{"kind": "date_range", "min": "2000-01-01"}]}"""), context)
        assertEquals("19.11.2026", date.format.format("19112026").text)
        assertEquals(listOf(InputIssueKeys.BELOW_MIN), date.validate("01011999").issues.map { it.key })
    }

    @Test
    fun unreadableDataIsSkippedAndListedNeverThrown() {
        val rules = InputRules.fromJson(
            json(
                """
                {"format": {"kind": "hologram"},
                 "rules": [{"kind": "pattern", "regex": "(["}, {"kind": "telepathy"}, 7, {"kind": "range", "min": "abc"},
                           {"kind": "date_range", "min": "2000-01-01"}, {"kind": "length", "max": 3}]}
                """,
            ),
        )
        assertIs<stapel.input.PlainFormat>(rules.format)
        assertEquals(6, rules.problems.size, rules.problems.toString())
        assertTrue(rules.validate("(([").isValid, "an unreadable rule is not enforced on the client")
        assertEquals(listOf(InputIssueKeys.TOO_LONG), rules.validate("abcd").issues.map { it.key }, "readable rules still apply")
        assertEquals(1, InputRules.fromJson(json("[1, 2]")).problems.size)
        assertEquals(1, InputRules.fromJson(json("""{"format": "digits"}""")).problems.size)
    }

    @Test
    fun typedSpecsBuildTheSameWay() {
        val rules = InputRules.build(FieldSpec(FormatSpec.Number(maxFractionDigits = 2), listOf(RuleSpec.Required(), RuleSpec.Length(max = 6))))
        assertEquals("12.34", rules.format.normalize("12.345"))
        assertEquals(listOf(InputIssueKeys.TOO_LONG), rules.validate("1234.56").issues.map { it.key })
    }
}
