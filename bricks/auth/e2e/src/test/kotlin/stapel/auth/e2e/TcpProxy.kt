package stapel.auth.e2e

import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import kotlin.concurrent.thread

/**
 * A loopback TCP forwarder in front of the stand. While [online] is false it refuses connections
 * (closes them at once): the client sees a server that is gone, the stand keeps running for the
 * other scenarios.
 */
class TcpProxy(target: String) : AutoCloseable {
    private val targetUri = URI(target)
    private val server = ServerSocket(0, 50, java.net.InetAddress.getLoopbackAddress())
    private val open = mutableListOf<Socket>()

    @Volatile
    var online: Boolean = true
        set(value) {
            field = value
            if (!value) synchronized(open) { open.forEach { runCatching { it.close() } }; open.clear() }
        }

    val url: String = "http://127.0.0.1:${server.localPort}/"

    init {
        thread(isDaemon = true, name = "e2e-proxy") {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                if (!online) {
                    client.close()
                    continue
                }
                val upstream = Socket()
                runCatching { upstream.connect(InetSocketAddress(targetUri.host, targetUri.port)) }.onFailure {
                    client.close()
                    return@thread
                }
                synchronized(open) { open += client; open += upstream }
                pipe(client, upstream)
                pipe(upstream, client)
            }
        }
    }

    private fun pipe(from: Socket, to: Socket) = thread(isDaemon = true) {
        runCatching { from.getInputStream().copyTo(to.getOutputStream()) }
        runCatching { to.shutdownOutput() }
    }

    override fun close() {
        online = false
        server.close()
    }
}
