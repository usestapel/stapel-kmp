package stapel.auth.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import stapel.navigation.toJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Holds this library to the web pair: nav ids/paths/placement, and i18n key names (fixtures in `src/jvmTest/resources/web`). */
class WebParityTest {
    private fun fixture(name: String) = File("src/jvmTest/resources/web/$name").readText()

    private val contribution = authNavContribution("0.0.0-test", signIn = {}, security = {}, accountMenu = {})

    @Test
    fun everyDestinationMatchesTheWebEntryWithTheSameId() {
        val web = Json.parseToJsonElement(fixture("nav-manifest.json")).jsonObject
        val webEntries = web.getValue("entries").jsonArray.associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }
        val mobile = Json.parseToJsonElement(contribution.manifest.toJson()).jsonObject
        val mobileEntries = mobile.getValue("entries").jsonArray
        assertEquals(setOf("auth.login", "auth.security"), mobileEntries.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet())
        for (entry in mobileEntries) {
            val m = entry.jsonObject
            val id = m.getValue("id").jsonPrimitive.content
            val w = webEntries[id]?.jsonObject ?: error("$id is not a web nav id")
            for (field in listOf("labelKey", "route", "placement", "menuVisibleDefault", "requiresAuth", "order")) {
                assertEquals(w[field], m[field], "$id.$field")
            }
            assertEquals(w["surface"], m["surface"], "$id.surface")
        }
    }

    @Test
    fun theAccountMenuEmbedMatchesTheWebEmbed() {
        val web = Json.parseToJsonElement(fixture("nav-manifest.json")).jsonObject.getValue("embeds").jsonArray
            .single { it.jsonObject.getValue("id").jsonPrimitive.content == AuthNavIds.ACCOUNT_MENU }.jsonObject
        val mobile = (Json.parseToJsonElement(contribution.manifest.toJson()).jsonObject.getValue("embeds") as JsonArray).single().jsonObject
        for (field in listOf("id", "slot", "order", "surface", "opens")) assertEquals(web[field], mobile[field], "embed.$field")
    }

    @Test
    fun webOnlyEntriesAreTheDeliberateOnes() {
        val web = Json.parseToJsonElement(fixture("nav-manifest.json")).jsonObject.getValue("entries").jsonArray
            .map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet()
        val mobile = contribution.manifest.destinations.map { it.id.value }.toSet()
        // auth.qr_confirm: the QR scanner role is slice S8; admin.*: the operator console stays on the web.
        assertEquals(setOf("auth.qr_confirm"), (web - mobile).filterNot { it.startsWith("admin.") }.toSet())
    }

    @Test
    fun keysMirrorTheWebAndMobileOnlyKeysAreHonest() {
        val webKeys = (Json.parseToJsonElement(fixture("i18n-keys.json")) as JsonArray).map { it.jsonPrimitive.content }.toSet()
        val en = AuthUiBundle.locales.getValue("en")()
        val families = AuthUiPluralFamilies
        val mobileKeys = en.keys.map { k -> families.firstOrNull { k.startsWith("$it.") } ?: k }.toSet()
        val shared = mobileKeys - AuthUiMobileOnlyKeys
        assertEquals(emptySet(), shared - webKeys, "keys claimed shared with the web that the web does not have")
        assertEquals(emptySet(), AuthUiMobileOnlyKeys intersect webKeys, "keys listed as mobile-only that the web has")
        assertEquals(emptySet(), AuthUiMobileOnlyKeys - mobileKeys, "mobile-only keys with no copy")
    }

    @Test
    fun everyTypedKeyHasEnglishCopy() {
        val en = AuthUiBundle.locales.getValue("en")()
        val typed = AuthUiStrings::class.java.declaredMethods.filter { it.parameterCount == 0 && it.name.startsWith("get") }
            .mapNotNull { m ->
                when (val v = m.invoke(AuthUiStrings)) {
                    is String -> v
                    else -> null
                }
            }
        // Value classes are unboxed to String in getters: every getter returns a key text.
        assertTrue(typed.size >= 80, "found ${typed.size} typed keys")
        val missing = typed.filter { key -> key !in en && "$key.other" !in en }
        assertEquals(emptyList(), missing)
    }
}
