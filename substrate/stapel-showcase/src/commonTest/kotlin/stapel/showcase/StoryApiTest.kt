package stapel.showcase

import stapel.tokens.ColorRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private enum class Variant { Primary, Secondary, Text }

class StoryApiTest {
    private val variant = Knob.enumOf("variant", "Variant", Variant.Primary)
    private val enabled = Knob.Flag("enabled", "Enabled", true)
    private val label = Knob.Text("label", "Label", "Continue")
    private val supporting = Knob.Text("supporting", "Supporting", null)
    private val count = Knob.Number("count", "Count", 0..10, 3)
    private val tint = Knob.Color("tint", "Tint", ColorRole.Brand)
    private val knobs = listOf(variant, enabled, label, supporting, count, tint)

    @Test
    fun knobValuesStartAtDefaultsAndReset() {
        val v = KnobValues(knobs)
        assertEquals(Variant.Primary, v[variant])
        assertEquals(true, v[enabled])
        assertNull(v[supporting])
        v[variant] = Variant.Text
        v[enabled] = false
        v[supporting] = "Hint"
        v[count] = 7
        v[tint] = ColorRole.Error
        assertEquals(setOf("variant", "enabled", "supporting", "count", "tint"), v.modified)
        v.reset(enabled)
        assertEquals(true, v[enabled])
        assertEquals(Variant.Text, v[variant])
        v.reset()
        assertEquals(emptySet(), v.modified)
        assertEquals(Variant.Primary, v[variant])
        assertEquals(3, v[count])
        assertEquals(ColorRole.Brand, v[tint])
    }

    @Test
    fun settingTheDefaultIsNotAModification() {
        val v = KnobValues(knobs)
        v[count] = 3
        assertEquals(emptySet(), v.modified)
    }

    @Test
    fun illegalValuesAreRefused() {
        val v = KnobValues(knobs)
        assertFailsWith<IllegalArgumentException> { v[count] = 11 }
        assertFailsWith<IllegalArgumentException> { v[label] = null }
        val narrow = Knob.Color("narrow", "Narrow", ColorRole.Brand, listOf(ColorRole.Brand, ColorRole.Error))
        val nv = KnobValues(listOf(narrow))
        assertFailsWith<IllegalArgumentException> { nv[narrow] = ColorRole.Surface }
        assertFalse(v.trySet("count", -1))
        assertFalse(v.trySet("variant", "Primary"))
        assertFalse(v.trySet("nope", 1))
        assertTrue(v.trySet("variant", Variant.Secondary))
        assertEquals(Variant.Secondary, v[variant])
        assertFailsWith<IllegalArgumentException> { v[Knob.Flag("enabled", "Other instance")] = true }
    }

    @Test
    fun knobDeclarationsAreValidated() {
        assertFailsWith<IllegalArgumentException> { Knob.Number("n", "N", 0..3, 5) }
        assertFailsWith<IllegalArgumentException> { Knob.Choice("c", "C", listOf("a" to 1), 2) }
        assertFailsWith<IllegalArgumentException> { Knob.Choice("c", "C", listOf("a" to 1, "a" to 2), 1) }
        assertFailsWith<IllegalArgumentException> { Knob.Text("t", "T", null, optional = false) }
        assertFailsWith<IllegalArgumentException> { Knob.Color("c", "C", ColorRole.Error, listOf(ColorRole.Brand)) }
        assertFailsWith<IllegalArgumentException> { Knob.Flag("Bad Key", "x") }
        assertEquals("Secondary", variant.describe(Variant.Secondary))
        assertEquals("—", supporting.describe(null))
    }

