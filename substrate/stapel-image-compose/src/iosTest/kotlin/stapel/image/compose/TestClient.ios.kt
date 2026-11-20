package stapel.image.compose

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

internal const val engineName = "Darwin"

internal fun testHttpClient(): HttpClient = HttpClient(Darwin)
