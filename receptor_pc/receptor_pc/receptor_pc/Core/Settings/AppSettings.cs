using System.Text.Json.Serialization;
using receptor_pc.Core.Protocol;

namespace receptor_pc.Core.Settings;

public sealed record AppSettings
{
    [JsonPropertyName("serverName")]
    public string ServerName { get; init; } = Environment.MachineName;

    [JsonPropertyName("port")]
    public int Port { get; init; } = ProtocolConstants.TcpPort;

    [JsonPropertyName("pairingOpen")]
    public bool PairingOpen { get; init; } = true;

    [JsonPropertyName("startWithWindows")]
    public bool StartWithWindows { get; init; } = false;
}
