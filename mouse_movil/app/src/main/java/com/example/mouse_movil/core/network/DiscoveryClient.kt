package com.example.mouse_movil.core.network

import android.content.Context
import android.net.wifi.WifiManager
import com.example.mouse_movil.core.protocol.DiscoverMessage
import com.example.mouse_movil.core.protocol.DiscoverReplyMessage
import com.example.mouse_movil.core.protocol.ProtocolConstants
import com.example.mouse_movil.core.protocol.ProtocolJson
import com.example.mouse_movil.core.protocol.ProtocolMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

data class DiscoveredPc(
    val serverId: String,
    val serverName: String,
    val host: String,
    val port: Int,
    val fingerprint: String,
    val pairingOpen: Boolean
)

class DiscoveryClient(private val context: Context) {

    suspend fun discover(timeoutMs: Long = 1_500L): List<DiscoveredPc> = withContext(Dispatchers.IO) {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifiManager?.createMulticastLock("mse_udp_discovery")?.apply {
            setReferenceCounted(false)
            try {
                acquire()
            } catch (_: Exception) {
            }
        }

        val results = linkedMapOf<String, DiscoveredPc>()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 250
            }

            val payload = ProtocolJson.encodeToString(
                ProtocolMessage.serializer(),
                DiscoverMessage(ProtocolConstants.PROTOCOL_VERSION)
            ).toByteArray(Charsets.UTF_8)

            val targets = collectBroadcastAddresses()

            repeat(3) { attempt ->
                for (addr in targets) {
                    try {
                        val packet = DatagramPacket(payload, payload.size, addr, ProtocolConstants.DISCOVERY_PORT)
                        socket.send(packet)
                    } catch (_: Exception) {
                    }
                }
                if (attempt < 2) {
                    collectReplies(socket, results, 300L)
                }
            }

            collectReplies(socket, results, timeoutMs)
        } catch (_: Exception) {
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
            try {
                if (lock?.isHeld == true) lock.release()
            } catch (_: Exception) {
            }
        }

        results.values.toList()
    }

    private suspend fun collectReplies(
        socket: DatagramSocket,
        results: MutableMap<String, DiscoveredPc>,
        windowMs: Long
    ) {
        val deadline = System.currentTimeMillis() + windowMs
        val buf = ByteArray(2048)

        while (System.currentTimeMillis() < deadline) {
            try {
                val packet = DatagramPacket(buf, buf.size)
                socket.receive(packet)
                val jsonText = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                val msg = try {
                    ProtocolJson.decodeFromString(ProtocolMessage.serializer(), jsonText)
                } catch (_: Exception) {
                    null
                }
                if (msg is DiscoverReplyMessage) {
                    val resolvedHost = packet.address?.hostAddress?.takeIf { it.isNotBlank() } ?: msg.host
                    results[msg.serverId] = DiscoveredPc(
                        serverId = msg.serverId,
                        serverName = msg.serverName,
                        host = resolvedHost,
                        port = msg.port,
                        fingerprint = msg.fp,
                        pairingOpen = msg.pairingOpen
                    )
                }
            } catch (_: SocketTimeoutException) {
                delay(10)
            } catch (_: Exception) {
                break
            }
        }
    }

    private fun collectBroadcastAddresses(): List<InetAddress> {
        val list = mutableListOf<InetAddress>()
        try {
            list.add(InetAddress.getByName("255.255.255.255"))
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                for (ia in nif.interfaceAddresses) {
                    val bcast = ia.broadcast ?: continue
                    if (!list.contains(bcast)) {
                        list.add(bcast)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return list
    }
}
