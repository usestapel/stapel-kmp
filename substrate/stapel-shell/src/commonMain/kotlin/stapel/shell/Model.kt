package stapel.shell

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import stapel.navigation.CapabilityKey
import stapel.navigation.DeepLinkConfig
import stapel.navigation.DestinationId
import stapel.navigation.EmbedId
import stapel.navigation.NavAuthSource
import stapel.navigation.NavOverrides
import stapel.navigation.NavPolicy
import stapel.navigation.SlotId
import stapel.navigation.WidthClass
import stapel.navigation.compose.NavContribution
import stapel.ui.StapelSizeClass

/** The app shell layouts; published in `shell-templates.json` for the scaffold. */
public enum class ShellTemplate {
    /** Primary navigation by width: bottom bar on a phone, rail on a tablet, drawer on a desktop. */
    AdaptiveTabs,

    /** No primary navigation chrome: single-purpose apps. */
    Stack,
}

/** The chrome slots the shell renders and the page slot screens render; ids equal the web's. */
public object Slots {
    /** Start of the top bar. */
    public val TopbarStart: SlotId = SlotId("topbar.start")

    /** Centre of the top bar (replaces the title). */
    public val TopbarCenter: SlotId = SlotId("topbar.center")

    /** End of the top bar. */
    public val TopbarEnd: SlotId = SlotId("topbar.end")

    /** Head of the rail or drawer. */
    public val SidenavHead: SlotId = SlotId("sidenav.head")

    /** Foot of the rail or drawer. */
    public val SidenavFoot: SlotId = SlotId("sidenav.foot")

    /** Foot of the phone "More" sheet. */
    public val SheetFoot: SlotId = SlotId("sheet.foot")

    /** Above the phone bottom bar. */
    public val Bottombar: SlotId = SlotId("bottombar")

    /** Page actions, rendered by the page through `ScreenScope.PageSlot`. */
    public val PageActions: SlotId = SlotId("page.actions")

    /** Every chrome slot, in render order. */
    public val chrome: List<SlotId> = listOf(TopbarStart, TopbarCenter, TopbarEnd, SidenavHead, SidenavFoot, SheetFoot, Bottombar)
}

/** Which chrome draws the primary navigation. */
internal enum class Chrome { BottomBar, Rail, Drawer, None }

internal fun chromeFor(template: ShellTemplate, size: StapelSizeClass): Chrome = when (template) {
    ShellTemplate.Stack -> Chrome.None
    ShellTemplate.AdaptiveTabs -> when (size) {
        StapelSizeClass.Phone -> Chrome.BottomBar
        StapelSizeClass.Tablet -> Chrome.Rail
        StapelSizeClass.Desktop -> Chrome.Drawer
    }
}

/** The chrome slots [template] renders at [size] (the ARCH-NAV §3 table). */
internal fun slotsRendered(template: ShellTemplate, size: StapelSizeClass): Set<SlotId> {
    val top = setOf(Slots.TopbarStart, Slots.TopbarCenter, Slots.TopbarEnd)
    return when (chromeFor(template, size)) {
        Chrome.BottomBar -> top + Slots.Bottombar + Slots.SheetFoot
        Chrome.Rail, Chrome.Drawer -> top + Slots.SidenavHead + Slots.SidenavFoot
        Chrome.None -> top
    }
}

internal fun StapelSizeClass.toWidthClass(): WidthClass = when (this) {
    StapelSizeClass.Phone -> WidthClass.Phone
    StapelSizeClass.Tablet -> WidthClass.Tablet
    StapelSizeClass.Desktop -> WidthClass.Desktop
}

/** Id of the synthetic "More" bar item. */
internal const val MORE_ID: String = "stapel.shell.more"

/** Bar items and overflow: at most [maxTabs] items, the last one "More" when tabs overflow. */
internal fun splitTabs(tabs: List<DestinationId>, maxTabs: Int): Pair<List<DestinationId>, List<DestinationId>> {
    require(maxTabs >= 2) { "maxTabs must leave room for 'More'" }
    return if (tabs.size <= maxTabs) tabs to emptyList() else tabs.take(maxTabs - 1) to tabs.drop(maxTabs - 1)
}

/**
 * Which embeds fill which chrome slot: the scaffold's decision (stapel-tools `plan_slots`), or
 * [offered] for a hand-wired app. An embed not listed here is not rendered.
 *
 * @property filled embed ids per slot.
 */
public class SlotPlan(public val filled: Map<SlotId, List<EmbedId>>) {
    /** Factories. */
    public companion object {
        /** Every chrome embed of [contributions] whose slot [template] renders at some width. */
        public fun offered(template: ShellTemplate, contributions: List<NavContribution>): SlotPlan {
            val rendered = StapelSizeClass.entries.flatMap { slotsRendered(template, it) }.toSet()
            val filled = contributions.flatMap { it.manifest.embeds }
                .filter { it.scope == null && it.slot in rendered }
                .sortedWith(compareBy({ it.order }, { it.id.value }))
                .groupBy({ it.slot }, { it.id })
            return SlotPlan(filled)
        }
    }
}

/** A live count shown as a badge on a destination's navigation item. */
public fun interface BadgeSource {
    /** The count; zero hides the badge. */
    public fun count(): Flow<Int>
}

/**
 * Everything the shell needs. The theme (`StapelTheme` with a skin) is applied by the caller.
 *
 * @property contributions the installed UI libraries.
 * @property template the shell layout.
 * @property slots which embeds fill which chrome slot.
 * @property policy sign-in / home destinations and the tab limit.
 * @property auth the app's auth state source.
 * @property overrides project nav overrides.
 * @property deepLinks the app's link space, or null for none.
 * @property badges badge counts by destination.
 * @property capabilities platform capabilities present.
 * @property label resolves an i18n key to text.
 */
public class ShellConfig(
    public val contributions: List<NavContribution>,
    public val template: ShellTemplate = ShellTemplate.AdaptiveTabs,
    public val slots: SlotPlan,
    public val policy: NavPolicy,
    public val auth: NavAuthSource,
    public val overrides: NavOverrides = NavOverrides(),
    public val deepLinks: DeepLinkConfig? = null,
    public val badges: Map<DestinationId, BadgeSource> = emptyMap(),
    public val capabilities: Set<CapabilityKey> = emptySet(),
    public val label: @Composable (key: String) -> String,
)

/** The `shell-templates.json` the scaffold mirrors: templates and the chrome slots each renders per width. */
public fun shellTemplatesJson(): String {
    val root = buildJsonObject {
        put("version", 1)
        putJsonArray("templates") {
            for (t in ShellTemplate.entries) {
                add(
                    buildJsonObject {
                        put("id", t.name)
                        putJsonObject("slots") {
                            for (size in StapelSizeClass.entries) {
                                val rendered = slotsRendered(t, size)
                                put(
                                    size.toWidthClass().name.lowercase(),
                                    JsonArray(Slots.chrome.filter { it in rendered }.map { JsonPrimitive(it.value) }),
                                )
                            }
                        }
                        putJsonObject("chrome") {
                            for (size in StapelSizeClass.entries) put(size.toWidthClass().name.lowercase(), chromeFor(t, size).name)
                        }
                    },
                )
            }
        }
    }
    return templatesJson.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), root) + "\n"
}

private val templatesJson = Json { prettyPrint = true }
