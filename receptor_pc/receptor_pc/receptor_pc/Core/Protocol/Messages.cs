using System.Text.Json.Serialization;

namespace receptor_pc.Core.Protocol;

[JsonPolymorphic(TypeDiscriminatorPropertyName = "t")]
[JsonDerivedType(typeof(HelloMessage), "hello")]
[JsonDerivedType(typeof(HelloAckMessage), "hello_ack")]
[JsonDerivedType(typeof(AuthMessage), "auth")]
[JsonDerivedType(typeof(PairRequestMessage), "pair_request")]
[JsonDerivedType(typeof(AuthOkMessage), "auth_ok")]
[JsonDerivedType(typeof(PairOkMessage), "pair_ok")]
[JsonDerivedType(typeof(AuthFailMessage), "auth_fail")]
[JsonDerivedType(typeof(PairFailMessage), "pair_fail")]
[JsonDerivedType(typeof(PingMessage), "ping")]
[JsonDerivedType(typeof(PongMessage), "pong")]
[JsonDerivedType(typeof(ByeMessage), "bye")]
[JsonDerivedType(typeof(ErrorMessage), "error")]
[JsonDerivedType(typeof(ClickMessage), "click")]
[JsonDerivedType(typeof(ButtonMessage), "button")]
[JsonDerivedType(typeof(TextMessage), "text")]
[JsonDerivedType(typeof(KeyMessage), "key")]
[JsonDerivedType(typeof(ComboMessage), "combo")]
[JsonDerivedType(typeof(MediaMessage), "media")]
[JsonDerivedType(typeof(SystemMessage), "system")]
[JsonDerivedType(typeof(DiscoverMessage), "discover")]
[JsonDerivedType(typeof(DiscoverReplyMessage), "discover_reply")]
public abstract record ProtocolMessage;

public sealed record HelloMessage(
    [property: JsonPropertyName("v")] int V,
    [property: JsonPropertyName("deviceId")] string DeviceId,
    [property: JsonPropertyName("deviceName")] string DeviceName,
    [property: JsonPropertyName("platform")] string Platform,
    [property: JsonPropertyName("appVersion")] string AppVersion
) : ProtocolMessage;

public sealed record HelloAckMessage(
    [property: JsonPropertyName("v")] int V,
    [property: JsonPropertyName("serverId")] string ServerId,
    [property: JsonPropertyName("serverName")] string ServerName,
    [property: JsonPropertyName("pairingOpen")] bool PairingOpen
) : ProtocolMessage;

public sealed record AuthMessage(
    [property: JsonPropertyName("deviceId")] string DeviceId,
    [property: JsonPropertyName("token")] string Token
) : ProtocolMessage;

public sealed record PairRequestMessage(
    [property: JsonPropertyName("deviceId")] string DeviceId,
    [property: JsonPropertyName("deviceName")] string DeviceName,
    [property: JsonPropertyName("pin")] string Pin
) : ProtocolMessage;

public sealed record AuthOkMessage(
    [property: JsonPropertyName("sessionId")] string SessionId,
    [property: JsonPropertyName("serverName")] string ServerName
) : ProtocolMessage;

public sealed record PairOkMessage(
    [property: JsonPropertyName("token")] string Token,
    [property: JsonPropertyName("serverId")] string ServerId,
    [property: JsonPropertyName("serverName")] string ServerName
) : ProtocolMessage;

public sealed record AuthFailMessage(
    [property: JsonPropertyName("reason")] string Reason
) : ProtocolMessage;

public sealed record PairFailMessage(
    [property: JsonPropertyName("reason")] string Reason,
    [property: JsonPropertyName("attemptsLeft")] int AttemptsLeft,
    [property: JsonPropertyName("retryAfterSec")] int RetryAfterSec
) : ProtocolMessage;

public sealed record PingMessage(
    [property: JsonPropertyName("ts")] long Ts
) : ProtocolMessage;

public sealed record PongMessage(
    [property: JsonPropertyName("ts")] long Ts
) : ProtocolMessage;

public sealed record ByeMessage(
    [property: JsonPropertyName("reason")] string Reason
) : ProtocolMessage;

public sealed record ErrorMessage(
    [property: JsonPropertyName("code")] string Code,
    [property: JsonPropertyName("message")] string Message
) : ProtocolMessage;

public sealed record ClickMessage(
    [property: JsonPropertyName("button")] string Button,
    [property: JsonPropertyName("count")] int Count = 1
) : ProtocolMessage;

public sealed record ButtonMessage(
    [property: JsonPropertyName("button")] string Button,
    [property: JsonPropertyName("action")] string Action
) : ProtocolMessage;

public sealed record TextMessage(
    [property: JsonPropertyName("text")] string Text
) : ProtocolMessage;

public sealed record KeyMessage(
    [property: JsonPropertyName("key")] string Key,
    [property: JsonPropertyName("action")] string Action
) : ProtocolMessage;

public sealed record ComboMessage(
    [property: JsonPropertyName("keys")] IReadOnlyList<string> Keys
) : ProtocolMessage;

public sealed record MediaMessage(
    [property: JsonPropertyName("action")] string Action
) : ProtocolMessage;

public sealed record SystemMessage(
    [property: JsonPropertyName("action")] string Action,
    [property: JsonPropertyName("confirm")] bool Confirm = false
) : ProtocolMessage;

public sealed record DiscoverMessage(
    [property: JsonPropertyName("v")] int V = ProtocolConstants.ProtocolVersion
) : ProtocolMessage;

public sealed record DiscoverReplyMessage(
    [property: JsonPropertyName("v")] int V,
    [property: JsonPropertyName("serverId")] string ServerId,
    [property: JsonPropertyName("serverName")] string ServerName,
    [property: JsonPropertyName("host")] string Host,
    [property: JsonPropertyName("port")] int Port,
    [property: JsonPropertyName("fp")] string Fp,
    [property: JsonPropertyName("pairingOpen")] bool PairingOpen
) : ProtocolMessage;
