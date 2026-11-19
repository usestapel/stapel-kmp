package stapel.conventions

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryLinesTest {
    @Test
    fun `twin groups`() {
        assertEquals("androidx.lifecycle", androidxTwinGroup("org.jetbrains.androidx.lifecycle"))
        assertEquals("androidx.annotation", androidxTwinGroup("org.jetbrains.compose.annotation-internal"))
        assertNull(androidxTwinGroup("org.jetbrains.compose.ui"))
        assertNull(androidxTwinGroup("androidx.lifecycle"))
    }

    @Test
    fun `every redirect group maps to its own twin`() {
        REDIRECT_SHIMS.forEach { (jb, target) -> assertEquals(androidxTwinGroup(jb), target.first, jb) }
    }

    private val shim = "org.jetbrains.androidx.lifecycle:lifecycle-common:2.9.6"
    private val real = "androidx.lifecycle:lifecycle-common:2.11.0"
    private val redirectEdge = setOf("org.jetbrains.androidx.lifecycle:lifecycle-common-jvm" to "androidx.lifecycle:lifecycle-common")

    @Test
    fun `a twin on a klib classpath fails even as a redirect`() {
        assertEquals(listOf("$shim next to $real"), twinViolations(listOf(shim, real), redirectEdge, klib = true))
    }

    @Test
    fun `a redirect twin on a jar classpath passes, a non-redirect twin fails`() {
        assertTrue(twinViolations(listOf(shim, real), redirectEdge, klib = false).isEmpty())
        assertEquals(listOf("$shim next to $real"), twinViolations(listOf(shim, real), emptySet(), klib = false))
    }

    @Test
    fun `different modules of twin groups are not twins`() {
        val nav3 = listOf("org.jetbrains.androidx.navigation3:navigation3-ui:1.2.0", "androidx.navigation3:navigation3-runtime:1.2.0")
        assertTrue(twinViolations(nav3, emptySet(), klib = true).isEmpty())
    }

    @Test
    fun `klib classpaths`() {
        listOf("metadataIosMainCompileClasspath", "iosArm64CompileKlibraries", "allSourceSetsCompileDependenciesMetadata")
            .forEach { assertTrue(isKlibClasspath(it), it) }
        listOf("jvmRuntimeClasspath", "androidCompileClasspath").forEach { assertTrue(!isKlibClasspath(it), it) }
    }
}
