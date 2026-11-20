package stapel.network.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.onUpload
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.ByteArrayContent
import kotlinx.coroutines.CancellationException
import stapel.network.HttpTransport
import stapel.network.WireBody
import stapel.network.WireOutcome
import stapel.network.WirePart
import stapel.network.WireRequest

/**
 * The Ktor-backed [HttpTransport]. Ktor is used for the exchange and timeouts only: no redirects,
 * no retry plugin, no auth plugin, no cookies, no content negotiation, `expectSuccess = false`.
 */
internal class KtorTransport(
    engine: HttpClientEngine,
    private val classify: (Throwable) -> WireOutcome.Failure,
    configure: HttpClientConfig<*>.() -> Unit = {},
) : HttpTransport {
    private val client = HttpClient(engine) {
        expectSuccess = false
        followRedirects = false
        install(HttpTimeout)
        configure()
    }

    override suspend fun send(request: WireRequest): WireOutcome {
        return try {
            val response = client.request(request.url) {
                method = HttpMethod.parse(request.method)
                timeout {
                    connectTimeoutMillis = request.timeouts.connect.inWholeMilliseconds
                    socketTimeoutMillis = request.timeouts.socket.inWholeMilliseconds
                    requestTimeoutMillis = request.timeouts.request.inWholeMilliseconds
                }
                headers {
                    for ((name, value) in request.headers) {
                        if (!name.equals(HttpHeaders.ContentType, ignoreCase = true)) append(name, value)
                    }
                }
                request.onUpload?.let { listener -> onUpload { sent, total -> listener(sent, total) } }
                when (val body = request.body) {
                    WireBody.Empty -> Unit
                    is WireBody.Bytes -> setBody(ByteArrayContent(body.bytes, ContentType.parse(body.contentType)))
                    is WireBody.Multipart -> setBody(MultiPartFormDataContent(multipartOf(body.parts)))
                }
            }
            val bytes = response.bodyAsBytes()
            WireOutcome.Response(
                status = response.status.value,
                headers = response.headers.withoutCookies(),
                body = bytes,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            classify(e)
        }
    }

    override fun close() {
        client.close()
    }
}

private fun Headers.withoutCookies(): List<Pair<String, String>> =
    entries().flatMap { (name, values) ->
        if (name.equals(HttpHeaders.SetCookie, ignoreCase = true)) emptyList() else values.map { name to it }
    }

private fun multipartOf(parts: List<WirePart>) = formData {
    for (part in parts) {
        when (part) {
            is WirePart.Field -> append(part.name, part.value)
            is WirePart.File -> append(
                part.name,
                part.bytes,
                Headers.build {
                    append(HttpHeaders.ContentDisposition, "filename=\"${part.fileName.replace("\"", "")}\"")
                    append(HttpHeaders.ContentType, part.contentType ?: "application/octet-stream")
                },
            )
        }
    }
}
