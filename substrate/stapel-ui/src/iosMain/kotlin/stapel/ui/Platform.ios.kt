package stapel.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal actual val platformInput: StapelInput = StapelInput.Touch

// Human Interface Guidelines: 44 pt minimum hit target.
internal actual val platformMinTouchTarget: Dp = 44.dp

// Settings › Accessibility › Motion › Reduce Motion.
@androidx.compose.runtime.Composable
internal actual fun platformReduceMotion(): Boolean = platform.UIKit.UIAccessibilityIsReduceMotionEnabled()
