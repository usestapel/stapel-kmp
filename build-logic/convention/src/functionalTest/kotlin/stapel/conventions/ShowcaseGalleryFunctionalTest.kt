package stapel.conventions

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * `stapel.showcase-gallery`: the registry is generated from the demo / stories modules found by
 * directory (a new demo module appears with no edit to the gallery), and `checkDemoCoverage` fails on
 * a brick UI without a demo, on a screen-table member without a story and on a slot without a story.
 * The fixture's catalogue tool writes the index the real tool would write, with the coverage each step
 * declares, so the gate is exercised exactly as in the repository.
 */
class ShowcaseGalleryFunctionalTest {
    private val skinDump = """
        public abstract interface class stapel/ui/skin/StapelSkin {
        	public abstract fun Button (Lstapel/ui/skin/ButtonSpec;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)V
        	public fun extension (Lstapel/ui/skin/SkinExtensionKey;)Ljava/lang/Object;
        }
    """

    private fun screensDump(brick: String, pascal: String, vararg screens: String) =
        "public abstract interface class stapel/$brick/ui/${pascal}Screens {\n" +
            screens.joinToString("") { "\tpublic abstract fun $it (Lkotlin/jvm/functions/Function1;Landroidx/compose/runtime/Composer;I)V\n" } + "}\n"

    /** The fixture tool: one catalogue per discovered module, with [covers] (catalogue id → covered keys). */
    private fun tool(covers: Map<String, List<String>>): String {
        val cases = covers.entries.joinToString("\n") { (id, keys) ->
            "        \"$id\" -> listOf(${keys.joinToString { "\"$it\"" }})"
        }
        return """
            package stapel.showcase.gallery.tools

            import stapel.showcase.gallery.generated.discoveredCatalogs

            fun main(args: Array<String>) {
                val out = java.io.File(args[args.indexOf("--out") + 1])
                out.parentFile.mkdirs()
                val catalogs = discoveredCatalogs.joinToString(",") { d ->
                    val covers = when (d.catalog.id) {
            $cases
                        else -> emptyList()
                    }.joinToString(",") { "\"" + it + "\"" }
                    "{\"id\":\"" + d.catalog.id + "\",\"module\":\"" + d.module + "\",\"stories\":[{\"id\":\"s\",\"covers\":[" + covers + "]}]}"
                }
                out.writeText("{\"catalogs\":[" + catalogs + "]}")
            }
        """
    }

    @Test
    fun `the registry is discovered and checkDemoCoverage fails on each gap`() {
        val fx = FixtureBuild("showcase-gallery")
            .module("substrate/stapel-showcase", substrate(plugin = "stapel.substrate-ui"))
            .module("bricks/auth/ui", substrate(plugin = "stapel.brick-ui"))
            .module("bricks/auth/demo", "plugins { id(\"stapel.brick-demo\") }")
            .module("showcase/stories/ui", "plugins { id(\"stapel.showcase\") }\nkotlin { sourceSets { commonMain.dependencies { api(projects.stapelShowcase) } } }")
            .module("showcase/gallery", "plugins { id(\"stapel.showcase\")\n id(\"stapel.showcase-gallery\") }")
        fx.file("substrate/stapel-showcase/src/commonMain/kotlin/stapel/showcase/StoryCatalog.kt", "package stapel.showcase\n/** C. */\npublic class StoryCatalog(public val id: String)")
        fx.file("substrate/stapel-ui/api/jvm/stapel-ui.api", skinDump)
        fx.file("bricks/auth/ui/src/commonMain/kotlin/stapel/auth/ui/A.kt", "package stapel.auth.ui\n/** A. */\npublic const val A: String = \"a\"")
        fx.file("bricks/auth/ui/api/jvm/stapel-auth-ui.api", screensDump("auth", "Auth", "SignIn", "Sessions"))
        fx.file("bricks/auth/demo/src/commonMain/kotlin/stapel/auth/ui/demo/Stories.kt", "package stapel.auth.ui.demo\nval storyCatalog = stapel.showcase.StoryCatalog(\"auth\")")
        fx.file("showcase/stories/ui/src/commonMain/kotlin/stapel/showcase/stories/ui/Stories.kt", "package stapel.showcase.stories.ui\nval storyCatalog = stapel.showcase.StoryCatalog(\"ui\")")
        val toolPath = "showcase/gallery/src/jvmMain/kotlin/stapel/showcase/gallery/tools/CatalogTool.kt"

        fx.file(toolPath, tool(mapOf("ui" to listOf("slot:Button"), "auth" to listOf("screen:SignIn", "screen:Sessions"))))
        fx.succeeds(":showcase:gallery:checkDemoCoverage")
        val registry = fx.dir.resolve("showcase/gallery/build/generated/stapel/showcaseRegistry/stapel/showcase/gallery/generated/GeneratedCatalogs.kt").readText()
        assertContains(registry, "DiscoveredCatalog(\":stapel-auth-ui-demo\", \"bricks/auth/demo\", stapel.auth.ui.demo.storyCatalog)")
        assertContains(registry, "stapel.showcase.stories.ui.storyCatalog")

        // A screen of the table without a story.
        fx.file(toolPath, tool(mapOf("ui" to listOf("slot:Button"), "auth" to listOf("screen:SignIn"))))
        assertContains(fx.fails(":showcase:gallery:checkDemoCoverage").output, "screen 'Sessions' of AuthScreens has no story in catalogue 'auth'")

        // A skin slot without a story.
        fx.file(toolPath, tool(mapOf("ui" to emptyList(), "auth" to listOf("screen:SignIn", "screen:Sessions"))))
        assertContains(fx.fails(":showcase:gallery:checkDemoCoverage").output, "StapelSkin slot 'Button' has no story")

        // A new brick UI without a demo module fails; adding its demo makes its catalogue appear with no
        // edit to the gallery (the registry regenerates from the directories).
        fx.file(toolPath, tool(mapOf("ui" to listOf("slot:Button"), "auth" to listOf("screen:SignIn", "screen:Sessions"), "chat" to listOf("screen:Thread"))))
        fx.module("bricks/chat/ui", substrate(plugin = "stapel.brick-ui"))
        fx.file("bricks/chat/ui/src/commonMain/kotlin/stapel/chat/ui/C.kt", "package stapel.chat.ui\n/** C. */\npublic const val C: String = \"c\"")
        fx.file("bricks/chat/ui/api/jvm/stapel-chat-ui.api", screensDump("chat", "Chat", "Thread"))
        assertContains(fx.fails(":showcase:gallery:checkDemoCoverage").output, "stapel-chat-ui has no demo module")
        fx.module("bricks/chat/demo", "plugins { id(\"stapel.brick-demo\") }")
        fx.file("bricks/chat/demo/src/commonMain/kotlin/stapel/chat/ui/demo/Stories.kt", "package stapel.chat.ui.demo\nval storyCatalog = stapel.showcase.StoryCatalog(\"chat\")")
        fx.succeeds(":showcase:gallery:checkDemoCoverage")
        val after = fx.dir.resolve("showcase/gallery/build/generated/stapel/showcaseRegistry/stapel/showcase/gallery/generated/GeneratedCatalogs.kt").readText()
        assertTrue("stapel.chat.ui.demo.storyCatalog" in after, after)
    }
}
