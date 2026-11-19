package stapel.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import stapel.navigation.CapabilityKey
import stapel.navigation.DestinationId
import stapel.navigation.DestinationSpec
import stapel.navigation.EmbedId
import stapel.navigation.EmbedSpec
import stapel.navigation.EmbedSurface
import stapel.navigation.NavIcons
import stapel.navigation.NavManifest
import stapel.navigation.PathCodec
import stapel.navigation.PathPattern
import stapel.navigation.Placement
import stapel.navigation.Presentation
import stapel.navigation.Route
import stapel.navigation.SlotId
import stapel.result.InternalStapelApi
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data object SecRoute : Route

@OptIn(InternalStapelApi::class)
class NavManifestJsonTest {
    private val manifest = NavManifest(
        artifact = "runtime-value-ignored",
        version = "runtime-value-ignored",
        destinations = listOf(
            DestinationSpec(
                id = DestinationId("auth.security"), routeClass = SecRoute::class, serializer = SecRoute.serializer(),
                path = PathPattern("security"), codec = PathCodec.constant(SecRoute), labelKey = "auth.nav.security",
                icon = NavIcons.ShieldCheck, placement = Placement.Submenu(DestinationId("profiles.settings")),
                menuVisibleDefault = true, requiresAuth = true, order = 10, presentation = Presentation.Sheet,
                requires = setOf(CapabilityKey("b"), CapabilityKey("a")),
            ),
        ),
        embeds = listOf(
            EmbedSpec(EmbedId("auth.account_menu"), SlotId("topbar.end"), 90, EmbedSurface.Member, opens = DestinationId("account.root")),
        ),
    )

    @Test
    fun theCommittedShapeIsTheRegistryShapePlusMobileFields() {
        val o = Json.parseToJsonElement(navManifestJson(manifest, "g:stapel-auth-ui", "1.2.3")).jsonObject
        assertEquals("g:stapel-auth-ui", o.getValue("artifact").jsonPrimitive.content)
        assertEquals("1.2.3", o.getValue("version").jsonPrimitive.content)
        val d = o.getValue("destinations").jsonArray.single().jsonObject
        assertEquals(
            listOf("id", "labelKey", "icon", "path", "level", "parentId", "menuVisibleDefault", "requiresAuth", "surface", "order", "presentation", "requires"),
            d.keys.toList(),
        )
        assertEquals("shield-check", d.getValue("icon").jsonPrimitive.content)
        assertEquals("submenu", d.getValue("level").jsonPrimitive.content)
        assertEquals("profiles.settings", d.getValue("parentId").jsonPrimitive.content)
        assertEquals(JsonNull, d.getValue("surface"))
        assertEquals("sheet", d.getValue("presentation").jsonPrimitive.content)
        assertEquals(listOf("a", "b"), d.getValue("requires").jsonArray.map { it.jsonPrimitive.content })
        val e = o.getValue("embeds").jsonArray.single().jsonObject
        assertEquals(listOf("id", "slot", "order", "surface", "opens"), e.keys.toList())
        assertEquals("member", e.getValue("surface").jsonPrimitive.content)
    }

    @Test
    fun theOutputIsDeterministic() {
        assertEquals(navManifestJson(manifest, "a", "1"), navManifestJson(manifest, "a", "1"))
    }
}
