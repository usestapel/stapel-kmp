package stapel.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal actual val platformInput: StapelInput = StapelInput.Touch

// Android accessibility guidance: 48 dp minimum touch target.
internal actual val platformMinTouchTarget: Dp = 48.dp
