package stapel.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal actual val platformInput: StapelInput = StapelInput.Touch

// Android accessibility guidance: 48 dp minimum touch target.
internal actual val platformMinTouchTarget: Dp = 48.dp

// "Remove animations" in the accessibility settings sets the animator duration scale to 0.
@androidx.compose.runtime.Composable
internal actual fun platformReduceMotion(): Boolean {
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    return android.provider.Settings.Global.getFloat(resolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
