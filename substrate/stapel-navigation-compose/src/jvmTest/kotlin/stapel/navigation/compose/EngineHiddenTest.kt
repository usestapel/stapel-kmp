package stapel.navigation.compose

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** N1: no engine type (Navigation 3, navigationevent, lifecycle) in any public signature. */
class EngineHiddenTest {
    private val engineMarkers = listOf("androidx/navigation3", "androidx.navigation3", "androidx/navigationevent", "androidx.navigationevent", "androidx/lifecycle", "androidx.lifecycle")

    @Test
    fun theCommittedAbiDumpsNameNoEngineType() {
        val dumps = File("api").walkTopDown().filter { it.isFile && it.name.endsWith(".api") }.toList()
        assertTrue(dumps.size >= 3, "ABI dumps not found from ${File(".").absolutePath}: $dumps")
        for (dump in dumps) {
            val leaks = dump.readLines().filter { line -> engineMarkers.any { it in line } }
            assertTrue(leaks.isEmpty(), "${dump.path} exposes engine types:\n${leaks.joinToString("\n")}")
        }
    }
}
