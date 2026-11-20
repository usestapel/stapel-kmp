package stapel.tools.codegen

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Typed events from flows.json (K10): one constant per flow id, an events.json in the web's shape. */
class FlowEventsTest {
    private val flows = parseJsonObject(
        """{"f":[
          {"id":"auth.passwordless_login","title":"Passwordless login","title_key":"flow.auth.passwordless_login.title",
           "description_key":"flow.auth.passwordless_login.description",
           "steps":[{"order":1,"kind":"http","note_key":"n1"},{"order":0,"kind":"human","note_key":"n0"}]},
          {"id":"auth.first_login","title":"First login","steps":[]}
        ]}""",
    )["f"]!!.jsonArray

    @Test
    fun aConstantPerFlowIdSortedWithTheRegistry() {
        val text = FlowEvents.typeSpec("auth", flows)!!.toString()
        assertTrue(Regex("const val FIRST_LOGIN: (kotlin\\.)?String = \"auth.first_login\"").containsMatchIn(text), text)
        assertTrue(Regex("const val PASSWORDLESS_LOGIN: (kotlin\\.)?String = \"auth.passwordless_login\"").containsMatchIn(text), text)
        assertTrue("flows.map { \"flow.\$it.*\" }.toSet()" in text, text)
        assertTrue("listOf(\"auth.first_login\", \"auth.passwordless_login\")" in text, text)
        assertTrue("object AuthEvents" in text, text)
    }

    @Test
    fun eventsJsonHasTheWebShape() {
        val doc = parseJsonObject(FlowEvents.registryJson("stapel-auth", "0.54.0", flows)!!)
        assertEquals("stapel-auth", doc["package"]!!.jsonPrimitive.content)
        val f = doc["flows"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("auth.first_login", "auth.passwordless_login"), f.map { it["flow"]!!.jsonPrimitive.content })
        val login = f[1]
        assertEquals("flow.auth.passwordless_login.<step>", login["event"]!!.jsonPrimitive.content)
        assertEquals(FlowEvents.PHASES, login["props"]!!.jsonObject["phase"]!!.jsonObject["options"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("n0", "n1"), login["steps"]!!.jsonArray.map { it.jsonObject["noteKey"]!!.jsonPrimitive.content })
        assertEquals("flows.json", login["source"]!!.jsonPrimitive.content)
    }

    @Test
    fun aBrickWithoutFlowsGetsNothing() {
        assertNull(FlowEvents.typeSpec("search", JsonArray(emptyList())))
        assertNull(FlowEvents.registryJson("stapel-search", "1", null))
    }

    @Test
    fun theGeneratorEmitsBothForTheAuthContract() {
        val auth = Fixtures.repoRoot.resolve("contracts/auth")
        val tmp = Files.createTempDirectory("flow-events")
        auth.toFile().copyRecursively(tmp.toFile())
        val files = Generator("auth", tmp).run().files
        assertTrue(files.keys.any { it.endsWith("/AuthEvents.kt") }, files.keys.toString())
        assertTrue(FlowEvents.FILE in files)
        tmp.toFile().deleteRecursively()
    }
}
