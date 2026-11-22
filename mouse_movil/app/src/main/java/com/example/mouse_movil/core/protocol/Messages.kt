package com.example.mouse_movil.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val ProtocolJson: Json = Json {
    classDiscriminator = "t"
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

@Serializable
sealed interface ProtocolMessage

@Serializable
@SerialName("hello")
data class HelloMessage(
    val v: Int = ProtocolConstants.PROTOCOL_VERSION,
    val deviceId: String,
    val deviceName: String,
    val platform: String = "android",
    val appVersion: String = "1.0"
) : ProtocolMessage

@Serializable
@SerialName("hello_ack")
data class HelloAckMessage(
    val v: Int,
    val serverId: String,
    val serverName: String,
    val pairingOpen: Boolean
) : ProtocolMessage

@Serializable
@SerialName("auth")
data class AuthMessage(
    val deviceId: String,
    val token: String
) : ProtocolMessage

@Serializable
@SerialName("pair_request")
data class PairRequestMessage(
    val deviceId: String,
    val deviceName: String,
    val pin: String
) : ProtocolMessage

@Serializable
@SerialName("auth_ok")
data class AuthOkMessage(
    val sessionId: String,
    val serverName: String
) : ProtocolMessage

@Serializable
@SerialName("pair_ok")
data class PairOkMessage(
    val token: String,
    val serverId: String,
    val serverName: String
) : ProtocolMessage

@Serializable
@SerialName("auth_fail")
data class AuthFailMessage(
    val reason: String
) : ProtocolMessage

@Serializable
@SerialName("pair_fail")
data class PairFailMessage(
    val reason: String,
    val attemptsLeft: Int = 0,
    val retryAfterSec: Int = 0
) : ProtocolMessage

@Serializable
@SerialName("ping")
data class PingMessage(
    val ts: Long
) : ProtocolMessage

@Serializable
@SerialName("pong")
data class PongMessage(
    val ts: Long
) : ProtocolMessage

@Serializable
@SerialName("bye")
data class ByeMessage(
    val reason: String
) : ProtocolMessage

@Serializable
@SerialName("error")
data class ErrorMessage(
    val code: String,
    val message: String
) : ProtocolMessage

@Serializable
@SerialName("click")
data class ClickMessage(
    val button: String,
    val count: Int = 1
) : ProtocolMessage

@Serializable
@SerialName("button")
data class ButtonMessage(
    val button: String,
    val action: String
) : ProtocolMessage

@Serializable
@SerialName("text")
data class TextMessage(
    val text: String
) : ProtocolMessage

@Serializable
@SerialName("key")
data class KeyMessage(
    val key: String,
    val action: String
) : ProtocolMessage

@Serializable
@SerialName("combo")
data class ComboMessage(
    val keys: List<String>
) : ProtocolMessage

@Serializable
@SerialName("media")
data class MediaMessage(
    val action: String
) : ProtocolMessage

@Serializable
@SerialName("system")
data class SystemMessage(
    val action: String,
    val confirm: Boolean = false
) : ProtocolMessage

@Serializable
@SerialName("discover")
data class DiscoverMessage(
    val v: Int = ProtocolConstants.PROTOCOL_VERSION
) : ProtocolMessage

@Serializable
@SerialName("discover_reply")
data class DiscoverReplyMessage(
    val v: Int,
    val serverId: String,
    val serverName: String,
    val host: String,
    val port: Int,
    val fp: String,
    val pairingOpen: Boolean
) : ProtocolMessage
