package stapel.app

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import stapel.navigation.NavManifest
import stapel.navigation.Placement
import stapel.result.InternalStapelApi

private val prettyJson = Json { prettyPrint = true }

/**
 * The committed `nav-manifest.json` of a brick UI library: the neutral fields of every destination
 * and embed in the shape of stapel-tools' `composition-registry.json` (`level`/`parentId` flat, `null`
 * for an absent parent or surface), plus the mobile fields `presentation` and `requires`. [artifact]
 * is the `group:artifact` the build publishes, not what the library passes at runtime. No version: the
 * file is committed, and its version is the train's (`kmp-registry.json` `train`, the published
 * artifact's own coordinates), so a version bump never makes it drift.
 * Deterministic: destinations and embeds in declaration order, sets sorted, one trailing newline.
 */
@InternalStapelApi
public fun navManifestJson(manifest: NavManifest, artifact: String): String {
    val root = buildJsonObject {
        put("artifact", artifact)
        putJsonArray("destinations") {
            for (d in manifest.destinations) {
                add(
                    buildJsonObject {
                        put("id", d.id.value)
                        put("labelKey", d.labelKey)
                        d.shortLabelKey?.let { put("shortLabelKey", it) }
                        put("icon", d.icon.key)
                        put("path", d.path.raw)
                        when (val p = d.placement) {
                            Placement.Top -> {
                                put("level", "top")
                                put("parentId", JsonNull)
                            }
                            is Placement.Submenu -> {
                                put("level", "submenu")
                                put("parentId", p.parentId.value)
                            }
                        }
                        put("menuVisibleDefault", d.menuVisibleDefault)
                        put("requiresAuth", d.requiresAuth)
                        put("surface", d.surface?.name?.lowercase()?.let(::JsonPrimitive) ?: JsonNull)
                        put("order", d.order)
                        put("presentation", d.presentation.name.lowercase())
                        put("requires", JsonArray(d.requires.map { it.value }.sorted().map(::JsonPrimitive)))
                    },
                )
            }
        }
        putJsonArray("embeds") {
            for (e in manifest.embeds) {
                add(
                    buildJsonObject {
                        put("id", e.id.value)
                        put("slot", e.slot.value)
                        put("order", e.order)
                        put("surface", e.surface.name.lowercase())
                        e.opens?.let { put("opens", it.value) }
                        e.scope?.let { put("scopeNavEntry", it.value) }
                        if (e.routeParams.isNotEmpty()) {
                            putJsonObject("routeProps") { e.routeParams.entries.sortedBy { it.key }.forEach { (k, v) -> put(k, v) } }
                        }
                        e.widths?.let { w ->
                            put("breakpoints", buildJsonArray { w.sortedBy { it.ordinal }.forEach { add(JsonPrimitive(it.name.lowercase())) } })
                        }
                    },
                )
            }
        }
    }
    return prettyJson.encodeToString(JsonObject.serializer(), root) + "\n"
}
