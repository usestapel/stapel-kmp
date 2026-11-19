package stapel.auth.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import stapel.auth.ui.AuthUiBundle
import stapel.auth.ui.authErrorCatalogs
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.result.text.ErrorTextResolver
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
import stapel.ui.LocalStapelErrorText
import stapel.ui.StapelInput
import stapel.ui.StapelListItem
import stapel.ui.StapelTheme
import stapel.ui.material3.StapelMaterialTheme
import stapel.ui.skin.FoundationSkin
import stapel.ui.skin.TabsSpec
import stapel.ui.stapelLayout

/** Which skin draws the demo. */
enum class DemoSkin { Material3, Foundation }

/** The theme every demo screen is drawn in: strings (en/ru), the auth error catalogs, one of the two skins. */
@Composable
fun DemoTheme(skin: DemoSkin, locale: String, mode: ThemeMode, phoneWidth: Boolean = false, content: @Composable () -> Unit) {
    val strings = remember(locale) { StapelStrings { initialLocale = locale; register(CoreUiBundle); register(AuthUiBundle) } }
    val layout = if (phoneWidth) stapelLayout(StapelDefaultTokens, 390.dp, input = StapelInput.Touch, platformTouchFloor = 44.dp) else null
    CompositionLocalProvider(LocalStapelErrorText provides ErrorTextResolver(authErrorCatalogs())) {
        when (skin) {
            DemoSkin.Material3 -> if (layout != null) StapelMaterialTheme(mode = mode, strings = strings, layout = layout, content = content)
                else StapelMaterialTheme(mode = mode, strings = strings, content = content)
            DemoSkin.Foundation -> if (layout != null) StapelTheme(skin = FoundationSkin, mode = mode, strings = strings, layout = layout, content = content)
                else StapelTheme(skin = FoundationSkin, mode = mode, strings = strings, content = content)
        }
    }
}

/** A list of [AuthStories] and the selected one drawn at phone width, with skin, locale and mode switches. */
@Composable
fun StoriesBrowser() {
    var selected by rememberSaveable { mutableStateOf(0) }
    var skin by rememberSaveable { mutableStateOf(DemoSkin.Material3) }
    var locale by rememberSaveable { mutableStateOf("en") }
    var dark by rememberSaveable { mutableStateOf(false) }
    DemoTheme(skin, locale, if (dark) ThemeMode.Dark else ThemeMode.Light) {
        Row(Modifier.fillMaxSize().background(StapelTheme.colors.surfaceSunken)) {
            Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                StapelTheme.skin.Tabs(TabsSpec(DemoSkin.entries.map { it.name }, skin.ordinal, { skin = DemoSkin.entries[it] }), Modifier)
                StapelTheme.skin.Tabs(TabsSpec(listOf("en", "ru"), if (locale == "en") 0 else 1, { locale = if (it == 0) "en" else "ru" }), Modifier)
                StapelTheme.skin.Tabs(TabsSpec(listOf("Light", "Dark"), if (dark) 1 else 0, { dark = it == 1 }), Modifier)
                AuthStories.forEachIndexed { i, story ->
                    StapelListItem(story.title, supporting = story.id, selected = i == selected, onClick = { selected = i })
                }
            }
            Box(Modifier.width(390.dp).fillMaxHeight()) {
                DemoTheme(skin, locale, if (dark) ThemeMode.Dark else ThemeMode.Light, phoneWidth = true) { AuthStories[selected].content() }
            }
        }
    }
}
