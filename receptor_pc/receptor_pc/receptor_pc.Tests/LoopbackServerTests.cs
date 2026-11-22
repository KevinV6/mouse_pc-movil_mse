using System.Collections.Concurrent;
using System.IO;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using receptor_pc.Core.Input;
using receptor_pc.Core.Networking;
using receptor_pc.Core.Protocol;
using receptor_pc.Core.Security;
using receptor_pc.Services;
using Xunit;

namespace receptor_pc.Tests;

internal sealed class CapturingCommandSink : ICommandSink
{
    public ConcurrentQueue<(string DeviceId, short Dx, short Dy)> Moves { get; } = new();
    public ConcurrentQueue<(string DeviceId, ProtocolMessage Message)> JsonCommands { get; } = new();

    public void EnqueueMouseMove(string deviceId, short dx, short dy) => Moves.Enqueue((deviceId, dx, dy));
    public void EnqueueScroll(string deviceId, short dx, short dy) { }
    public void EnqueueZoom(string deviceId, short steps) { }
    public void EnqueueJsonCommand(string deviceId, ProtocolMessage message) => JsonCommands.Enqueue((deviceId, message));
    public void OnSessionClosed(string deviceId) { }
}

public class LoopbackServerTests
{
    [Fact]
    public async Task PairThenAuth_PingPong_MouseMove_AndBadTokenRejected()
    {
        string tempDir = Path.Combine(Path.GetTempPath(), $"mse-test-loopback-{Guid.NewGuid():N}");
        Directory.CreateDirectory(tempDir);

        using var certProvider = new CertificateProvider(tempDir);
        var logger = new FileLogger(tempDir);
        var store = new TrustedDeviceStore(tempDir);
        var pairing = new PairingManager(store);
        var registry = new SessionRegistry(store, logger);
        var sink = new CapturingCommandSink();

        await using var server = new RemoteServer(
            certProvider,
            pairing,
            registry,
            sink,
            () => "TEST-PC",
            logger,
            port: 0); // ephemeral port

        server.Start(IPAddress.Loopback);
        int port = server.Port;
        Assert.True(port > 0);

        string issuedToken;

        // 1. Pair over TLS
        using (var tcp = new TcpClient { NoDelay = true })
        {
            await tcp.ConnectAsync(IPAddress.Loopback, port);
            using var ssl = new SslStream(tcp.GetStream(), false, (_, cert, _, _) =>
            {
                Assert.NotNull(cert);
                string fp = CryptoUtils.Sha256Base64Url(cert!.GetRawCertData());
                return fp == certProvider.Fingerprint;
            });

            await ssl.AuthenticateAsClientAsync(new SslClientAuthenticationOptions
            {
                TargetHost = "127.0.0.1",
                EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13
            });

            await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(
                new HelloMessage(1, "dev-loop", "LoopbackPhone", "android", "1.0")));

            RawFrame? helloAckRaw = await FrameCodec.ReadFrameAsync(ssl);
            Assert.NotNull(helloAckRaw);
            var helloAck = Assert.IsType<HelloAckMessage>(FrameCodec.DecodeJson(helloAckRaw.Value.Payload));
            Assert.Equal("TEST-PC", helloAck.ServerName);

            await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(
                new PairRequestMessage("dev-loop", "LoopbackPhone", pairing.CurrentPin)));

            RawFrame? pairOkRaw = await FrameCodec.ReadFrameAsync(ssl);
            Assert.NotNull(pairOkRaw);
            var pairOk = Assert.IsType<PairOkMessage>(FrameCodec.DecodeJson(pairOkRaw.Value.Payload));
            issuedToken = pairOk.Token;
            Assert.False(string.IsNullOrWhiteSpace(issuedToken));

            // Send Ping
            await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(new PingMessage(12345L)));
            RawFrame? pongRaw = await FrameCodec.ReadFrameAsync(ssl);
            Assert.NotNull(pongRaw);
            var pong = Assert.IsType<PongMessage>(FrameCodec.DecodeJson(pongRaw.Value.Payload));
            Assert.Equal(12345L, pong.Ts);

            // Send binary MouseMove
            await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeMouseMove(12, -8));
            await Task.Delay(40);
            Assert.True(sink.Moves.TryDequeue(out var move));
            Assert.Equal((short)12, move.Dx);
            Assert.Equal((short)-8, move.Dy);

            await FrameCodec.WriteFrameAsync(ssl, FrameCodec.EncodeJson(new ByeMessage("client_exit")));
        }

        // 2. Reconnect with saved token -> AuthOk
        using (var tcp2 = new TcpClient { NoDelay = true })
        {
            await tcp2.ConnectAsync(IPAddress.Loopback, port);
            using var ssl2 = new SslStream(tcp2.GetStream(), false, (_, _, _, _) => true);
            await ssl2.AuthenticateAsClientAsync("127.0.0.1");

            await FrameCodec.WriteFrameAsync(ssl2, FrameCodec.EncodeJson(
                new HelloMessage(1, "dev-loop", "LoopbackPhone", "android", "1.0")));
            _ = await FrameCodec.ReadFrameAsync(ssl2);

            await FrameCodec.WriteFrameAsync(ssl2, FrameCodec.EncodeJson(
                new AuthMessage("dev-loop", issuedToken)));

            RawFrame? authOkRaw = await FrameCodec.ReadFrameAsync(ssl2);
            Assert.NotNull(authOkRaw);
            Assert.IsType<AuthOkMessage>(FrameCodec.DecodeJson(authOkRaw.Value.Payload));
        }

        // 3. Connect with bad token -> AuthFail
        using (var tcp3 = new TcpClient { NoDelay = true })
        {
            await tcp3.ConnectAsync(IPAddress.Loopback, port);
            using var ssl3 = new SslStream(tcp3.GetStream(), false, (_, _, _, _) => true);
            await ssl3.AuthenticateAsClientAsync("127.0.0.1");

            await FrameCodec.WriteFrameAsync(ssl3, FrameCodec.EncodeJson(
                new HelloMessage(1, "dev-loop", "LoopbackPhone", "android", "1.0")));
            _ = await FrameCodec.ReadFrameAsync(ssl3);

            await FrameCodec.WriteFrameAsync(ssl3, FrameCodec.EncodeJson(
                new AuthMessage("dev-loop", "invalid_token_xyz")));

            RawFrame? authFailRaw = await FrameCodec.ReadFrameAsync(ssl3);
            Assert.NotNull(authFailRaw);
            var fail = Assert.IsType<AuthFailMessage>(FrameCodec.DecodeJson(authFailRaw.Value.Payload));
            Assert.Equal("bad_token", fail.Reason);
        }

        try { Directory.Delete(tempDir, true); } catch { }
    }
}
