package stapel.auth.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import stapel.i18n.CoreUiBundle
import stapel.i18n.StapelStrings
import stapel.result.ErrorCode
import stapel.result.ErrorEnvelope
import stapel.result.ErrorParams
import stapel.result.ServerRefusal
import stapel.result.text.ErrorTextResolver
import stapel.tokens.StapelDefaultTokens
import stapel.tokens.ThemeMode
import stapel.ui.LocalStapelErrorText
import stapel.ui.StapelInput
import stapel.ui.StapelTheme
import stapel.ui.material3.Material3Skin
import stapel.ui.material3.StapelMaterialTheme
import stapel.ui.skin.FoundationSkin
import stapel.ui.skin.StapelSkin
import stapel.ui.stapelLayout

/** The two shipped skins every screen is rendered with. */
enum class TestSkin { Foundation, Material3 }

const val ROOT_TAG = "test.root"

/** A host whose skin, locale and content can change between assertions of one test. */
class Harness {
    var skin by mutableStateOf(TestSkin.Foundation)
    var locale by mutableStateOf("en")
    var fontScale by mutableStateOf(1f)
    var width by mutableStateOf(360.dp)
    var customSkin by mutableStateOf<StapelSkin?>(null)
    var content by mutableStateOf<@Composable () -> Unit>({})

    @Composable
    fun Render() {
        key(skin, locale, customSkin) {
            val strings = remember { StapelStrings { initialLocale = locale; register(CoreUiBundle); register(AuthUiBundle) } }
            val layout = stapelLayout(StapelDefaultTokens, width, input = StapelInput.Touch, platformTouchFloor = 48.dp)
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalStapelErrorText provides ErrorTextResolver(authErrorCatalogs()),
                LocalDensity provides Density(base.density, fontScale),
            ) {
                val body: @Composable () -> Unit = { Box(Modifier.width(width).testTag(ROOT_TAG)) { content() } }
                val custom = customSkin
                when {
                    custom != null -> StapelTheme(skin = custom, mode = ThemeMode.Light, strings = strings, layout = layout, content = body)
                    skin == TestSkin.Material3 ->
                        StapelMaterialTheme(mode = ThemeMode.Light, skin = Material3Skin, strings = strings, layout = layout, content = body)
                    else -> StapelTheme(skin = FoundationSkin, mode = ThemeMode.Light, strings = strings, layout = layout, content = body)
                }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.harness(content: @Composable () -> Unit = {}): Harness {
    val h = Harness()
    h.content = content
    setContent { h.Render() }
    return h
}

/** Runs [block] once per shipped skin. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.eachSkin(h: Harness, block: (TestSkin) -> Unit) {
    for (s in TestSkin.entries) {
        h.skin = s
        waitForIdle()
        block(s)
    }
}

/** A server refusal with [code], [status] and [params] (what the network layer hands the flows). */
fun refusal(code: String, status: Int, vararg params: Pair<String, String>): ServerRefusal =
    ServerRefusal(ErrorEnvelope(ErrorCode(code), status, ErrorParams(params.toMap())))

/** Nodes carrying an error semantic. */
val hasErrorSemantics: SemanticsMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)

/** Records events. */
class Events<E> {
    val all = mutableListOf<E>()
    val sink: (E) -> Unit = { all += it }
    fun clear() = all.clear()
}

val PhoneWidth: Dp = 360.dp

/** The editable node tagged [tag] whose error semantic is [text]. */
fun fieldError(tag: String, text: String): SemanticsMatcher =
    androidx.compose.ui.test.hasTestTag(tag) and SemanticsMatcher.expectValue(SemanticsProperties.Error, text)
