package stapel.navigation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathAndManifestTest {
    @Test
    fun pathPatternMatchesAndFormatsRoundTrip() {
        val p = PathPattern("/book/:type/:key")
        assertTrue(p.isAbsolute)
        assertEquals(listOf("type", "key"), p.params)
        assertEquals(mapOf("type" to "room", "key" to "a b"), p.match(listOf("book", "room", "a b")))
        assertNull(p.match(listOf("book", "room")))
        assertNull(p.match(listOf("bock", "room", "x")))
        assertEquals("/book/room/a%20b%2F%C3%BC", p.format(mapOf("type" to "room", "key" to "a b/ü")))
        assertEquals("a b/ü", percentDecode("a%20b%2F%C3%BC"))
        assertFailsWith<IllegalArgumentException> { p.format(mapOf("type" to "x")) }
        val rel = PathPattern("settings/security")
        assertFalse(rel.isAbsolute)
        assertEquals("settings/security", rel.format(emptyMap()))
        assertEquals("100%", percentDecode("100%"), "a malformed escape is kept")
    }

    @Test
    fun manifestJsonHasTheWebShapePlusMobileFields() {
        val json = Json.parseToJsonElement(appManifest.toJson()).jsonObject
        assertEquals("test:app-ui", json.getValue("package").jsonPrimitive.content)
        val entries = json.getValue("entries").jsonArray.map { it.jsonObject }
        val thread = entries.single { it.getValue("id").jsonPrimitive.content == "chat.thread" }
        assertEquals("t/:threadId", thread.getValue("route").jsonObject.getValue("path").jsonPrimitive.content)
        assertEquals("submenu", thread.getValue("placement").jsonObject.getValue("level").jsonPrimitive.content)
        assertEquals("chat.inbox", thread.getValue("placement").jsonObject.getValue("parentId").jsonPrimitive.content)
        assertFalse("surface" in thread, "an undeclared surface stays undeclared (derived by the reader)")
        val confirm = entries.single { it.getValue("id").jsonPrimitive.content == "x.confirm" }
        assertEquals("dialog", confirm.getValue("presentation").jsonPrimitive.content)
        assertEquals(appManifest.toJson(), appManifest.toJson(), "deterministic")
    }
}
