package shop.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import shop.ShopApp
import shop.createDesktopShopApp
import kotlin.system.exitProcess

/** The desktop app. `--smoke`: exit 0 once the navigator exists and the first destination is shown. */
fun main(args: Array<String>) {
    val app = createDesktopShopApp(System.getProperty("shop.baseUrl"))
    val smoke = "--smoke" in args
    application {
        Window(onCloseRequest = { app.close(); exitApplication() }, title = "Shop") {
            ShopApp(app)
        }
        if (smoke) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val ready = kotlinx.coroutines.withTimeoutOrNull(30_000) {
                    val navigator = app.navigator.first { it != null }!!
                    navigator.state.first { it.top != null }
                    delay(500)
                    true
                } ?: false
                println(if (ready) "shop.smoke ok: ${app.navigator.value?.state?.value?.top?.route}" else "shop.smoke FAILED: no destination within 30 s")
                app.close()
                exitProcess(if (ready) 0 else 1)
            }
        }
    }
}
