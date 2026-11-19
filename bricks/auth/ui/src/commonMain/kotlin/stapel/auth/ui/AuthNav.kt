package stapel.auth.ui

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import stapel.navigation.DestinationId
import stapel.navigation.EmbedSurface
import stapel.navigation.FlowId
import stapel.navigation.NavIcons
import stapel.navigation.PathCodec
import stapel.navigation.Placement
import stapel.navigation.Route
import stapel.navigation.RouteAddress
import stapel.navigation.SlotId
import stapel.navigation.compose.EmbedScope
import stapel.navigation.compose.NavContribution
import stapel.navigation.compose.ScreenScope
import stapel.navigation.compose.navContribution

/** Ids of what this library contributes to navigation; equal to the web pair's manifest ids. */
public object AuthNavIds {
    /** The sign-in screen, web `/login`. */
    public val Login: DestinationId = DestinationId("auth.login")

    /** Security settings (v0: sessions and devices), web `security` under `profiles.settings`. */
    public val Security: DestinationId = DestinationId("auth.security")

    /** The parent `auth.security` hangs from; dropped with a reason when no installed library declares it. */
    public val SecurityParent: DestinationId = DestinationId("profiles.settings")

    /** The account control in the top bar. */
    public const val ACCOUNT_MENU: String = "auth.account_menu"

    /** The top-bar slot the account control goes to. */
    public val AccountMenuSlot: SlotId = SlotId("topbar.end")

    /** The container-owned account section the account control opens. */
    public val AccountRoot: DestinationId = DestinationId("account.root")

    /** The presenter scope the sign-in steps share. */
    public val SignInFlow: FlowId = FlowId("auth.sign_in")
}

/**
 * Route of `auth.login`.
 *
 * @property next where to go after sign-in (web `?next=`); null = the policy's home or the pending return.
 */
@Serializable
public data class SignInRoute(val next: String? = null) : Route

/** Route of `auth.security`. */
@Serializable
public data object SecurityRoute : Route

/** Maven coordinates of this library without version (the manifest's `package`). */
public const val AUTH_UI_ARTIFACT: String = "io.github.usestapel:stapel-auth-ui"

private val SignInCodec: PathCodec<SignInRoute> = PathCodec(
    parse = { _, query -> SignInRoute(query["next"]?.firstOrNull()) },
    format = { r -> RouteAddress(query = r.next?.let { mapOf("next" to listOf(it)) }.orEmpty()) },
)

/**
 * This library's navigation contribution: `auth.login` (`/login`, public, not in menus),
 * `auth.security` (`security` under `profiles.settings`, member) and the embed `auth.account_menu`
 * (`topbar.end`, members, opens `account.root`). Ids, paths, placement, order and visibility equal the
 * web pair's `nav-manifest.json` (fixture-checked). The contents are the stateful bindings the app
 * passes in (the screens they draw come from an [AuthScreens] table); a project replaces one with
 * `NavContribution.overriding(id, …)`.
 *
 * @param version this library's version (the manifest's `version`).
 * @param signIn draws `auth.login`.
 * @param security draws `auth.security`.
 * @param accountMenu draws the account control; null = no embed.
 */
public fun authNavContribution(
    version: String,
    signIn: @Composable ScreenScope.(SignInRoute) -> Unit,
    security: @Composable ScreenScope.(SecurityRoute) -> Unit,
    accountMenu: (@Composable EmbedScope.() -> Unit)? = null,
): NavContribution = navContribution(AUTH_UI_ARTIFACT, version) {
    destination<SignInRoute>(
        id = AuthNavIds.Login.value,
        path = "/login",
        labelKey = AuthUiStrings.NavLogin.key,
        icon = NavIcons.Login,
        codec = SignInCodec,
        placement = Placement.Top,
        menuVisibleDefault = false,
        requiresAuth = false,
        order = 0,
        flow = AuthNavIds.SignInFlow,
        content = signIn,
    )
    destination<SecurityRoute>(
        id = AuthNavIds.Security.value,
        path = "security",
        labelKey = AuthUiStrings.NavSecurity.key,
        icon = NavIcons.ShieldCheck,
        codec = PathCodec.constant(SecurityRoute),
        placement = Placement.Submenu(AuthNavIds.SecurityParent),
        menuVisibleDefault = true,
        requiresAuth = true,
        surface = null,
        order = 10,
        content = security,
    )
    if (accountMenu != null) {
        embed(
            id = AuthNavIds.ACCOUNT_MENU,
            slot = AuthNavIds.AccountMenuSlot,
            order = 90,
            surface = EmbedSurface.Member,
            opens = AuthNavIds.AccountRoot,
            content = accountMenu,
        )
    }
}
