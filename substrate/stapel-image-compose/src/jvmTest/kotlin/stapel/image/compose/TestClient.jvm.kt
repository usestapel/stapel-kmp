package stapel.image.compose

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

internal const val engineName = "OkHttp"

internal fun testHttpClient(): HttpClient = HttpClient(OkHttp)
