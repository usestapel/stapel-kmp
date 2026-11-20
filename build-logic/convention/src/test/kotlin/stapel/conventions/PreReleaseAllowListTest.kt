package stapel.conventions

import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The scoped pre-release allow-list: a vendor's transitive pre-release, in one module, under one stable parent. */
class PreReleaseAllowListTest {
    private val entries = parsePreReleaseAllowList(
        listOf(
            "# comment",
            "org.example:dev-only:2.0.0-rc1   # unscoped, development",
            "androidx.privacysandbox.ads:ads-adservices:1.1.0-beta11 via com.google.android.gms:play-services-measurement-impl in stapel-analytics-firebase",
            "androidx.privacysandbox.ads:ads-adservices-java:1.1.0-beta11 via com.google.android.gms:play-services-measurement-impl in stapel-analytics-firebase  # x",
        ),
    )
    private val ads = "androidx.privacysandbox.ads:ads-adservices:1.1.0-beta11"
    private val adsJava = "androidx.privacysandbox.ads:ads-adservices-java:1.1.0-beta11"
    private val impl = "com.google.android.gms:play-services-measurement-impl:24.0.0"

    @Test
    fun theVendorsTransitivePreReleaseIsAllowedInItsModuleUnderItsParent() {
        assertTrue(preReleaseAllowed(entries, "stapel-analytics-firebase", adsJava, setOf(impl)))
        assertTrue(preReleaseAllowed(entries, "stapel-analytics-firebase", ads, setOf(impl, adsJava)), "a scoped sibling counts as the parent")
    }

    @Test
    fun declaredDirectlyItFails() {
        assertFalse(preReleaseAllowed(entries, "stapel-analytics-firebase", ads, setOf("project")))
        assertFalse(preReleaseAllowed(entries, "stapel-analytics-firebase", ads, setOf(impl, "project")))
    }

    @Test
    fun inAnotherModuleItFails() {
        assertFalse(preReleaseAllowed(entries, "stapel-analytics", ads, setOf(impl)))
        assertFalse(preReleaseAllowed(entries, "stapel-push-firebase", adsJava, setOf(impl)))
    }

    @Test
    fun underAnotherParentOrAPreReleaseParentItFails() {
        assertFalse(preReleaseAllowed(entries, "stapel-analytics-firebase", ads, setOf("com.google.android.gms:play-services-ads:25.5.0")))
        assertFalse(preReleaseAllowed(entries, "stapel-analytics-firebase", ads, setOf("com.google.android.gms:play-services-measurement-impl:25.0.0-beta01")))
        assertFalse(preReleaseAllowed(entries, "stapel-analytics-firebase", ads, emptySet()))
    }

    @Test
    fun anUnscopedEntryStaysAnywhereAndAMalformedLineIsRefused() {
        assertTrue(preReleaseAllowed(entries, "stapel-result", "org.example:dev-only:2.0.0-rc1", setOf("project")))
        assertFalse(preReleaseAllowed(entries, "stapel-result", "org.example:dev-only:2.0.0-rc2", setOf("project")))
        assertThrows<GradleException> { parsePreReleaseAllowList(listOf("a:b:1.0-rc1 via c:d")) }
        assertThrows<GradleException> { parsePreReleaseAllowList(listOf("a:b:1.0-rc1 via c:d:1 in m")) }
    }
}
