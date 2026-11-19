package stapel.auth.ui.demo

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * `./gradlew :stapel-auth-ui-demo:run --args="--base-url http://127.0.0.1:18731"` (or
 * `-Dstapel.demo.baseUrl=…`) opens the live app against that stapel-auth backend. The stories of every
 * screen state are in the gallery: `./gradlew :showcase:desktop:run --args="--open /c/auth"`.
 */
fun main(args: Array<String>) {
    val baseUrl = args.toList().zipWithNext().firstOrNull { it.first == "--base-url" }?.second ?: System.getProperty("stapel.demo.baseUrl")
    if (baseUrl == null) {
        println("The auth stories live in the gallery: ./gradlew :showcase:desktop:run --args=\"--open /c/auth\"")
        println("The live app needs a backend: ./gradlew :stapel-auth-ui-demo:run --args=\"--base-url http://127.0.0.1:18731/\"")
        return
    }
    application {
        Window(onCloseRequest = ::exitApplication, title = "Stapel auth demo", state = rememberWindowState(width = 1100.dp, height = 860.dp)) {
            LiveDemo(baseUrl)
        }
    }
}
