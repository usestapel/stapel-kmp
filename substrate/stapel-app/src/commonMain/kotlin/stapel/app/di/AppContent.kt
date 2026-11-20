package stapel.app.di

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import stapel.i18n.StringKey
import stapel.mvi.compose.di.KoinPresenterFactory
import stapel.mvi.compose.LocalPresenterFactory
import stapel.navigation.StapelNavigator
import stapel.navigation.compose.rememberStapelNavigator
import stapel.runtime.Analytics
import stapel.runtime.StapelLogger
import stapel.runtime.info
import stapel.shell.ShellConfig
import stapel.shell.StapelShell
import stapel.ui.LocalStapelErrorText
import stapel.ui.LocalStapelStrings
import stapel.ui.stapelString

/** Log tag of the app's lifecycle markers (`stapel.app.ready`, `stapel.nav <destination id>`). */
public const val APP_LOG_TAG: String = "stapel.app"

/**
 * The whole app UI: theme, strings and error texts, presenter creation (the bricks' factories, then
 * Koin), the navigator, and the shell around the navigation host. Restores the session brick's stored
 * session once, and writes two log markers through the app's [StapelLogger]: `stapel.app.ready` on the
 * first composition and `stapel.nav <id>` whenever the topmost destination changes (what launch and
 * deep-link smokes wait for). On the same change it records `Analytics.page(<id>)` when the graph
 * binds an [Analytics] (the optional `stapel-analytics` hub); without one nothing is recorded.
 *
 * @param onNavigator receives the navigator once created (tests, platform glue).
 */
@Composable
public fun StapelAppContent(app: StapelApp, modifier: Modifier = Modifier, onNavigator: (StapelNavigator) -> Unit = {}) {
    val logger = remember(app) { app.koin.get<StapelLogger>() }
    val analytics = remember(app) { app.koin.getOrNull<Analytics>() }
    val presenters = remember(app) {
        app.bricks.foldRight(KoinPresenterFactory(app.koin) as stapel.mvi.compose.PresenterFactory) { brick, next ->
            brick.presenters(app.context, next)
        }
    }
    CompositionLocalProvider(
        LocalStapelStrings provides app.strings,
        LocalStapelErrorText provides app.errorTexts,
        LocalPresenterFactory provides presenters,
    ) {
        app.theme {
            val navigator = rememberStapelNavigator(app.contributions, app.policy, app.navAuth, app.deepLinks, app.navOverrides)
            DisposableEffect(navigator) {
                app.attach(navigator)
                onNavigator(navigator)
                onDispose { }
            }
            LaunchedEffect(app) {
                logger.info(APP_LOG_TAG) { "stapel.app.ready" }
                app.session?.restore()
            }
            LaunchedEffect(navigator) {
                navigator.state
                    .map { s -> s.top?.let { navigator.nav.destinationOf(it.route)?.id?.value } }
                    .distinctUntilChanged()
                    .collect { id ->
                        if (id != null) {
                            logger.info(APP_LOG_TAG) { "stapel.nav $id" }
                            analytics?.page(id)
                        }
                    }
            }
            val config = remember(app) {
                ShellConfig(
                    contributions = app.contributions,
                    template = app.template,
                    slots = app.slots,
                    policy = app.policy,
                    auth = app.navAuth,
                    overrides = app.navOverrides,
                    deepLinks = app.deepLinks,
                    capabilities = app.capabilities,
                    label = { key -> stapelString(StringKey(key)) },
                )
            }
            StapelShell(navigator, config, modifier)
        }
    }
}
