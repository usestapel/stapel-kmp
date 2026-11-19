package stapel.shell

import stapel.navigation.DestinationId
import stapel.navigation.EmbedSurface
import stapel.navigation.SlotId
import stapel.navigation.compose.navContribution
import stapel.ui.StapelSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShellModelTest {
    @Test
    fun chromeByTemplateAndWidth() {
        assertEquals(Chrome.BottomBar, chromeFor(ShellTemplate.AdaptiveTabs, StapelSizeClass.Phone))
        assertEquals(Chrome.Rail, chromeFor(ShellTemplate.AdaptiveTabs, StapelSizeClass.Tablet))
        assertEquals(Chrome.Drawer, chromeFor(ShellTemplate.AdaptiveTabs, StapelSizeClass.Desktop))
        for (size in StapelSizeClass.entries) assertEquals(Chrome.None, chromeFor(ShellTemplate.Stack, size))
    }

    @Test
    fun slotsRenderedFollowTheTable() {
        val top = setOf(Slots.TopbarStart, Slots.TopbarCenter, Slots.TopbarEnd)
        assertEquals(top + Slots.Bottombar + Slots.SheetFoot, slotsRendered(ShellTemplate.AdaptiveTabs, StapelSizeClass.Phone))
        assertEquals(top + Slots.SidenavHead + Slots.SidenavFoot, slotsRendered(ShellTemplate.AdaptiveTabs, StapelSizeClass.Tablet))
        assertEquals(top + Slots.SidenavHead + Slots.SidenavFoot, slotsRendered(ShellTemplate.AdaptiveTabs, StapelSizeClass.Desktop))
        assertEquals(top, slotsRendered(ShellTemplate.Stack, StapelSizeClass.Desktop))
    }

    @Test
    fun overflowMovesTheRestBehindMore() {
        val tabs = (1..7).map { DestinationId("t$it") }
        assertEquals(tabs.take(5) to emptyList(), splitTabs(tabs.take(5), 5))
        val (bar, more) = splitTabs(tabs, 5)
        assertEquals(tabs.take(4), bar, "four tabs + More = five items")
        assertEquals(tabs.drop(4), more)
        assertFailsWith<IllegalArgumentException> { splitTabs(tabs, 1) }
    }

    @Test
    fun offeredPlanTakesRenderedChromeSlotsOnlyInOrder() {
        val c = navContribution("t", "0") {
            embed("e.end2", SlotId("topbar.end"), order = 2) {}
            embed("e.end1", SlotId("topbar.end"), order = 1) {}
            embed("e.footer", SlotId("footer"), order = 1) {}
            embed("e.page", SlotId("page.actions"), order = 1, scope = DestinationId("x")) {}
            embed("e.bar", SlotId("bottombar"), order = 1, surface = EmbedSurface.Member) {}
        }
        val tabs = SlotPlan.offered(ShellTemplate.AdaptiveTabs, listOf(c)).filled.mapValues { it.value.map { e -> e.value } }.mapKeys { it.key.value }
        assertEquals(mapOf("topbar.end" to listOf("e.end1", "e.end2"), "bottombar" to listOf("e.bar")), tabs)
        val stack = SlotPlan.offered(ShellTemplate.Stack, listOf(c)).filled.keys.map { it.value }
        assertEquals(listOf("topbar.end"), stack)
    }

    @Test
    fun templatesJsonIsDeterministic() {
        assertEquals(shellTemplatesJson(), shellTemplatesJson())
    }
}
