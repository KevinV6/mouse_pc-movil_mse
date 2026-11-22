package com.example.mouse_movil.core.network

import com.example.mouse_movil.core.protocol.ProtocolConstants
import java.net.URI
import java.net.URLDecoder

data class PairingUriData(
    val version: Int,
    val host: String,
    val port: Int,
    val serverName: String,
    val fingerprint: String,
    val pin: String
)

object PairingUri {
    fun parse(rawUri: String): PairingUriData? {
        val trimmed = rawUri.trim()
        if (!trimmed.startsWith("mse://pair", ignoreCase = true)) {
            return null
        }

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return null
        }

        val query = uri.rawQuery ?: return null
        val params = mutableMapOf<String, String>()
        for (part in query.split("&")) {
            val idx = part.indexOf('=')
            if (idx <= 0) continue
            val k = URLDecoder.decode(part.substring(0, idx), "UTF-8").lowercase()
            val v = URLDecoder.decode(part.substring(idx + 1), "UTF-8")
            params[k] = v
        }

        val version = params["v"]?.toIntOrNull() ?: ProtocolConstants.PROTOCOL_VERSION
        val host = params["h"]?.trim().orEmpty()
        val port = params["p"]?.toIntOrNull() ?: ProtocolConstants.TCP_PORT
        val name = params["n"]?.trim()?.ifEmpty { "PC Windows" } ?: "PC Windows"
        val fp = params["fp"]?.trim().orEmpty()
        val pin = params["pin"]?.trim().orEmpty()

        if (host.isEmpty() || fp.isEmpty() || pin.length != 6 || !pin.all { it.isDigit() } || port !in 1..65535) {
            return null
        }

        return PairingUriData(
            version = version,
            host = host,
            port = port,
            serverName = name,
            fingerprint = fp,
            pin = pin
        )
    }
}
