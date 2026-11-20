package stapel.analytics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The guard's verdicts equal the web's on every case of the shared `pii-vectors.json`. */
class PiiVectorsTest {
    @Test
    fun everySharedVectorHasTheWebVerdict() {
        val root = Json.parseToJsonElement(PII_VECTORS_JSON).jsonObject
        assertEquals(PII_REDACTED, root.getValue("redacted").jsonPrimitive.content)
        val cases = root.getValue("cases").jsonArray.map { it.jsonObject }
        assertTrue(cases.size >= 50, "the fixture lost cases")
        val wrong = cases.mapNotNull { c ->
            val value = c.getValue("value").jsonPrimitive.content
            val expected = c.getValue("pii").jsonPrimitive.boolean
            if (looksLikePii(value) != expected) "${value.encodeForMessage()} expected $expected" else null
        }
        assertEquals(emptyList(), wrong)
    }

    private fun String.encodeForMessage(): String = map { ch -> if (ch.code in 0x20..0x7e) "$ch" else "\\u" + ch.code.toString(16).padStart(4, '0') }.joinToString("", "\"", "\"")
}
