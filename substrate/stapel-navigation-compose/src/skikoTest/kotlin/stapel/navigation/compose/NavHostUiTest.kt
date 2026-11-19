package stapel.navigation.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import kotlinx.coroutines.isActive
import stapel.mvi.compose.LocalPresenterFactory
import stapel.navigation.CapabilityKey
import stapel.navigation.DeepLinkConfig
import stapel.navigation.ExternalUris
import stapel.navigation.NavAuthSource
import stapel.navigation.NavAuthState
import stapel.navigation.StapelNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class TestStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

private class TestLifecycle : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
    override val lifecycle: Lifecycle get() = registry
}

/**
 * Hosts the test app as an activity would: a retained ViewModelStore, a lifecycle that is
 * destroyed on configuration change, a saveable registry that is the only thing a new process gets,
 * and the platform back channel (navigationevent) — where Android back, the iOS edge-swipe
 * recognizer and desktop Esc all arrive.
 */
@OptIn(ExperimentalTestApi::class)
private class Harness(private val test: ComposeUiTest, private val auth: NavAuthSource = NavAuthSource { memberAuth }) {
    val factory = RecordingFactory()
    var storeOwner = TestStoreOwner()
    private var lifecycle by mutableStateOf(test.runOnIdle { TestLifecycle() })
    private var registry by mutableStateOf(SaveableStateRegistry(null) { true })
    private var alive by mutableStateOf(true)
    var capabilities: Set<CapabilityKey> = emptySet()
    lateinit var navigator: StapelNavigator
    private var back: DirectNavigationEventInput? = null

    fun start(deepLinks: DeepLinkConfig? = null) {
        test.setContent {
            if (alive) {
                CompositionLocalProvider(
                    LocalSaveableStateRegistry provides registry,
                    LocalViewModelStoreOwner provides storeOwner,
                    LocalLifecycleOwner provides lifecycle,
                    LocalPresenterFactory provides factory,
                ) { App(deepLinks) }
            }
        }
        test.waitForIdle()
    }

    @Composable
    private fun App(deepLinks: DeepLinkConfig?) {
        val n = rememberStapelNavigator(listOf(testContribution), testPolicy, auth, deepLinks)
        navigator = n
        val dispatcher = LocalNavigationEventDispatcherOwner.current!!.navigationEventDispatcher
        if (back == null) back = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        StapelNavHost(
            navigator = n,
            contributions = listOf(testContribution),
            capabilities = capabilities,
            unavailable = { d, missing -> BasicText("unavailable ${d.id} ${missing.joinToString()}", Modifier.testTag("unavailable")) },
            waiting = { BasicText("waiting", Modifier.testTag("waiting")) },
        )
    }

    /** The platform back action, through the same dispatcher the platform inputs use. */
    fun systemBack() {
        test.runOnIdle {
            back!!.backStarted(NavigationEvent())
            back!!.backCompleted()
        }
        test.waitForIdle()
    }

    /** Configuration change: state saved, lifecycle destroyed, composition gone, ViewModelStore retained. */
    fun configurationChange() = recreate(keepStores = true)

    /** Process death: only the saveable registry's content survives. */
    fun processDeath() = recreate(keepStores = false)

    private fun recreate(keepStores: Boolean) {
        val saved = test.runOnIdle { registry.performSave() }
        test.runOnIdle { lifecycle.registry.currentState = Lifecycle.State.DESTROYED }
        alive = false
        test.waitForIdle()
        if (!keepStores) storeOwner = TestStoreOwner()
        registry = SaveableStateRegistry(saved) { true }
        lifecycle = test.runOnIdle { TestLifecycle() }
        back = null
        alive = true
        test.waitForIdle()
    }

    fun shows(tag: String) = test.onNodeWithTag(tag).assertIsDisplayed()
}

@OptIn(ExperimentalTestApi::class)
class NavHostUiTest {
    @Test
    fun walksThreeDestinationsWithATabSwitchAndSystemBack() = runComposeUiTest {
        val h = Harness(this)
        h.start()
        h.shows("screen-home")
        onNodeWithTag("open-7").performClick()
        waitForIdle()
        h.shows("screen-detail")
        onNodeWithTag("detail-count").performClick()
        onNodeWithTag("detail-count").assertTextEquals("detail:1")
        runOnIdle { h.navigator.selectTab(PROFILE) }
        waitForIdle()
        h.shows("screen-profile")
        h.shows("embed-action")
        runOnIdle { h.navigator.selectTab(HOME) }
        waitForIdle()
        h.shows("screen-detail")
        onNodeWithTag("detail-count").assertTextEquals("detail:1") // saveable state survived the tab switch
        h.systemBack()
        h.shows("screen-home")
        assertEquals(listOf<stapel.navigation.Route>(HomeRoute), h.navigator.state.value.visible.map { it.route })
        h.systemBack() // at the root: nothing to pop, the platform would close the app
        h.shows("screen-home")
    }

