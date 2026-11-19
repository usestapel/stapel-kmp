package stapel.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import stapel.navigation.DestinationId
import stapel.navigation.DestinationSpec
import stapel.navigation.NavIcon
import stapel.navigation.NavManifest
import stapel.navigation.PathCodec
import stapel.navigation.PathPattern
import stapel.navigation.Placement
import stapel.navigation.Route
import stapel.navigation.Surface
import stapel.navigation.resolveNav
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Replays the route plans shipped in the pinned stapel-tools wheel (`gradle/composition/route-plans`,
 * digest-pinned in `gradle/composition/pin.json`) through [resolveNav], with the input read from the
 * same wheel's `composition-registry.json`: every routed id and absolute path, and the container roots,
 * equal the Python planner's. One selection, one routing, on both platforms.
 */
class PinnedRoutePlanTest {
    private val registry = Json.parseToJsonElement(PINNED_COMPOSITION.getValue("composition-registry.json")).jsonObject
    private val bricks = registry.getValue("bricks").jsonObject
    private val icons = registry.getValue("icons").jsonArray.map { NavIcon(it.jsonPrimitive.content) }.toSet()
    private val declared = (
        bricks.values.flatMap { b -> b.jsonObject.getValue("nav").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content } } +
            registry.getValue("containerRoots").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        ).map(::DestinationId).toSet()

    @Test
    fun everyPinnedPlanIsReproducedByResolveNav() {
        val plans = PINNED_COMPOSITION.filterKeys { it.startsWith("route-plans/") }
        assertTrue(plans.size >= 7, "pinned route plans missing: ${plans.keys}")
        for ((name, text) in plans) {
            val plan = Json.parseToJsonElement(text).jsonObject
            val modules = plan.getValue("modules").jsonArray.map { it.jsonPrimitive.content }
            val authWired = plan.getValue("authWired").jsonPrimitive.boolean
            val manifests = modules.filter { it in bricks && (authWired || it != "auth") }.map { manifestOf(it) }
            val nav = resolveNav(manifests, icons = icons, knownIds = declared)
            val expected = plan.getValue("absoluteRoutes").jsonArray.associate {
                it.jsonObject.getValue("id").jsonPrimitive.content to it.jsonObject.getValue("path").jsonPrimitive.content
            } + plan.getValue("appChildren").jsonArray.associate {
                it.jsonObject.getValue("id").jsonPrimitive.content to "/app/" + it.jsonObject.getValue("path").jsonPrimitive.content
            }
            assertEquals(expected.entries.sortedBy { it.key }.map { it.toPair() }, nav.byId.entries.map { it.key.value to it.value.absolutePath }.sortedBy { it.first }, "$name: routes")
            assertEquals(
                plan.getValue("containerRoots").jsonArray.map { it.jsonPrimitive.content }.sorted(),
                nav.containerRoots.map { it.value }.sorted(),
                "$name: container roots",
            )
        }
    }

    @Test
    fun aChangedPathInTheInputIsCaught() {
        // Inversion: the same comparison must fail when one input path moves.
        val plan = Json.parseToJsonElement(PINNED_COMPOSITION.getValue("route-plans/auth.json")).jsonObject
        val manifest = manifestOf("auth") { id, path -> if (id == "auth.login") "/sign-in" else path }
        val nav = resolveNav(listOf(manifest), icons = icons, knownIds = declared)
        val expectedLogin = plan.getValue("absoluteRoutes").jsonArray.first { it.jsonObject.getValue("id").jsonPrimitive.content == "auth.login" }
            .jsonObject.getValue("path").jsonPrimitive.content
        assertTrue(nav.byId.getValue(DestinationId("auth.login")).absolutePath != expectedLogin)
    }

    private fun manifestOf(brick: String, path: (String, String) -> String = { _, p -> p }): NavManifest = NavManifest(
        artifact = "registry:$brick",
        version = "0",
        destinations = bricks.getValue(brick).jsonObject.getValue("nav").jsonArray.map { spec(it.jsonObject, path) },
    )

    private fun spec(e: JsonObject, path: (String, String) -> String): DestinationSpec<*> {
        val id = e.getValue("id").jsonPrimitive.content
        return typed(PINNED_ROUTES.getValue(id), e, id, path(id, e.getValue("path").jsonPrimitive.content))
    }

    private fun <R : Route> typed(type: PinRouteType<R>, e: JsonObject, id: String, path: String): DestinationSpec<R> = DestinationSpec(
        id = DestinationId(id),
        routeClass = type.type,
        serializer = type.serializer,
        path = PathPattern(path),
        codec = PathCodec.constant(type.instance),
        labelKey = e.getValue("labelKey").jsonPrimitive.content,
        icon = NavIcon(e.getValue("icon").jsonPrimitive.content),
        placement = if (e.getValue("level").jsonPrimitive.content == "top") {
            Placement.Top
        } else {
            Placement.Submenu(DestinationId(e.getValue("parentId").jsonPrimitive.content))
        },
        menuVisibleDefault = e.getValue("menuVisibleDefault").jsonPrimitive.booleanOrNull ?: true,
        requiresAuth = e.getValue("requiresAuth").jsonPrimitive.boolean,
        order = e.getValue("order").jsonPrimitive.int,
        surface = e["surface"]?.jsonPrimitive?.contentOrNull?.let { s -> Surface.entries.single { it.name.lowercase() == s } },
        shortLabelKey = e["shortLabelKey"]?.jsonPrimitive?.contentOrNull,
    )
}
