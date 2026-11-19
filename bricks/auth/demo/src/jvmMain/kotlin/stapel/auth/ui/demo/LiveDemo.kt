package stapel.auth.ui.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import stapel.auth.StapelAuth
import stapel.auth.session.AuthState
import stapel.auth.ui.AuthAccountMenu
import stapel.auth.ui.AuthNavIds
import stapel.auth.ui.AuthSessions
import stapel.auth.ui.AuthSignIn
import stapel.auth.ui.SecurityRoute
import stapel.auth.ui.authNavContribution
import stapel.auth.ui.rememberSignOut
import stapel.navigation.NavIcons
import stapel.navigation.NavPolicy
import stapel.navigation.PathCodec
import stapel.navigation.Placement
import stapel.navigation.Route
import stapel.navigation.compose.NavContribution
import stapel.navigation.compose.StapelNavHost
import stapel.navigation.compose.navContribution
import stapel.navigation.compose.rememberStapelNavigator
import stapel.result.StapelResult
import stapel.tokens.ThemeMode
import stapel.ui.StapelButton
import stapel.ui.StapelPage
import stapel.ui.StapelProgress
import stapel.ui.StapelText
import stapel.ui.StapelTheme
import stapel.ui.skin.ButtonVariant

/** The protected placeholder the demo opens after sign-in. */
@Serializable
data object HomeRoute : Route

/** The container the security screen hangs from (normally the profiles brick's settings section). */
@Serializable
data object SettingsRoute : Route

/** Test tags of the demo's own screen. */
object DemoTags {
    const val HOME = "demo.home"
    const val USER = "demo.home.user"
    const val RELOAD = "demo.home.reload"
    const val SIGN_OUT = "demo.home.sign_out"
    const val SECURITY = "demo.home.security"
    const val PROFILE_ERROR = "demo.home.error"
}

/** The demo app's own contribution: a protected home and the settings parent of `auth.security`. */
fun demoContribution(auth: StapelAuth): NavContribution = navContribution("demo:stapel-auth-ui-demo", "0") {
    destination<HomeRoute>(
        id = "demo.home", path = "/app", labelKey = "demo.home", icon = NavIcons.Home,
        codec = PathCodec.constant(HomeRoute), requiresAuth = true, order = 0,
    ) { HomeScreen(auth, onSecurity = { navigator.navigate(SecurityRoute) }) }
    destination<SettingsRoute>(
        id = AuthNavIds.SecurityParent.value, path = "settings", labelKey = "demo.settings", icon = NavIcons.Settings,
        codec = PathCodec.constant(SettingsRoute), placement = Placement.Top, requiresAuth = true, order = 1,
    ) { HomeScreen(auth, onSecurity = { navigator.navigate(SecurityRoute) }) }
}

/** The auth library's contribution bound to [auth]. */
fun boundAuthContribution(auth: StapelAuth): NavContribution = authNavContribution(
    version = "0",
    signIn = { AuthSignIn(auth, onSignedIn = {}) },
    security = { AuthSessions(auth) },
    accountMenu = { AuthAccountMenu(auth, onOpen = { open() }) },
)

/** The live app: the navigation host with the auth destinations and the demo home, on [stack]. */
@Composable
fun LiveApp(stack: DemoStack, onNavigator: (stapel.navigation.StapelNavigator) -> Unit = {}) {
    val contributions = remember(stack) { listOf(boundAuthContribution(stack.auth), demoContribution(stack.auth)) }
    val navigator = rememberStapelNavigator(
        contributions,
        NavPolicy(signIn = AuthNavIds.Login, home = stapel.navigation.DestinationId("demo.home")),
        stack.navBridge,
    )
    DisposableEffect(navigator) {
        stack.navBridge.attach(navigator)
        onNavigator(navigator)
        onDispose { }
    }
    LaunchedEffect(stack) { stack.auth.session.restore() }
    StapelNavHost(
        navigator,
        contributions,
        Modifier.fillMaxSize(),
        unavailable = { d, _ -> StapelText(d.id.value) },
        waiting = { StapelProgress() },
    )
}

/** `--base-url` mode of the demo window. */
@Composable
fun LiveDemo(baseUrl: String) {
    val stack = remember(baseUrl) { DemoStack(baseUrl) }
    DisposableEffect(stack) { onDispose { stack.close() } }
    DemoTheme(DemoSkin.Material3, "en", ThemeMode.Light) { LiveApp(stack) }
}

/** The protected placeholder: who is signed in, reload the profile (exercises refresh), security, sign out. */
@Composable
fun HomeScreen(auth: StapelAuth, onSecurity: () -> Unit) {
    val state by auth.session.state.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var reloads by remember { mutableStateOf(0) }
    val signOut = rememberSignOut(auth)
    StapelPage(Modifier.testTag(DemoTags.HOME)) {
        Row { AuthAccountMenu(auth) }
        val user = (state as? AuthState.SignedIn)?.user ?: (state as? AuthState.Guest)?.user
        StapelText(user?.let { it.email ?: it.phone ?: it.username } ?: "…", Modifier.testTag(DemoTags.USER), style = StapelTheme.type.heading)
        StapelText("reloads: $reloads")
        error?.let { StapelText(it, Modifier.testTag(DemoTags.PROFILE_ERROR), color = StapelTheme.colors.error) }
        Column {
            StapelButton("Reload profile", {
                scope.launch {
                    when (val r = auth.session.refreshUser()) {
                        is StapelResult.Ok -> { error = null; reloads++ }
                        is StapelResult.Err -> error = r.error.code.value
                    }
                }
            }, testTag = DemoTags.RELOAD)
            StapelButton("Security", onSecurity, variant = ButtonVariant.Secondary, testTag = DemoTags.SECURITY)
            StapelButton("Sign out", signOut, variant = ButtonVariant.Text, testTag = DemoTags.SIGN_OUT)
        }
    }
}
