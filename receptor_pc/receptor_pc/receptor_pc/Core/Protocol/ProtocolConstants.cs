namespace receptor_pc.Core.Protocol;

public static class ProtocolConstants
{
    public const int ProtocolVersion = 1;
    public const int TcpPort = 47800;
    public const int DiscoveryPort = 47801;
    public const int MaxFramePayload = 16384;

    public static readonly TimeSpan HandshakeTimeout = TimeSpan.FromSeconds(10);
    public static readonly TimeSpan PingInterval = TimeSpan.FromSeconds(5);
    public static readonly TimeSpan IdleTimeout = TimeSpan.FromSeconds(15);
    public static readonly TimeSpan MoveFlushInterval = TimeSpan.FromMilliseconds(8);
    public static readonly TimeSpan PinLifetime = TimeSpan.FromMinutes(5);
    public static readonly TimeSpan PinLockoutDuration = TimeSpan.FromSeconds(30);

    public const int PinMaxAttempts = 5;
    public const string UriScheme = "mse://pair";
}
