package stapel.mvi.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import kotlinx.coroutines.isActive
import kotlinx.serialization.builtins.serializer
import stapel.mvi.Presenter
import stapel.mvi.PresenterContext
import stapel.mvi.StateSaver
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

private data class NoteState(val text: String = "")

private sealed interface NoteIntent {
    data class Type(val text: String) : NoteIntent
    data class Shout(val message: String) : NoteIntent
}

private class NotePresenter(val context: PresenterContext, val id: String) :
    Presenter<NoteState, NoteIntent, String>(NoteState(), context) {
    init {
        saved("text", String.serializer()) { text }?.let { restored -> reduce { copy(text = restored) } }
    }

    override suspend fun handle(intent: NoteIntent) {
        when (intent) {
            is NoteIntent.Type -> reduce { copy(text = intent.text) }
            is NoteIntent.Shout -> emit(intent.message)
        }
    }
}

private class Factory : PresenterFactory {
    val created = mutableListOf<NotePresenter>()

    override fun <P : Presenter<*, *, *>> create(type: KClass<P>, context: PresenterContext, args: List<Any?>): P {
        check(type == NotePresenter::class)
        @Suppress("UNCHECKED_CAST")
        return NotePresenter(context, args.single() as String).also { created += it } as P
    }
}

private class TestOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore = ViewModelStore()
}

@Composable
private fun Note(id: String, onPresenter: (NotePresenter) -> Unit = {}, onEffect: (String) -> Unit = {}) {
    val p = rememberPresenter<NotePresenter>(id)
    onPresenter(p)
    val state by p.collectState()
    p.CollectEffects { onEffect(it) }
    BasicText(state.text, Modifier.testTag("note-$id"))
}

/**
 * Emulates process death: the saveable registry's saved map is the only thing carried over; every
 * `remember`ed object (ViewModel stores included) is recreated. CMP 1.12.2's StateRestorationTester
 * cannot do this on skiko targets (`emulateSaveAndRestore` throws NotImplementedError there).
 */
@OptIn(ExperimentalTestApi::class)
internal class ProcessDeath(private val test: ComposeUiTest) {
    private var registry by mutableStateOf(SaveableStateRegistry(null) { true })
    private var alive by mutableStateOf(true)

    fun setContent(content: @Composable () -> Unit) {
        test.setContent {
            // Same composition position before and after, so saveable keys match as in a real restore.
            if (alive) CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { content() }
        }
    }

    fun saveAndRestore() {
        val saved = registry.performSave()
        alive = false
        test.waitForIdle()
        registry = SaveableStateRegistry(saved) { true }
        alive = true
        test.waitForIdle()
    }
}

@OptIn(ExperimentalTestApi::class)
class PresenterRetentionUiTest {
    @Test
    fun aPresenterSurvivesItsScreenBeingRecreatedAndIsClearedWithItsOwner() = runComposeUiTest {
        val factory = Factory()
        val owner = TestOwner()
        var shown by mutableStateOf(true)
        val seen = mutableListOf<NotePresenter>()
        setContent {
            CompositionLocalProvider(LocalPresenterFactory provides factory, LocalViewModelStoreOwner provides owner) {
                if (shown) Note("a", onPresenter = { seen += it })
            }
        }
        val first = seen.last()
        first.dispatch(NoteIntent.Type("draft"))
        waitUntil { first.state.value.text == "draft" }
        waitForIdle()
        onNodeWithTag("note-a").assertTextEquals("draft")
        // A configuration change disposes the composition but keeps the ViewModelStore.
        shown = false
        waitForIdle()
        shown = true
        waitForIdle()
        assertSame(first, seen.last(), "the same presenter instance after recreation")
        onNodeWithTag("note-a").assertTextEquals("draft")
        assertEquals(1, factory.created.size)
        // Popping the entry clears its store: the presenter's scope is cancelled.
        assertTrue(first.context.scope.isActive)
        owner.viewModelStore.clear()
        assertFalse(first.context.scope.isActive)
    }

    @Test
    fun differentArgumentsGiveDifferentPresenters() = runComposeUiTest {
        val factory = Factory()
        val owner = TestOwner()
        setContent {
            CompositionLocalProvider(LocalPresenterFactory provides factory, LocalViewModelStoreOwner provides owner) {
                Note("a")
                Note("b")
            }
        }
        waitForIdle()
        assertEquals(listOf("a", "b"), factory.created.map { it.id })
        assertNotSame(factory.created[0], factory.created[1])
    }

    @Test
    fun anEffectEmittedWhileTheScreenIsGoneIsHandledOnceWhenItReturns() = runComposeUiTest {
        val factory = Factory()
        val owner = TestOwner()
        var shown by mutableStateOf(true)
        val handled = mutableListOf<String>()
        setContent {
            CompositionLocalProvider(LocalPresenterFactory provides factory, LocalViewModelStoreOwner provides owner) {
                if (shown) Note("a", onEffect = { handled += it })
            }
        }
        waitForIdle()
        val p = factory.created.single()
        shown = false
        waitForIdle()
        p.dispatch(NoteIntent.Shout("saved"))
        waitForIdle()
        assertTrue(handled.isEmpty())
        shown = true
        waitForIdle()
        shown = false
        waitForIdle()
        shown = true
        waitForIdle()
        assertEquals(listOf("saved"), handled, "delivered once, not lost and not replayed")
    }

    @Test
    fun withoutASavedStateRegistryTheSaverFallsBackToNone() = runComposeUiTest {
        val factory = Factory()
        setContent {
            CompositionLocalProvider(LocalPresenterFactory provides factory, LocalViewModelStoreOwner provides TestOwner()) {
                Note("a")
            }
        }
        waitForIdle()
        assertSame(StateSaver.None, factory.created.single().context.saver)
    }

    /**
     * Measures the multiplatform SavedStateHandle path the navigation engine uses: an entry owner from
     * a ViewModelStoreProvider, inside a SaveableStateHolder provider. Everything `remember`ed is lost
     * (as in process death); only saveable state comes back.
     */
    @Test
    fun savedValuesSurviveProcessDeathThroughSavedStateHandle() = runComposeUiTest {
        val factory = Factory()
        val process = ProcessDeath(this)
        process.setContent {
            val processOwner = remember { TestOwner() } // a new process: a new, empty ViewModelStore
            val holder = rememberSaveableStateHolder()
            CompositionLocalProvider(LocalPresenterFactory provides factory, LocalViewModelStoreOwner provides processOwner) {
                holder.SaveableStateProvider("entry-1") {
                    val provider = rememberViewModelStoreProvider(parent = processOwner)
                    val entryOwner = rememberViewModelStoreOwner("entry-1", provider)
                    CompositionLocalProvider(LocalViewModelStoreOwner provides entryOwner) { Note("a") }
                }
            }
        }
        waitForIdle()
        val before = factory.created.single()
        assertTrue(before.context.saver !== StateSaver.None, "the entry owner supplies a SavedStateHandle")
        before.dispatch(NoteIntent.Type("kept across process death"))
        waitUntil { before.state.value.text == "kept across process death" }
        process.saveAndRestore()
        waitForIdle()
        assertEquals(2, factory.created.size, "a new process creates a new presenter")
        val after = factory.created[1]
        assertTrue(after.context.saver !== StateSaver.None)
        assertEquals("kept across process death", after.state.value.text, "restored by the new presenter")
        onNodeWithTag("note-a").assertTextEquals("kept across process death")
    }
}

