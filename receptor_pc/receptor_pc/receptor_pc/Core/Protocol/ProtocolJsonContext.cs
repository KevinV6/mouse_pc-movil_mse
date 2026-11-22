using System.Text.Encodings.Web;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace receptor_pc.Core.Protocol;

[JsonSourceGenerationOptions(
    PropertyNamingPolicy = JsonKnownNamingPolicy.CamelCase,
    DefaultIgnoreCondition = JsonIgnoreCondition.Never,
    WriteIndented = false)]
[JsonSerializable(typeof(ProtocolMessage))]
[JsonSerializable(typeof(HelloMessage))]
[JsonSerializable(typeof(HelloAckMessage))]
[JsonSerializable(typeof(AuthMessage))]
[JsonSerializable(typeof(PairRequestMessage))]
[JsonSerializable(typeof(AuthOkMessage))]
[JsonSerializable(typeof(PairOkMessage))]
[JsonSerializable(typeof(AuthFailMessage))]
[JsonSerializable(typeof(PairFailMessage))]
[JsonSerializable(typeof(PingMessage))]
[JsonSerializable(typeof(PongMessage))]
[JsonSerializable(typeof(ByeMessage))]
[JsonSerializable(typeof(ErrorMessage))]
[JsonSerializable(typeof(ClickMessage))]
[JsonSerializable(typeof(ButtonMessage))]
[JsonSerializable(typeof(TextMessage))]
[JsonSerializable(typeof(KeyMessage))]
[JsonSerializable(typeof(ComboMessage))]
[JsonSerializable(typeof(MediaMessage))]
[JsonSerializable(typeof(SystemMessage))]
[JsonSerializable(typeof(DiscoverMessage))]
[JsonSerializable(typeof(DiscoverReplyMessage))]
public partial class ProtocolJsonContext : JsonSerializerContext
{
    public static ProtocolJsonContext Relaxed { get; } = new(new JsonSerializerOptions
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        Encoder = JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
        WriteIndented = false
    });
}
