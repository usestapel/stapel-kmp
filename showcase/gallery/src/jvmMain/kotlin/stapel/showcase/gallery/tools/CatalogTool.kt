package stapel.showcase.gallery.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import stapel.showcase.Knob
import stapel.showcase.catalogProblems
import stapel.showcase.gallery.GalleryControls
import stapel.showcase.gallery.GallerySkin
import stapel.showcase.gallery.SizeChoice
import stapel.showcase.gallery.generated.DiscoveredCatalog
import stapel.showcase.gallery.generated.discoveredCatalogs
import stapel.tokens.ThemeMode
import java.io.File
import kotlin.system.exitProcess

/**
 * The catalogue tool behind the gallery's Gradle tasks:
 *
 * - `index --out <file>`: the catalogue index `checkDemoCoverage` reads;
 * - `record --root <repo> [--catalogs a,b] [--any-os]`: writes `<module>/snapshots/<story>__<state>__<variant>.png`;
 * - `verify --root <repo> --report <dir> [--catalogs a,b]`: compares every catalogue that has a
 *   `snapshots/` directory, exit code 1 on any difference;
 * - `export --out <dir> [--density 2]`: `catalog.json` + PNGs per story / state / mode for a docs site.
 */
fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: usage()
    val opts = args.drop(1).let { rest ->
        val m = HashMap<String, String>()
        var i = 0
        while (i < rest.size) {
            val k = rest[i].removePrefix("--")
            if (i + 1 < rest.size && !rest[i + 1].startsWith("--")) { m[k] = rest[i + 1]; i += 2 } else { m[k] = "true"; i++ }
        }
        m
    }
    val filter = opts["catalogs"]?.split(',')?.map { it.trim() }?.toSet()
    val entries = discoveredCatalogs.filter { filter == null || it.catalog.id in filter }
    catalogProblems(discoveredCatalogs.map { it.catalog }).takeIf { it.isNotEmpty() }?.let { fail(it.joinToString("\n")) }
    when (command) {
        "index" -> File(opts["out"] ?: usage()).apply { parentFile.mkdirs() }.writeText(PRETTY.encodeToString(JsonObject.serializer(), indexJson(discoveredCatalogs)))
        "record" -> {
            requireMac(opts)
            val root = File(opts["root"] ?: usage())
            entries.forEach { e ->
                val (w, u, r) = record(e, root)
                println("${e.catalog.id}: $w written, $u unchanged, $r stale removed → ${e.dir}/snapshots")
            }
        }
        "verify" -> {
            requireMac(opts)
            val root = File(opts["root"] ?: usage())
            val report = File(opts["report"] ?: usage()).apply { deleteRecursively(); mkdirs() }
            val withBaselines = entries.filter { File(root, "${it.dir}/snapshots").isDirectory }
            val problems = withBaselines.flatMap { verify(it, root, report) }
            val checked = withBaselines.sumOf { expectedSnapshots(it).size }
            if (problems.isNotEmpty()) {
                fail(
                    "verifyScreenshots: ${problems.size} of $checked snapshot(s) differ (actual/expected/diff under $report):\n" +
                        problems.joinToString("\n") { "  - ${it.catalog}: ${it.file}: ${it.reason}" },
                )
            }
            println("verifyScreenshots: $checked snapshot(s) of ${withBaselines.joinToString { it.catalog.id }} match")
        }
        "export" -> export(entries, File(opts["out"] ?: usage()), opts["density"]?.toFloat() ?: 2f)
        else -> usage()
    }
}

private val PRETTY = Json { prettyPrint = true }

private fun usage(): Nothing = fail("usage: index --out F | record --root R [--catalogs a,b] | verify --root R --report D | export --out D [--density N]")

private fun fail(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}

private fun requireMac(opts: Map<String, String>) {
    if (!System.getProperty("os.name").startsWith("Mac") && opts["any-os"] != "true") {
        fail("Baselines are recorded and verified on macOS only (text rasterisation differs on other systems).")
    }
}

/** The index of [entries]: catalogues, stories, states, knobs, coverage, anatomy, snippets. */
fun indexJson(entries: List<DiscoveredCatalog>, images: (DiscoveredCatalog, String, String) -> List<String> = { _, _, _ -> emptyList() }): JsonObject = buildJsonObject {
    putJsonArray("catalogs") {
        entries.forEach { e ->
            val c = e.catalog
            add(
                buildJsonObject {
                    put("id", c.id)
                    put("title", c.title)
                    put("module", e.module)
                    put("dir", e.dir)
                    put("locales", JsonArray(c.environment.locales.map(::JsonPrimitive)))
                    putJsonArray("stories") {
                        c.stories.forEach { s ->
                            add(
                                buildJsonObject {
                                    put("id", s.id)
                                    put("qualifiedId", c.qualifiedId(s))
                                    put("title", s.title)
                                    put("group", s.group)
                                    put("kind", s.kind.name)
                                    s.description?.let { put("description", it) }
                                    s.snippet?.let { put("snippet", it) }
                                    put("covers", JsonArray(s.covers.map { JsonPrimitive(it.key) }.sortedBy { it.content }))
                                    putJsonArray("states") {
                                        s.states.forEach { st ->
                                            add(
                                                buildJsonObject {
                                                    put("id", st.id)
                                                    put("title", st.title)
                                                    val imgs = images(e, s.id, st.id)
                                                    if (imgs.isNotEmpty()) put("images", JsonArray(imgs.map(::JsonPrimitive)))
                                                },
                                            )
                                        }
                                    }
                                    putJsonArray("knobs") {
                                        s.knobs.forEach { k ->
                                            add(
                                                buildJsonObject {
                                                    put("key", k.key)
                                                    put("label", k.label)
                                                    put("kind", k.kindName)
                                                    put("default", describe(k))
                                                    if (k is Knob.Choice<*>) put("options", buildJsonArray { k.options.forEach { add(it.first) } })
                                                },
                                            )
                                        }
                                    }
                                    putJsonArray("anatomy") {
                                        s.anatomy.forEach { a ->
                                            add(buildJsonObject { put("id", a.id); put("label", a.label); a.description?.let { put("description", it) } })
                                        }
                                    }
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun describe(k: Knob<*>): String = (k as Knob<Any?>).describe(k.default)

/** Writes `catalog.json` and `images/<catalog>/<story>__<state>__<mode>.png` (Material 3, light and dark). */
fun export(entries: List<DiscoveredCatalog>, out: File, density: Float) {
    out.deleteRecursively()
    out.mkdirs()
    var count = 0
    val index = indexJson(entries) { e, storyId, stateId ->
        val story = e.catalog.story(storyId)!!
        val state = story.state(stateId)!!
        listOf(ThemeMode.Light, ThemeMode.Dark).map { mode ->
            val rel = "images/${e.catalog.id}/${storyId}__${stateId}__${mode.name.lowercase()}.png"
            val controls = GalleryControls(skin = GallerySkin.Material3, size = SizeChoice.Phone, reduceMotion = true)
            File(out, rel).apply { parentFile.mkdirs() }.writeBytes(renderPng(e, story, state, controls, mode, density))
            count++
            rel
        }
    }
    File(out, "catalog.json").writeText(PRETTY.encodeToString(JsonObject.serializer(), index))
    println("exportCatalog: ${entries.sumOf { it.catalog.stories.size }} stories, $count images → $out")
}
