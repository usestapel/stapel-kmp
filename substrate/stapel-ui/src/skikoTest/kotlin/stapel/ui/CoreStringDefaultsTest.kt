package stapel.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
import stapel.ui.skin.FoundationSkin
import stapel.ui.skin.TextFieldKind
import kotlin.test.Test

/** The wrappers default their accessible labels to the `stapel.ui.*` floor, in the theme's locale. */
@OptIn(ExperimentalTestApi::class)
class CoreStringDefaultsTest {
    private fun androidx.compose.ui.test.ComposeUiTest.themed(locale: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val strings = StapelStrings { initialLocale = locale; register(CoreUiBundle) }
        setContent {
            StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings, layout = stapelLayout(StapelDefaultTokens, 390.dp, StapelInput.Touch, 44.dp), reduceMotion = true) {
                Box(Modifier.size(390.dp, 800.dp)) { content() }
            }
        }
    }

    @Test
    fun pickerDefaultsCloseClearAndNoMatches() = runComposeUiTest {
        themed("en") {
            PickerSheet("Country", listOf("Austria", "Belgium"), { it }, emptySet(), {}, {}, searchLabel = "Find", testTag = "p")
        }
        onNodeWithContentDescription("Close").assertExists()
        onNodeWithTag("p$PICKER_SEARCH_TAG").performTextInput("zzz")
        onNodeWithText("No matches").assertExists()
        onNodeWithContentDescription("Clear").assertExists()
    }

    @Test
    fun multiSelectDefaultsToDoneInRussian() = runComposeUiTest {
        themed("ru") {
            StapelMultiSelect("Страны", listOf("A", "B"), setOf("A"), {}, { it }, testTag = "m")
        }
        onNodeWithTag("m").performClick()
        onNodeWithText("Готово").assertExists()
        onNodeWithContentDescription("Закрыть").assertExists()
    }

    @Test
    fun searchFieldAndChipAndConfirmDialogDefaults() = runComposeUiTest {
        themed("en") {
            StapelTextField(TextFieldState("shoes"), "Search", kind = TextFieldKind.Search)
            StapelChip("Ada", onRemove = {})
            ConfirmDialog("Sign out?", onConfirm = {}, onDismiss = {})
        }
        onNodeWithContentDescription("Clear").assertExists()
        onNodeWithContentDescription("Remove").assertExists()
        onNodeWithText("Confirm").assertExists()
        onNodeWithText("Cancel").assertExists()
    }

    @Test
    fun pullToRefreshDefaultsItsName() = runComposeUiTest {
        themed("es") {
            StapelPullToRefresh(refreshing = false, onRefresh = {}) { androidx.compose.foundation.text.BasicText("x") }
        }
        val named = androidx.compose.ui.test.SemanticsMatcher("refresh action named Actualizar") { node ->
            node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsActions.CustomActions) { null }.orEmpty().any { it.label == "Actualizar" }
        }
        onNode(named).assertExists()
    }
}
