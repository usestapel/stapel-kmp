package stapel.showcase.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import stapel.showcase.gallery.Gallery

/** Opens the Stapel UI gallery in a desktop window. */
fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Stapel UI gallery", state = rememberWindowState(width = 1400.dp, height = 900.dp)) {
        Gallery()
    }
}
