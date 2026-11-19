package shop

import kotlinx.coroutines.flow.StateFlow
import org.koin.core.module.Module
import stapel.app.di.BrickContext
import stapel.app.di.BrickSession
import stapel.app.di.StapelBrick
import stapel.auth.StapelAuth
import stapel.auth.di.authModule
import stapel.auth.ui.AuthAccountMenu
import stapel.auth.ui.AuthNavBridge
import stapel.auth.ui.AuthNavIds
import stapel.auth.ui.AuthPresenterFactory
import stapel.auth.ui.AuthSessions
import stapel.auth.ui.AuthSignIn
import stapel.auth.ui.AuthUiBundle
import stapel.auth.ui.authErrorCatalogs
import stapel.auth.ui.authNavContribution
import stapel.i18n.I18nBundle
import stapel.mvi.compose.PresenterFactory
import stapel.navigation.DestinationId
import stapel.navigation.NavAuthState
import stapel.navigation.StapelNavigator
import stapel.navigation.compose.NavContribution
import stapel.result.text.ErrorCatalog

/**
 * The auth brick's descriptor, written in the app until `stapel-auth-ui` ships its own
 * `stapel.auth.ui.di.AuthBrick` (then this file is deleted and `bricks(AuthBrick())` used). It calls
 * only public API of stapel-auth / stapel-auth-ui — the same calls the library descriptor makes.
 */
class ShopAuthBrick : StapelBrick {
    override val key: String = "auth"
    override val strings: List<I18nBundle> = listOf(AuthUiBundle)
    override val errorCatalogs: List<ErrorCatalog> = authErrorCatalogs()

    override fun modules(): List<Module> = listOf(authModule(bindCacheToSession = false))

    override fun navigation(context: BrickContext): NavContribution = authNavContribution(
        version = "app",
        signIn = { AuthSignIn(context.koin.get<StapelAuth>(), onSignedIn = {}) },
        security = { AuthSessions(context.koin.get<StapelAuth>()) },
        accountMenu = { AuthAccountMenu(context.koin.get<StapelAuth>(), onOpen = { open() }) },
    )

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

    override fun presenters(context: BrickContext, next: PresenterFactory): PresenterFactory =
        AuthPresenterFactory(context.koin.get<StapelAuth>(), next)
}
