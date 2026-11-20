package stapel.profiles.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import stapel.navigation.toJson
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Holds this library to the web pair: nav ids/paths/placement and i18n key names (fixtures in `src/jvmTest/resources/web`). */
class WebParityTest {
    private fun fixture(name: String) = File("src/jvmTest/resources/web/$name").readText()

    private val contribution = profilesNavContribution("0.0.0-test", {}, {}, {}, {}, {}, {})

    @Test
    fun everyWebDestinationExistsWithTheSameFields() {
        val web = Json.parseToJsonElement(fixture("nav-manifest.json")).jsonObject.getValue("entries").jsonArray
            .associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }
        val mobile = Json.parseToJsonElement(contribution.manifest.toJson()).jsonObject.getValue("entries").jsonArray
            .associateBy { it.jsonObject.getValue("id").jsonPrimitive.content }
        assertEquals(web.keys, mobile.keys)
        for ((id, entry) in mobile) {
            val m = entry.jsonObject
            val w = web.getValue(id).jsonObject
            for (field in listOf("labelKey", "route", "placement", "menuVisibleDefault", "requiresAuth", "order", "surface")) {
                assertEquals(w[field], m[field], "$id.$field")
            }
        }
    }

    @Test
    fun theSharedCopyUsesTheWebKeyNames() {
        val web = Json.parseToJsonElement(fixture("i18n-keys.json")).jsonArray.map { it.jsonPrimitive.content }.toSet()
        val shared = PROFILES_WEB_EN.keys.map { k -> k.substringBeforeLast('.').takeIf { k.substringAfterLast('.') in PLURAL } ?: k }.toSet()
        assertEquals(emptySet(), shared - web, "shared keys the web pair does not have")
        assertTrue(ProfilesUiMobileOnlyKeys.all { it.startsWith("profiles.ui.") }, "mobile-only copy lives under profiles.ui.*")
        assertEquals(emptySet(), ProfilesUiMobileOnlyKeys.map { it.substringBeforeLast('.') }.toSet().intersect(web) + ProfilesUiMobileOnlyKeys.intersect(web))
    }

    private companion object {
        val PLURAL = setOf("one", "few", "many", "other")
    }
}
