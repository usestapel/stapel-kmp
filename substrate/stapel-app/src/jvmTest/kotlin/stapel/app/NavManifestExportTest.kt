package stapel.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.module.Module
import stapel.app.di.BrickContext
import stapel.app.di.StapelBrick
import stapel.navigation.NavIcons
import stapel.navigation.PathCodec
import stapel.navigation.Route
import stapel.navigation.compose.NavContribution
import stapel.navigation.compose.navContribution
import stapel.result.InternalStapelApi
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Serializable
data object ExportHome : Route

/** A descriptor the way a brick UI library declares one: public, no-argument constructor. */
class ExportBrick : StapelBrick {
    override val key: String = "export"
    override fun modules(): List<Module> = emptyList()
    override fun navigation(context: BrickContext): NavContribution = navContribution("runtime", "runtime") {
        destination<ExportHome>(
            id = "export.home", path = "/export", labelKey = "export.nav.home", icon = NavIcons.Home,
            codec = PathCodec.constant(ExportHome), requiresAuth = false,
        ) { context.koin.get<String>() }
    }
}

/** What `exportNavManifest` runs: reflection on the descriptor, an empty graph, the coordinates from the build. */
@OptIn(InternalStapelApi::class)
class NavManifestExportTest {
    @Test
    fun theExportWritesTheDescriptorsManifestWithTheBuildsCoordinates() {
        val out = File.createTempFile("nav-manifest", ".json")
        stapel.app.di.export.main(arrayOf(ExportBrick::class.java.name, "g:stapel-export-ui", out.absolutePath))
        val o = Json.parseToJsonElement(out.readText()).jsonObject
        assertEquals("g:stapel-export-ui", o.getValue("artifact").jsonPrimitive.content)
        kotlin.test.assertFalse("version" in o, "the committed manifest carries no version: $o")
        assertEquals("export.home", o.getValue("destinations").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun aMissingDescriptorIsNamed() {
        val e = assertFailsWith<IllegalStateException> {
            stapel.app.di.export.main(arrayOf("stapel.nope.di.NopeBrick", "g:a", File.createTempFile("missing", ".json").absolutePath))
        }
        assertTrue("no brick descriptor 'stapel.nope.di.NopeBrick'" in e.message.orEmpty(), e.message)
    }
}
