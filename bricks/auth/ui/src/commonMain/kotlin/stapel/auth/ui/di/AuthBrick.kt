package stapel.auth.ui.di

import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.module.Module
import stapel.app.di.BrickContext
import stapel.app.di.BrickSession
import stapel.app.di.StapelBrick
import stapel.auth.AuthConfig
import stapel.auth.StapelAuth
import stapel.auth.di.authModule
import stapel.auth.platform.AuthPlatform
import stapel.auth.ui.AuthAccountMenu
import stapel.auth.ui.AuthNavBridge
import stapel.auth.ui.AuthNavIds
import stapel.auth.ui.AuthPresenterFactory
import stapel.auth.ui.AuthSessions
import stapel.auth.ui.AuthSignIn
import stapel.auth.ui.AuthUiBundle
import stapel.auth.ui.authErrorCatalogs
import stapel.auth.ui.authNavContribution
import stapel.auth.ui.generated.StapelBuildInfo
import stapel.i18n.I18nBundle
import stapel.mvi.compose.PresenterFactory
import stapel.navigation.DestinationId
import stapel.navigation.NavAuthState
import stapel.navigation.StapelNavigator
import stapel.navigation.compose.NavContribution
import stapel.result.text.ErrorCatalog

/**
 * The auth brick, for `stapelApp { bricks(AuthBrick()) }`: [authModule] (the cache is bound to the
 * session by the app builder, never here), the sign-in, security and account-menu navigation bound to
 * the app's [StapelAuth], the auth strings and error texts, the auth presenters, and the session the
 * navigation guards read (through [AuthNavBridge], so a user whose session expired comes back to the
 * screens they had open). Needs no capability from the app.
 *
 * @param config the auth library's options (guest policy, method fence, attribution).
 * @param platform platform adapters (browser, passkeys, identity providers) the app supplies.
 */
public class AuthBrick(
    private val config: AuthConfig = AuthConfig(),
    private val platform: AuthPlatform = AuthPlatform(),
) : StapelBrick {
    /** `auth`, the backend library key. */
    override val key: String = "auth"

    /** [AuthUiBundle] (en, ru). */
    override val strings: List<I18nBundle> = listOf(AuthUiBundle)

    /** The contract's English texts and the Russian catalog ([authErrorCatalogs]). */
    override val errorCatalogs: List<ErrorCatalog> = authErrorCatalogs()

    /** `authModule(config, platform, bindCacheToSession = false)`. */
    override fun modules(): List<Module> = listOf(authModule(config, platform, bindCacheToSession = false))

    /** `auth.login`, `auth.security` and the `auth.account_menu` embed over the app's [StapelAuth]. */
    override fun navigation(context: BrickContext): NavContribution = authNavContribution(
        version = StapelBuildInfo.VERSION,
        signIn = {
            val auth = remember(context) { context.koin.get<StapelAuth>() }
            // The navigation policy leaves sign-in by itself once a session exists.
            AuthSignIn(auth, onSignedIn = {})
        },
        security = { AuthSessions(remember(context) { context.koin.get<StapelAuth>() }) },
        accountMenu = { AuthAccountMenu(remember(context) { context.koin.get<StapelAuth>() }, onOpen = { open() }) },
    )

    /** The sign-in owner: the session's navigation image, its restore, and the way back after an expiry. */
    override fun session(context: BrickContext): BrickSession {
        val auth = context.koin.get<StapelAuth>()
        val bridge = AuthNavBridge(auth, context.appScope)
        return object : BrickSession {
            override val signIn: DestinationId = AuthNavIds.Login
            override fun authState(): StateFlow<NavAuthState> = bridge.authState()
            override suspend fun restore() = auth.session.restore()
            override fun attach(navigator: StapelNavigator) = bridge.attach(navigator)
        }
    }

    /** [AuthPresenterFactory] over the app's [StapelAuth], chained before [next]. */
    override fun presenters(context: BrickContext, next: PresenterFactory): PresenterFactory =
        AuthPresenterFactory(context.koin.get<StapelAuth>(), next)
}
