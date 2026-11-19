package stapel.showcase.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import stapel.showcase.gallery.Gallery
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Opens the Stapel showcase gallery in a desktop window.
 *
 * `--open <link>` opens a page at start: a path (`/s/ui/button?skin=Foundation`, `/c/auth`) or a
 * full `stapel-showcase://…` link.
 */
fun main(args: Array<String>) {
    val link = args.toList().zipWithNext().firstOrNull { it.first == "--open" }?.second
    application {
        Window(onCloseRequest = ::exitApplication, title = "Stapel showcase", state = rememberWindowState(width = 1440.dp, height = 920.dp)) {
            Gallery(initialLink = link, copyText = { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(it), null) })
        }
    }
}
