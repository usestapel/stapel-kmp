package shop

import org.koin.dsl.module
import stapel.navigation.resolveNav
import stapel.session.vault.InMemoryTokenVault
import stapel.session.vault.TokenVault
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The runtime navigation equals what the scaffold planned into stapel.composition.json (`routes`,
 * `signIn`, the top-bar slot): the generated app's NavGateTest. No backend needed.
 */
class NavGateTest {
    private val planned = mapOf(
        "auth.login" to "/login",
        "shop.home" to "/app/home",
        "shop.order" to "/app/orders/:orderId",
        // The profiles brick, as the auth+profiles route plan of stapel-tools places it (auth.security
        // under the profiles settings container).
        "profiles.public" to "/u/:userId",
        "profiles.settings" to "/app/settings",
        "auth.security" to "/app/settings/security",
        "profiles.language" to "/app/settings/language",
        "profiles.notifications" to "/app/settings/notifications",
        "profiles.contacts" to "/app/settings/contacts",
        "profiles.connections" to "/app/connections",
    )

    @Test
    fun runtimeNavigationEqualsThePlannedComposition() {
        val app = createShopApp(testPlatformContext(), testTarget, "http://127.0.0.1:9/") {
            overrides(module { single<TokenVault> { InMemoryTokenVault() } })
            global(false)
        }
        try {
            val nav = resolveNav(app.contributions.map { it.manifest })
            val routed = nav.byId.entries.associate { it.key.value to it.value.absolutePath }
            assertEquals(planned, routed.filterKeys { it in planned })
            assertEquals("auth.login", app.policy.signIn?.value)
            assertEquals(listOf("auth.account_menu"), app.slots.filled[stapel.shell.Slots.TopbarEnd]?.map { it.value })
        } finally {
            app.close()
        }
    }
}