    @Test
    fun storyStatesAreValidatedAgainstKnobs() {
        val ok = Story("button", "Button", "Slots/Actions", knobs = knobs, states = listOf(StoryState("off", "Disabled", mapOf("enabled" to false)))) {}
        assertEquals(listOf("off"), ok.states.map { it.id })
        assertEquals(listOf("Slots", "Actions"), ok.groupPath)
        assertEquals(listOf(StoryState.Default), Story("plain", "Plain", "G") {}.states)
        assertFailsWith<IllegalArgumentException> { Story("b", "B", "G", knobs = knobs, states = listOf(StoryState("x", "X", mapOf("missing" to 1)))) {} }
        assertFailsWith<IllegalArgumentException> { Story("b", "B", "G", knobs = knobs, states = listOf(StoryState("x", "X", mapOf("count" to 99)))) {} }
        assertFailsWith<IllegalArgumentException> { Story("b", "B", "G", states = listOf(StoryState("x", "X"), StoryState("x", "Y"))) {} }
        assertFailsWith<IllegalArgumentException> { Story("b", "B", "G", knobs = listOf(enabled, Knob.Flag("enabled", "again"))) {} }
        assertFailsWith<IllegalArgumentException> { Story("B!", "B", "G") {} }
        assertFailsWith<IllegalArgumentException> { Story("b", "B", " / ") {} }
    }

    @Test
    fun matrixIsTheCrossProductOfChoiceAndFlagKnobs() {
        val story = Story("button", "Button", "G", knobs = knobs) {}
        val cells = story.matrix()!!
        assertEquals(3 * 2, cells.size)
        assertEquals("variant=Primary · enabled=false", cells[0].label)
        assertEquals(mapOf<String, Any?>("variant" to Variant.Primary, "enabled" to true), cells[1].values)
        assertEquals(Variant.Text, cells.last().values["variant"])
        assertEquals(listOf(false, true), story.matrix(listOf("enabled"))!!.map { it.values["enabled"] })
        assertNull(story.matrix(maxCells = 5), "too many cells")
        assertEquals(emptyList(), Story("t", "T", "G", knobs = listOf(label, count)) {}.matrix())
        assertFailsWith<IllegalArgumentException> { story.matrix(listOf("nope")) }
    }

    @Test
    fun catalogsValidateIdsAndSearchRanksHits() {
        val ui = StoryCatalog(
            "ui", "Stapel UI",
            listOf(
                Story("button", "Button", "Slots/Actions", description = "Primary call to action") {},
                Story("icon-button", "Icon button", "Slots/Actions") {},
                Story("text-field", "Text field", "Slots/Inputs") {},
            ),
        )
        val auth = StoryCatalog("auth", "Auth", listOf(Story("sign_in.email", "Sign-in by e-mail", "Sign-in", kind = StoryKind.Screen) {}))
        assertEquals("ui/button", ui.qualifiedId(ui.story("button")!!))
        assertEquals(listOf("Slots/Actions", "Slots/Inputs"), ui.groups)
        assertEquals(listOf("button", "icon-button"), searchStories(listOf(ui, auth), "button").map { it.story.id })
        assertEquals(listOf("button"), searchStories(listOf(ui, auth), "ui/button").map { it.story.id })
        assertEquals(listOf("sign_in.email"), searchStories(listOf(ui, auth), "SIGN e-mail").map { it.story.id })
        assertEquals(listOf("text-field"), searchStories(listOf(ui, auth), "inputs").map { it.story.id })
        assertEquals(listOf("button"), searchStories(listOf(ui, auth), "action primary").map { it.story.id })
        assertEquals(emptyList(), searchStories(listOf(ui, auth), "   "))
        assertEquals(emptyList(), searchStories(listOf(ui, auth), "button zebra"))
        assertFailsWith<IllegalArgumentException> { StoryCatalog("ui", "x", emptyList()) }
        assertFailsWith<IllegalArgumentException> { StoryCatalog("u.i", "x", ui.stories) }
        assertFailsWith<IllegalArgumentException> { StoryCatalog("ui", "x", ui.stories + ui.stories.first()) }
        assertEquals(emptyList(), catalogProblems(listOf(ui, auth)))
        assertEquals(1, catalogProblems(listOf(ui, auth, StoryCatalog("ui", "again", auth.stories))).size)
    }

    @Test
    fun targetsCompareByKey() {
        assertEquals(StoryTarget.Slot("Button"), StoryTarget.Slot("Button"))
        assertEquals("screen:SignIn", StoryTarget.Screen("SignIn").key)
        assertFalse(StoryTarget.Slot("SignIn") == StoryTarget.Screen("SignIn"))
    }
}
