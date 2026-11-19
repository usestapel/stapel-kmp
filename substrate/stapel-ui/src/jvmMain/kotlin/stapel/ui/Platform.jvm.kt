package stapel.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal actual val platformInput: StapelInput = StapelInput.Pointer
internal actual val platformMinTouchTarget: Dp = 0.dp

// Desktop has no portable reduce-motion setting; an app passes `reduceMotion` to StapelTheme itself.
@androidx.compose.runtime.Composable
internal actual fun platformReduceMotion(): Boolean = false
