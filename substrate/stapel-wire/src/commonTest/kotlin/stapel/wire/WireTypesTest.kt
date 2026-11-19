package stapel.wire

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class Patch(
    @SerialName("name") val name: Maybe<String?> = Maybe.Absent,
    @SerialName("age") val age: Maybe<Int> = Maybe.Absent,
)

@Serializable(with = Color.Serializer::class)
private sealed interface Color : WireEnum {
    data object Red : Color { override val wire: String get() = "red" }
    data class Unknown(override val wire: String) : Color

    companion object {
        val known: List<Color> = listOf(Red)
        fun of(wire: String): Color = known.firstOrNull { it.wire == wire } ?: Unknown(wire)
    }

    object Serializer : WireEnumSerializer<Color>("Color", { of(it) })
    object BlankOrNull : BlankAsNullSerializer<Color>(Serializer, nullAsBlank = false)
    object BlankForNull : BlankAsNullSerializer<Color>(Serializer, nullAsBlank = true)
}

@Serializable
private data class Paint(
    @Serializable(with = Color.BlankOrNull::class) @SerialName("a") val a: Color? = null,
    @Serializable(with = Color.BlankForNull::class) @SerialName("b") val b: Color? = null,
)

@Serializable
private data class Price(@SerialName("amount") val amount: Decimal)

@Serializable(with = Shape.Serializer::class)
private sealed interface Shape {
    @Serializable data class Circle(@SerialName("kind") val kind: String, @SerialName("r") val r: Int) : Shape
    @Serializable data class Square(@SerialName("kind") val kind: String, @SerialName("side") val side: Int) : Shape
    data class Unknown(val raw: JsonObject) : Shape

    object Serializer : UnionSerializer<Shape>("Shape") {
        override fun select(obj: JsonObject): KSerializer<out Shape>? =
            when ((obj["kind"] as? JsonPrimitive)?.content) {
                "circle" -> Circle.serializer()
                "square" -> Square.serializer()
                else -> null
            }

        override fun unknown(obj: JsonObject): Shape = Unknown(obj)

        override fun serializerOf(value: Shape): KSerializer<out Shape>? = when (value) {
            is Circle -> Circle.serializer()
            is Square -> Square.serializer()
            is Unknown -> null
        }

        override fun rawOf(value: Shape): JsonObject? = (value as? Unknown)?.raw
    }
}

class WireTypesTest {
    private val json = WireJson

    @Test
    fun maybeAbsentOmitsTheKeyPresentNullWritesNullPresentValueWritesValue() {
        assertEquals("{}", json.encodeToString(Patch.serializer(), Patch()))
        assertEquals("""{"name":null}""", json.encodeToString(Patch.serializer(), Patch(name = Maybe.Present(null))))
        assertEquals(
            """{"name":"x","age":3}""",
            json.encodeToString(Patch.serializer(), Patch(Maybe.Present("x"), Maybe.Present(3))),
        )
    }

    @Test
    fun maybeDecodesTheThreeStates() {
        assertEquals(Patch(), json.decodeFromString(Patch.serializer(), "{}"))
        assertEquals(Patch(name = Maybe.Present(null)), json.decodeFromString(Patch.serializer(), """{"name":null}"""))
        assertEquals(Patch(age = Maybe.Present(7)), json.decodeFromString(Patch.serializer(), """{"age":7}"""))
        assertEquals("x", Maybe.Present("x").getOrNull())
        assertNull(Maybe.ofNullable<String>(null).getOrNull())
    }

    @Test
    fun maybeAbsentRefusesToBeEncodedExplicitly() {
        assertFailsWith<SerializationException> {
            json.encodeToString(MaybeSerializer(Int.serializer()), Maybe.Absent)
        }
    }

    @Test
    fun decimalSurvivesByteForByteFromStringAndNumber() {
        val text = """{"amount":"19.990"}"""
        assertEquals(text, json.encodeToString(Price.serializer(), json.decodeFromString(Price.serializer(), text)))
        assertEquals("1000000000000000000000.01", json.decodeFromString(Price.serializer(), """{"amount":1000000000000000000000.01}""").amount.text)
        assertFailsWith<SerializationException> { json.decodeFromString(Price.serializer(), """{"amount":"1e5"}""") }
        assertFailsWith<IllegalArgumentException> { Decimal("abc") }
    }

    @Test
    fun wireEnumRoundTripsUnknownValues() {
        assertEquals(Color.Red, json.decodeFromString(Color.Serializer, "\"red\""))
        val unknown = json.decodeFromString(Color.Serializer, "\"teal\"")
        assertEquals(Color.Unknown("teal"), unknown)
        assertEquals("\"teal\"", json.encodeToString(Color.Serializer, unknown))
    }

    @Test
    fun blankAndNullDecodeToNullAndEncodePerUnion() {
        val decoded = json.decodeFromString(Paint.serializer(), """{"a":"","b":null}""")
        assertNull(decoded.a)
        assertNull(decoded.b)
        assertEquals(Color.Red, json.decodeFromString(Paint.serializer(), """{"a":"red"}""").a)
        val written = json.encodeToString(Paint.serializer(), Paint(a = Color.Red, b = Color.Red))
        assertEquals("""{"a":"red","b":"red"}""", written)
        // Non-default nulls: a writes JSON null, b writes the empty string.
        assertEquals("""{"a":null}""", json.encodeToString(Color.BlankOrNull, null).let { """{"a":$it}""" })
        assertEquals("\"\"", json.encodeToString(Color.BlankForNull, null))
    }

    @Test
    fun unionSelectsVariantAndKeepsUnknownRaw() {
        assertEquals(Shape.Circle("circle", 2), json.decodeFromString(Shape.Serializer, """{"kind":"circle","r":2}"""))
        val raw = """{"kind":"hexagon","n":6}"""
        val unknown = json.decodeFromString(Shape.Serializer, raw)
        assertIs<Shape.Unknown>(unknown)
        assertEquals(raw, json.encodeToString(Shape.Serializer, unknown))
        assertEquals("""{"kind":"square","side":1}""", json.encodeToString(Shape.Serializer, Shape.Square("square", 1)))
        assertFailsWith<SerializationException> { json.decodeFromString(Shape.Serializer, "[1]") }
    }

    @Test
    fun pageCursorDecodesStringsAndNumbers() {
        assertEquals(PageCursor("12"), json.decodeFromString(PageCursor.serializer(), "12"))
        assertEquals(PageCursor("abc"), json.decodeFromString(PageCursor.serializer(), "\"abc\""))
    }

    @Test
    fun cacheKeysAreCanonicalAndPatternsMatchByPrefix() {
        val a = CacheKey.of("auth", "sessions", args = buildJsonObject { put("b", 1); put("a", "x") })
        val b = CacheKey.of("auth", "sessions", args = buildJsonObject { put("a", "x"); put("b", 1) })
        assertEquals(a, b)
        assertEquals("""{"a":"x","b":1}""", a.args)
        assertEquals(CacheKey.of("auth", "me"), CacheKey.of("auth", "me", args = buildJsonObject { put("x", null as String?) }))
        assertTrue(KeyPattern.of("auth", "sessions").matches(a))
        assertTrue(KeyPattern.of("auth").matches(a))
        assertFalse(KeyPattern.of("auth", "me").matches(a))
        assertFalse(KeyPattern.of("chat", "sessions").matches(a))
        assertTrue(KeyPattern.exact(a).matches(b))
        assertFalse(KeyPattern.exact(CacheKey.of("auth", "sessions")).matches(a))
        assertTrue(a.storageKey < CacheKey.of("auth", "sessionsz").storageKey)
    }
}
