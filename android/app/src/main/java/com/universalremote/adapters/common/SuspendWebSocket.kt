package com.universalremote.adapters.common

import com.universalremote.domain.model.RemoteError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * WebSocket de OkHttp expuesto como corrutinas: [open] suspende hasta el handshake y los mensajes
 * de texto llegan por [incoming], que se cierra (con causa si hubo fallo) al caer la conexión.
 */
class SuspendWebSocket private constructor(private val socket: WebSocket, val incoming: ReceiveChannel<String>) {

    fun send(text: String) {
        if (!socket.send(text)) throw RemoteError.NotConnected()
    }

    fun close() {
        socket.close(NORMAL_CLOSURE, null)
    }

    companion object {
        private const val NORMAL_CLOSURE = 1000

        suspend fun open(client: OkHttpClient, url: String): SuspendWebSocket {
            val channel = Channel<String>(Channel.UNLIMITED)
            val opened = CompletableDeferred<Unit>()
            val socket = client.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened.complete(Unit)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        channel.trySend(text)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, null)
                        channel.close()
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        channel.close()
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        opened.completeExceptionally(t)
                        channel.close(t)
                    }
                },
            )
            try {
                opened.await()
            } catch (t: Throwable) {
                socket.cancel()
                throw t
            }
            return SuspendWebSocket(socket, channel)
        }
    }
}