    @Test
    fun aPresenterSurvivesConfigurationChangeAndTabSwitchesAndIsClearedWhenItsEntryIsPopped() = runComposeUiTest {
        val h = Harness(this)
        h.start()
        runOnIdle { h.navigator.navigate(DetailRoute("9")) }
        waitForIdle()
        val p = h.factory.created.single()
        h.configurationChange()
        h.shows("screen-detail")
        assertSame(p, h.factory.created.single(), "retained across the configuration change")
        runOnIdle { h.navigator.selectTab(PROFILE) }
        waitForIdle()
        runOnIdle { h.navigator.selectTab(HOME) }
        waitForIdle()
        assertEquals(1, h.factory.created.size, "retained while its entry sits in another tab")
        assertTrue(p.context.scope.isActive)
        h.systemBack()
        waitUntil { !p.context.scope.isActive }
        assertFalse(p.context.scope.isActive, "cleared once the entry left every stack")
    }

    @Test
    fun aResultReachesTheDestinationBelowOnce() = runComposeUiTest {
        val h = Harness(this)
        h.start()
        runOnIdle { h.navigator.navigate(DetailRoute("1")) }
        waitForIdle()
        runOnIdle { h.navigator.navigate(PickerRoute) }
        waitForIdle()
        onNodeWithTag("pick-blue").performClick()
        waitForIdle()
        onNodeWithTag("picked").assertTextEquals("picked blue")
    }

    @Test
    fun dialogAndSheetOverlaysAppearAndPopWithBack() = runComposeUiTest {
        val h = Harness(this)
        h.start()
        runOnIdle { h.navigator.navigate(ConfirmRoute) }
        waitForIdle()
        h.shows("dialog")
        h.shows("screen-home") // the screen underneath stays composed
        runOnIdle { h.navigator.back() }
        waitForIdle()
        onNodeWithTag("dialog").assertDoesNotExistCompat()
        runOnIdle { h.navigator.navigate(FilterRoute) }
        waitForIdle()
        h.shows("sheet")
        h.shows("screen-home")
        h.systemBack()
        assertEquals(listOf<stapel.navigation.Route>(HomeRoute), h.navigator.state.value.visible.map { it.route }, "back popped the sheet")
        onNodeWithTag("sheet").assertDoesNotExistCompat()
    }

    @Test
    fun theStackAndEntryStateSurviveProcessDeath() = runComposeUiTest {
        val h = Harness(this)
        h.start()
        runOnIdle { h.navigator.navigate(DetailRoute("5")) }
        waitForIdle()
        onNodeWithTag("detail-count").performClick()
        onNodeWithTag("detail-count").performClick()
        h.processDeath()
        h.shows("screen-detail")
        onNodeWithTag("screen-detail").assertTextEquals("detail 5")
        onNodeWithTag("detail-count").assertTextEquals("detail:2")
        assertEquals(2, h.factory.created.size, "a new process builds a new presenter")
        h.systemBack()
        h.shows("screen-home")
    }

    @Test
    fun aMissingCapabilityIsNamedNotBlank() = runComposeUiTest {
        val h = Harness(this)
        h.start()
        runOnIdle { h.navigator.navigate(CameraRoute) }
        waitForIdle()
        onNodeWithTag("unavailable").assertTextEquals("unavailable t.camera camera")
    }

    @Test
    fun theWaitScreenShowsUntilAuthResolves() = runComposeUiTest {
        val auth = MutableStateFlow(NavAuthState.Unresolved)
        val h = Harness(this, NavAuthSource { auth })
        h.start()
        h.shows("waiting")
        auth.value = NavAuthState.Member
        waitForIdle()
        h.shows("screen-home")
    }

    @Test
    fun aMultiStepScreenConsumesBackForItsOwnSteps() = runComposeUiTest {
        val h = Harness(this)
        h.start()
        runOnIdle { h.navigator.navigate(WizardRoute) }
        waitForIdle()
        onNodeWithTag("wizard").performClick()
        onNodeWithTag("wizard").assertTextEquals("step 2")
        h.systemBack()
        onNodeWithTag("wizard").assertTextEquals("step 1")
        h.systemBack()
        h.shows("screen-home")
    }

    @Test
    fun aContainerRootShowsItsChildrenAsASection() = runComposeUiTest {
        val h = Harness(this)
        h.start()
        runOnIdle { h.navigator.selectTab(stapel.navigation.DestinationId("account.root")) }
        waitForIdle()
        onNodeWithText("t.privacy.label").performClick()
        waitForIdle()
        h.shows("screen-privacy")
    }

    @Test
    fun anExternalUriOpensItsStackInsideTheTab() = runComposeUiTest {
        val h = Harness(this)
        h.start(DeepLinkConfig(schemes = setOf("demo"), hosts = setOf("demo.example")))
        runOnIdle { h.navigator.selectTab(PROFILE) }
        waitForIdle()
        ExternalUris.offer("https://demo.example/app/home/d/42")
        waitUntil { h.navigator.state.value.top?.route == DetailRoute("42") }
        h.shows("screen-detail")
        assertEquals(HOME, h.navigator.state.value.selectedTab)
        h.systemBack()
        h.shows("screen-home")
    }
}

@OptIn(ExperimentalTestApi::class)
private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() = assertDoesNotExist()
