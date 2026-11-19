package stapel.navigation

import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NavStateCodecTest {
    private val codec = NavStateCodec(listOf(authManifest, appManifest))

    @Test
    fun navStateSurvivesAnEncodeDecodeRoundTripWithTwoContributions() {
        val s = reduceAll(
            initialNavState(testNav, redirectPolicy, NavAuthState.Member), redirectPolicy,
            NavCommand.Push(ThreadRoute("ü / 1")),
            NavCommand.SelectTab(DestinationId("profiles.settings")),
            NavCommand.Push(SecurityRoute),
            NavCommand.OpenStack(listOf(SectionRoute(DestinationId("account.root")), PrivacyRoute)),
            NavCommand.Push(ItemRoute("9", "photos")),
            NavCommand.Push(ConfirmRoute),
        ).state.copy(pendingReturn = listOf(QrRoute("k")))
        val text = codec.encode(s)
        val restored = codec.decode(text)
        assertEquals(s, restored)
        assertEquals(s.visible, restored.visible)
        // and the restored state keeps working: uids continue, no collision with restored entries
        val next = reduceNav(restored, NavCommand.Push(ThreadRoute("2")), testNav, redirectPolicy).state
        val uids = (next.tabStacks.values.flatten() + next.root + next.overlays).map { it.uid }
        assertEquals(uids.size, uids.toSet().size)
    }

    @Test
    fun aRouteTypeNobodyRegisteredCannotBeRestored() {
        val s = initialNavState(testNav, redirectPolicy, NavAuthState.Member)
        val text = codec.encode(s)
        val withoutApp = NavStateCodec(listOf(authManifest))
        assertFailsWith<SerializationException> { withoutApp.decode(text) }
        assertTrue("InboxRoute" in text)
    }
}
