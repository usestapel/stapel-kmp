package stapel.conventions

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The rule of `checkDemoCoverage`; each violation is produced from a fixture that breaks it. */
class DemoCoverageTest {
    private val skinDump = """
        public abstract interface class stapel/ui/skin/StapelSkin {
        	public abstract fun Button (Lstapel/ui/skin/ButtonSpec;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)V
        	public fun Chip (Lstapel/ui/skin/ChipSpec;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)V
        	public fun extension (Lstapel/ui/skin/SkinExtensionKey;)Ljava/lang/Object;
        }

        public final class stapel/ui/skin/StapelSkin${'$'}DefaultImpls {
        	public static fun Chip (Lstapel/ui/skin/StapelSkin;Lstapel/ui/skin/ChipSpec;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;I)V
        }
    """.trimIndent()

    private val authDump = """
        public abstract interface class stapel/auth/ui/AuthScreens {
        	public abstract fun Sessions (Lstapel/auth/ui/SessionsState;Lkotlin/jvm/functions/Function1;Landroidx/compose/runtime/Composer;I)V
        	public abstract fun SignIn (Lstapel/auth/ui/SignInState;Lkotlin/jvm/functions/Function1;Lstapel/auth/ui/AuthSlots;Landroidx/compose/runtime/Composer;I)V
        }
    """.trimIndent()

    private val slots = interfaceMembers(skinDump, STAPEL_SKIN_INTERNAL_NAME)
    private val modules = mapOf(":showcase:stories:ui" to "", ":stapel-auth-ui-demo" to "auth")
    private val ui = IndexedCatalog("ui", ":showcase:stories:ui", mapOf("button" to setOf("slot:Button"), "chip" to setOf("slot:Chip")))
    private val auth = IndexedCatalog("auth", ":stapel-auth-ui-demo", mapOf("sign_in" to setOf("screen:SignIn"), "sessions" to setOf("screen:Sessions")))
    private val screens = mapOf("auth" to interfaceMembers(authDump, screensInterface("auth")))

    private fun check(
        brickUis: List<String> = listOf("auth"),
        demos: Set<String> = setOf("auth"),
        catalogs: List<IndexedCatalog> = listOf(ui, auth),
        slotList: List<String>? = slots,
        screenTables: Map<String, List<String>?> = screens,
    ) = demoCoverageViolations(brickUis, demos, modules, catalogs, slotList, screenTables)

    @Test
    fun `dumps are read for slot and screen members only`() {
        assertEquals(listOf("Button", "Chip"), slots)
        assertEquals(listOf("Sessions", "SignIn"), screens["auth"])
        assertEquals("stapel/order/items/ui/OrderItemsScreens", screensInterface("order-items"))
        assertEquals(null, interfaceMembers(authDump, "stapel/chat/ui/ChatScreens"))
    }

    @Test
    fun `a covered repository passes`() {
        assertEquals(emptyList(), check())
    }

    @Test
    fun `a brick ui without a demo module fails`() {
        val out = check(brickUis = listOf("auth", "chat"))
        assertTrue(out.single().contains("stapel-chat-ui has no demo module"), out.toString())
    }

    @Test
    fun `a screen without a story fails`() {
        val out = check(catalogs = listOf(ui, IndexedCatalog("auth", ":stapel-auth-ui-demo", mapOf("sign_in" to setOf("screen:SignIn")))))
        assertTrue(out.single().contains("screen 'Sessions' of AuthScreens has no story"), out.toString())
    }

    @Test
    fun `a slot without a story fails`() {
        val out = check(catalogs = listOf(IndexedCatalog("ui", ":showcase:stories:ui", mapOf("button" to setOf("slot:Button"))), auth))
        assertTrue(out.single().contains("StapelSkin slot 'Chip' has no story"), out.toString())
    }

    @Test
    fun `a demo module that exports nothing fails`() {
        val out = check(catalogs = listOf(ui))
        assertTrue(out.single().contains("bricks/auth/demo exports no catalogue"), out.toString())
    }

    @Test
    fun `missing dumps and clashing catalogue ids fail`() {
        assertTrue(check(slotList = null).single().contains("stapel-ui.api is missing"))
        assertTrue(check(screenTables = mapOf("auth" to null)).single().contains("stapel-auth-ui.api is missing"))
        val clash = check(catalogs = listOf(ui, auth, IndexedCatalog("ui", ":showcase:stories:extra", mapOf("x" to emptySet()))))
        assertTrue(clash.any { it.contains("catalogue id 'ui' is exported by") }, clash.toString())
    }

    @Test
    fun `a brick ui without a screen table needs only its catalogue`() {
        assertEquals(emptyList(), check(screenTables = mapOf("auth" to emptyList())))
    }

    @Test
    fun `the repository's own dumps parse`() {
        val root = File(System.getProperty("stapel.repoRoot"))
        val repoSlots = interfaceMembers(File(root, "substrate/stapel-ui/api/jvm/stapel-ui.api").readText(), STAPEL_SKIN_INTERNAL_NAME)!!
        assertTrue(repoSlots.size >= 22 && "Button" in repoSlots && "extension" !in repoSlots, repoSlots.toString())
    }

    @Test
    fun `discovery finds demo and stories modules and generates the registry`() {
        val root = File("/r")
        val found = discoverCatalogModules(
            root,
            listOf(
                ":stapel-auth-ui-demo" to File(root, "bricks/auth/demo"),
                ":stapel-auth-ui" to File(root, "bricks/auth/ui"),
                ":stapel-order-items-ui-demo" to File(root, "bricks/order-items/demo"),
                ":showcase:stories:ui" to File(root, "showcase/stories/ui"),
                ":showcase:gallery" to File(root, "showcase/gallery"),
            ),
            "stapel",
        )
        assertEquals(
            listOf(
                CatalogModule(":showcase:stories:ui", "showcase/stories/ui", "stapel.showcase.stories.ui", null),
                CatalogModule(":stapel-auth-ui-demo", "bricks/auth/demo", "stapel.auth.ui.demo", "auth"),
                CatalogModule(":stapel-order-items-ui-demo", "bricks/order-items/demo", "stapel.order.items.ui.demo", "order-items"),
            ),
            found,
        )
        val src = showcaseRegistrySource(found)
        assertTrue(src.contains("DiscoveredCatalog(\":stapel-auth-ui-demo\", \"bricks/auth/demo\", stapel.auth.ui.demo.storyCatalog)"), src)
        assertTrue(src.contains("stapel.order.items.ui.demo.storyCatalog"), src)
    }
}
