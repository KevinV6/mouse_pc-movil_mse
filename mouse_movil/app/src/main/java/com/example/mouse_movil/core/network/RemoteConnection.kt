package com.example.mouse_movil.core.network

import com.example.mouse_movil.core.protocol.ByeMessage
import com.example.mouse_movil.core.protocol.FrameCodec
import com.example.mouse_movil.core.protocol.FrameType
import com.example.mouse_movil.core.protocol.ProtocolMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager

class RemoteConnection(
    private val host: String,
    private val port: Int,
    expectedFingerprint: String?,
    private val scope: CoroutineScope
) : Closeable {

    val trustManager = PinnedTrustManager(expectedFingerprint)

    private var sslSocket: SSLSocket? = null
    private var readerJob: Job? = null
    private var writerJob: Job? = null
    private val writeChannel = Channel<ByteArray>(capacity = 256)
    private val closed = AtomicBoolean(false)

    private val _incomingMessages = MutableSharedFlow<ProtocolMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<ProtocolMessage> = _incomingMessages.asSharedFlow()

    private val _disconnected = MutableSharedFlow<Throwable?>(extraBufferCapacity = 1)
    val disconnected: SharedFlow<Throwable?> = _disconnected.asSharedFlow()

    val isConnected: Boolean
        get() = sslSocket?.isConnected == true && !closed.get()

    suspend fun connect(connectTimeoutMs: Int = 4_000) = withContext(Dispatchers.IO) {
        val rawSocket = Socket()
        rawSocket.tcpNoDelay = true
        rawSocket.connect(InetSocketAddress(host, port), connectTimeoutMs)

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())

        val upgraded = sslContext.socketFactory.createSocket(
            rawSocket,
            host,
            port,
            true
        ) as SSLSocket

        upgraded.tcpNoDelay = true
        upgraded.enabledProtocols = upgraded.supportedProtocols.filter {
            it == "TLSv1.3" || it == "TLSv1.2"
        }.toTypedArray()

        upgraded.soTimeout = 20_000
        upgraded.startHandshake()
        sslSocket = upgraded

        startIoLoops(upgraded)
    }

    private fun startIoLoops(socket: SSLSocket) {
        val input = socket.inputStream
        val output = socket.outputStream

        writerJob = scope.launch(Dispatchers.IO) {
            try {
                for (frame in writeChannel) {
                    FrameCodec.writeFrame(output, frame)
                }
            } catch (t: Throwable) {
                notifyClosed(t)
            }
        }

        readerJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive && !closed.get()) {
                    val raw = FrameCodec.readFrame(input) ?: break
                    if (raw.knownType == FrameType.JSON) {
                        val msg = try {
                            FrameCodec.decodeJson(raw.payload)
                        } catch (_: Exception) {
                            null
                        }
                        if (msg != null) {
                            _incomingMessages.tryEmit(msg)
                        }
                    }
                }
                notifyClosed(null)
            } catch (t: Throwable) {
                notifyClosed(t)
            }
        }
    }

    fun send(message: ProtocolMessage) {
        if (closed.get()) return
        try {
            writeChannel.trySend(FrameCodec.encodeJson(message))
        } catch (_: Exception) {
        }
    }

    fun sendMouseMove(dx: Short, dy: Short) {
        if (closed.get() || (dx == 0.toShort() && dy == 0.toShort())) return
        writeChannel.trySend(FrameCodec.encodeMouseMove(dx, dy))
    }

    fun sendScroll(dx: Short, dy: Short) {
        if (closed.get() || (dx == 0.toShort() && dy == 0.toShort())) return
        writeChannel.trySend(FrameCodec.encodeScroll(dx, dy))
    }

    fun sendZoom(steps: Short) {
        if (closed.get() || steps == 0.toShort()) return
        writeChannel.trySend(FrameCodec.encodeZoom(steps))
    }

    suspend fun closeGracefully(reason: String = "client_exit") = withContext(Dispatchers.IO) {
        if (closed.get()) return@withContext
        try {
            sslSocket?.outputStream?.let { out ->
                FrameCodec.writeFrame(out, FrameCodec.encodeJson(ByeMessage(reason)))
            }
        } catch (_: Exception) {
        }
        close()
    }

    private fun notifyClosed(cause: Throwable?) {
        if (closed.compareAndSet(false, true)) {
            writeChannel.close()
            try {
                sslSocket?.close()
            } catch (_: Exception) {
            }
            _disconnected.tryEmit(cause)
        }
    }

    override fun close() {
        notifyClosed(null)
        readerJob?.cancel()
        writerJob?.cancel()
    }
}
