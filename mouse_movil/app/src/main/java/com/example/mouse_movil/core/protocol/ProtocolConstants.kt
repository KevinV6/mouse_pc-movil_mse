package com.example.mouse_movil.core.protocol

object ProtocolConstants {
    const val PROTOCOL_VERSION: Int = 1
    const val TCP_PORT: Int = 47800
    const val DISCOVERY_PORT: Int = 47801
    const val MAX_FRAME_PAYLOAD: Int = 16384

    const val HANDSHAKE_TIMEOUT_MS: Long = 10_000L
    const val PING_INTERVAL_MS: Long = 5_000L
    const val IDLE_TIMEOUT_MS: Long = 15_000L
    const val MOVE_FLUSH_INTERVAL_MS: Long = 8L
    const val PIN_LIFETIME_MS: Long = 5 * 60 * 1000L

    const val PIN_MAX_ATTEMPTS: Int = 5
    const val URI_SCHEME: String = "mse://pair"
}
