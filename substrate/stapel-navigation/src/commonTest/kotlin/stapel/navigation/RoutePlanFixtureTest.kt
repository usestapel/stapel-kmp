package stapel.navigation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Replays the route plans stapel-tools' `build_nav_route_plan` produced (src/commonTest/resources/
 * route_plan, see the README there) through [resolveNav]: same routed ids, same absolute paths,
 * same container roots, the planner's unrouted entries exactly our dropped ones, and the planner's
 * refusals ours too.
 */
class RoutePlanFixtureTest {
    /** Cases the Kotlin contract cannot even express (so they cannot be wrong here), with the reason. */
    private val unrepresentable = mapOf(
        "synthetic-error-route-index" to "DestinationSpec has no route.index field",
    )

    @Test
    fun everyFixtureIsReplayed() {
        assertTrue(ROUTE_PLAN_FIXTURES.size >= 20, "fixtures missing: ${ROUTE_PLAN_FIXTURES.keys}")
        val seenUnrepresentable = mutableSetOf<String>()
        for ((name, text) in ROUTE_PLAN_FIXTURES) {
            val case = Json.parseToJsonElement(text).jsonObject
            val manifests = try {
                manifestsOf(case)
            } catch (e: Unrepresentable) {
                assertTrue(name in unrepresentable, "$name: ${e.message}")
                seenUnrepresentable += name
                continue
            }
            val icons = case.getValue("icons").jsonArray.map { NavIcon(it.jsonPrimitive.content) }.toSet()
            val known = case.getValue("declared").jsonArray.map { DestinationId(it.jsonPrimitive.content) }.toSet()
            val expectedError = case["expectedError"]?.jsonObject
            if (expectedError != null) {
                val err = assertFailsWith<NavContractException>(name) { resolveNav(manifests, icons = icons, knownIds = known) }
                val ids = err.problems.map { it.substringBefore(':') }.distinct().sorted()
                assertEquals(expectedError.getValue("ids").jsonArray.map { it.jsonPrimitive.content }, ids, name)
                continue
            }
            val expected = case.getValue("expected").jsonObject
            val nav = resolveNav(manifests, icons = icons, knownIds = known)
            val routes = expected.getValue("routes").jsonObject.mapValues { it.value.jsonPrimitive.content }
            assertEquals(routes, nav.byId.entries.associate { it.key.value to it.value.absolutePath }, "$name: routed paths")
            assertEquals(
                expected.getValue("containerRoots").jsonArray.map { it.jsonPrimitive.content },
                nav.containerRoots.map { it.value },
                "$name: container roots",
            )
            assertEquals(
                expected.getValue("unrouted").jsonArray.map { it.jsonPrimitive.content },
                nav.dropped.map { it.id }.sorted(),
                "$name: dropped = unrouted",
            )
            assertTrue(nav.dropped.all { it.reason.isNotBlank() }, "$name: every drop names its reason")
        }
        assertEquals(unrepresentable.keys, seenUnrepresentable)
    }

    private class Unrepresentable(message: String) : Exception(message)

    private fun manifestsOf(case: JsonObject): List<NavManifest> = case.getValue("input").jsonArray.map { pairEl ->
        val pair = pairEl.jsonObject
        NavManifest(
            artifact = pair.getValue("package").jsonPrimitive.content,
            version = pair.getValue("version").jsonPrimitive.content,
            destinations = pair.getValue("nav").jsonArray.map { specOf(it.jsonObject) },
        )
    }

    private fun specOf(e: JsonObject): DestinationSpec<*> {
        val id = e.getValue("id").jsonPrimitive.content
        val route = e.getValue("route").jsonObject
        if ("index" in route) throw Unrepresentable("$id: route.index")
        val type = FIXTURE_ROUTES[id] ?: fail("no generated route type for $id")
        return typed(type, e, id, route.getValue("path").jsonPrimitive.content)
    }

    private fun <R : Route> typed(type: FixtureRouteType<R>, e: JsonObject, id: String, path: String): DestinationSpec<R> {
        val placement = e.getValue("placement").jsonObject
        return DestinationSpec(
            id = DestinationId(id),
            routeClass = type.type,
            serializer = type.serializer,
            path = PathPattern(path),
            codec = PathCodec.constant(type.instance),
            labelKey = e.getValue("labelKey").jsonPrimitive.content,
            icon = NavIcon(e.getValue("icon").jsonPrimitive.content),
            placement = when (placement.getValue("level").jsonPrimitive.content) {
                "top" -> Placement.Top
                "submenu" -> Placement.Submenu(DestinationId(placement.getValue("parentId").jsonPrimitive.content))
                else -> fail("$id: unknown level")
            },
            menuVisibleDefault = e.getValue("menuVisibleDefault").jsonPrimitive.boolean,
            requiresAuth = e.getValue("requiresAuth").jsonPrimitive.boolean,
            order = e.getValue("order").jsonPrimitive.int,
            surface = e["surface"]?.jsonPrimitive?.content?.let { s -> Surface.entries.single { it.name.lowercase() == s } },
            shortLabelKey = e["shortLabelKey"]?.jsonPrimitive?.content,
        )
    }
}
