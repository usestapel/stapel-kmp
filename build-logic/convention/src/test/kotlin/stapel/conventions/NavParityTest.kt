package stapel.conventions

import groovy.json.JsonSlurper
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `checkNavParity`'s comparison against the vendored, pinned composition-registry.json. */
class NavParityTest {
    private val dir = File(System.getProperty("stapel.repoRoot"), COMPOSITION_DIR)

    @Suppress("UNCHECKED_CAST")
    private val registry = JsonSlurper().parse(File(dir, "composition-registry.json")) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    private fun authEntry(id: String): MutableMap<String, Any?> =
        ((registry["bricks"] as Map<String, Any?>)["auth"] as Map<String, Any?>)["nav"].let { nav ->
            LinkedHashMap((nav as List<Map<String, Any?>>).single { it["id"] == id })
        }

    private fun manifest(vararg destinations: Map<String, Any?>, embeds: List<Map<String, Any?>> = emptyList()) =
        mapOf("artifact" to "g:stapel-auth-ui", "version" to "0", "destinations" to destinations.toList(), "embeds" to embeds)

    @Test
    fun `the vendored data matches its pin`() {
        verifiedComposition(File(dir, "pin.json"))
    }

    @Test
    fun `equal shared fields pass and mobile fields are ignored`() {
        val login = authEntry("auth.login").apply { put("presentation", "screen"); put("requires", emptyList<String>()) }
        val menu = mapOf("id" to "auth.account_menu", "slot" to "topbar.end", "order" to 90, "surface" to "member", "opens" to "account.root")
        assertEquals(emptyList(), navParityErrors(registry, "auth", manifest(login, embeds = listOf(menu))))
    }

    @Test
    fun `a changed shared field, an unknown id, a foreign icon and a moved embed are each named`() {
        val login = authEntry("auth.login").apply { put("labelKey", "auth.nav.sign_in") }
        val unknown = authEntry("auth.security").apply { put("id", "auth.unknown") }
        val badIcon = authEntry("auth.security").apply { put("icon", "LoginOutlined") }
        val menu = mapOf("id" to "auth.account_menu", "slot" to "topbar.start", "order" to 90, "surface" to "member", "opens" to "account.root")
        val errors = navParityErrors(registry, "auth", manifest(login, unknown, badIcon, embeds = listOf(menu)))
        assertTrue(errors.any { "auth.login': labelKey" in it }, "$errors")
        assertTrue(errors.any { "'auth.unknown' is unknown" in it }, "$errors")
        assertTrue(errors.any { "icon 'LoginOutlined' is not in the neutral icon vocabulary" in it }, "$errors")
        assertTrue(errors.any { "embed 'auth.account_menu': slot" in it }, "$errors")
    }

    @Test
    fun `a tampered vendored file fails the pin`() {
        val copy = kotlin.io.path.createTempDirectory("pin").toFile()
        dir.copyRecursively(copy)
        File(copy, "composition-registry.json").appendText(" ")
        val e = runCatching { verifiedComposition(File(copy, "pin.json")) }.exceptionOrNull()
        assertTrue(e != null && "sha256" in e.message.orEmpty(), "$e")
    }
}
