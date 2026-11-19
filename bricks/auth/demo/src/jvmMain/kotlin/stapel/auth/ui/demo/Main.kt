package stapel.auth.ui.demo

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * `./gradlew :stapel-auth-ui-demo:run` opens the stories; `--args="--base-url http://127.0.0.1:18731"`
 * (or `-Dstapel.demo.baseUrl=…`) opens the live app against that stapel-auth backend.
 */
fun main(args: Array<String>) {
    val baseUrl = args.toList().zipWithNext().firstOrNull { it.first == "--base-url" }?.second ?: System.getProperty("stapel.demo.baseUrl")
    application {
        Window(onCloseRequest = ::exitApplication, title = "Stapel auth demo", state = rememberWindowState(width = 1100.dp, height = 860.dp)) {
            if (baseUrl == null) StoriesBrowser() else LiveDemo(baseUrl)
        }
    }
}
