using System.IO;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
using System.Threading.Channels;
using receptor_pc.Core.Input;
using receptor_pc.Core.Protocol;
using receptor_pc.Core.Security;
using receptor_pc.Services;

namespace receptor_pc.Core.Networking;

public sealed class ClientSession : IAsyncDisposable
{
    private enum SessionState
    {
        AwaitHello,
        AwaitAuth,
        Authenticated,
        Closed
    }

    private readonly TcpClient _tcpClient;
    private readonly X509Certificate2 _serverCertificate;
    private readonly string _serverId;
    private readonly Func<string> _serverNameProvider;
    private readonly PairingManager _pairingManager;
    private readonly SessionRegistry _registry;
    private readonly ICommandSink _commandSink;
    private readonly Action<IPAddress>? _onAuthFailedIp;
    private readonly FileLogger _logger;

    private readonly Channel<byte[]> _writeChannel = Channel.CreateUnbounded<byte[]>(
        new UnboundedChannelOptions { SingleReader = true, SingleWriter = false });
    private readonly CancellationTokenSource _sessionCts = new();

    private SslStream? _sslStream;
    private SessionState _state = SessionState.AwaitHello;
    private int _invalidJsonCount;
    private int _closing;

    public string SessionId { get; } = $"s-{Guid.NewGuid().ToString("N")[..8]}";
    public string DeviceId { get; private set; } = string.Empty;
    public string DeviceName { get; private set; } = "Dispositivo";
    public string RemoteIp { get; }
    public IPAddress RemoteIpAddress { get; }
    public DateTimeOffset ConnectedAt { get; } = DateTimeOffset.UtcNow;
    public long LastRttMs { get; private set; }

    public ClientSession(
        TcpClient tcpClient,
        X509Certificate2 serverCertificate,
        string serverId,
        Func<string> serverNameProvider,
        PairingManager pairingManager,
        SessionRegistry registry,
        ICommandSink commandSink,
        FileLogger logger,
        Action<IPAddress>? onAuthFailedIp = null)
    {
        _tcpClient = tcpClient;
        _tcpClient.NoDelay = true;
        _serverCertificate = serverCertificate;
        _serverId = serverId;
        _serverNameProvider = serverNameProvider;
        _pairingManager = pairingManager;
        _registry = registry;
        _commandSink = commandSink;
        _logger = logger;
        _onAuthFailedIp = onAuthFailedIp;

        if (tcpClient.Client.RemoteEndPoint is IPEndPoint ep)
        {
            RemoteIpAddress = ep.Address;
            RemoteIp = ep.Address.ToString();
        }
        else
        {
            RemoteIpAddress = IPAddress.Loopback;
            RemoteIp = "127.0.0.1";
        }
    }

    public ActiveSessionInfo ToInfo() =>
        new(SessionId, DeviceId, DeviceName, RemoteIp, ConnectedAt, LastRttMs);

    public async Task RunAsync(CancellationToken serverToken)
    {
        using var linkedCts = CancellationTokenSource.CreateLinkedTokenSource(serverToken, _sessionCts.Token);
        CancellationToken ct = linkedCts.Token;

        Task? writerTask = null;
        try
        {
            NetworkStream netStream = _tcpClient.GetStream();
            _sslStream = new SslStream(netStream, leaveInnerStreamOpen: false);

            using (var handshakeCts = CancellationTokenSource.CreateLinkedTokenSource(ct))
            {
                handshakeCts.CancelAfter(ProtocolConstants.HandshakeTimeout);
                var sslOptions = new SslServerAuthenticationOptions
                {
                    ServerCertificate = _serverCertificate,
                    ClientCertificateRequired = false,
                    EnabledSslProtocols = SslProtocols.Tls12 | SslProtocols.Tls13,
                    CertificateRevocationCheckMode = X509RevocationMode.NoCheck
                };
                await _sslStream.AuthenticateAsServerAsync(sslOptions, handshakeCts.Token).ConfigureAwait(false);
            }

            writerTask = Task.Run(() => WriterLoopAsync(_sslStream, ct), CancellationToken.None);

            DateTimeOffset handshakeDeadline = DateTimeOffset.UtcNow.Add(ProtocolConstants.HandshakeTimeout);

            while (!ct.IsCancellationRequested && _state != SessionState.Closed)
            {
                TimeSpan timeout = _state == SessionState.Authenticated
                    ? ProtocolConstants.IdleTimeout
                    : MaxPositive(handshakeDeadline - DateTimeOffset.UtcNow, TimeSpan.FromMilliseconds(200));

                if (_state != SessionState.Authenticated && DateTimeOffset.UtcNow >= handshakeDeadline)
                {
                    _logger.Warn($"Handshake timeout from {RemoteIp}");
                    break;
                }

                RawFrame? frame;
                using (var readTimeoutCts = CancellationTokenSource.CreateLinkedTokenSource(ct))
                {
                    readTimeoutCts.CancelAfter(timeout);
                    frame = await FrameCodec.ReadFrameAsync(_sslStream, readTimeoutCts.Token).ConfigureAwait(false);
                }

                if (frame is null)
                {
                    break;
                }

                bool keepGoing = await HandleFrameAsync(frame.Value).ConfigureAwait(false);
                if (!keepGoing)
                {
                    break;
                }
            }
        }
        catch (OperationCanceledException)
        {
            // Normal timeout or shutdown
        }
        catch (AuthenticationException ex)
        {
            _logger.Warn($"TLS handshake failed from {RemoteIp}: {ex.Message}");
        }
        catch (InvalidDataException ex)
        {
            _logger.Warn($"Invalid frame from {RemoteIp}: {ex.Message}");
        }
        catch (IOException)
        {
            // Socket closed
        }
        catch (Exception ex)
        {
            _logger.Error($"Session error ({RemoteIp})", ex);
        }
        finally
        {
            _state = SessionState.Closed;
            _writeChannel.Writer.TryComplete();
            if (writerTask is not null)
            {
                try
                {
                    await writerTask.WaitAsync(TimeSpan.FromSeconds(1)).ConfigureAwait(false);
                }
                catch
                {
                    // Ignore
                }
            }

            if (!string.IsNullOrEmpty(DeviceId))
            {
                _commandSink.OnSessionClosed(DeviceId);
                _registry.Unregister(this);
            }

            await DisposeAsync().ConfigureAwait(false);
        }
    }

    private async Task<bool> HandleFrameAsync(RawFrame raw)
    {
        FrameType? known = raw.KnownType;
        if (known is null)
        {
            // Ignore unknown frame types for forward compatibility
            return true;
        }

        if (_state != SessionState.Authenticated && known != FrameType.Json)
        {
            EnqueueMessage(new ErrorMessage("not_authenticated", "Binary frames require authentication."));
            return false;
        }

        switch (known.Value)
        {
            case FrameType.MouseMove:
            {
                var (dx, dy) = FrameCodec.DecodePairInt16(raw.Payload);
                _commandSink.EnqueueMouseMove(DeviceId, dx, dy);
                return true;
            }
            case FrameType.Scroll:
            {
                var (dx, dy) = FrameCodec.DecodePairInt16(raw.Payload);
                _commandSink.EnqueueScroll(DeviceId, dx, dy);
                return true;
            }
            case FrameType.Zoom:
            {
                short steps = FrameCodec.DecodeSingleInt16(raw.Payload);
                _commandSink.EnqueueZoom(DeviceId, steps);
                return true;
            }
            case FrameType.Json:
                return await HandleJsonFrameAsync(raw.Payload).ConfigureAwait(false);
            default:
                return true;
        }
    }

    private async Task<bool> HandleJsonFrameAsync(byte[] payload)
    {
        ProtocolMessage message;
        try
        {
            message = FrameCodec.DecodeJson(payload);
            _invalidJsonCount = 0;
        }
        catch (JsonException ex)
        {
            _invalidJsonCount++;
            _logger.Warn($"Bad JSON from {RemoteIp} (#{_invalidJsonCount}): {ex.Message}");
            EnqueueMessage(new ErrorMessage("bad_message", "Malformed JSON frame."));
            return _invalidJsonCount <= 3;
        }

        if (message is PingMessage ping)
        {
            long nowMs = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
            if (ping.Ts > 0 && nowMs >= ping.Ts && (nowMs - ping.Ts) < 60_000)
            {
                LastRttMs = nowMs - ping.Ts;
                _registry.NotifyUpdated();
            }
            EnqueueMessage(new PongMessage(ping.Ts));
            return true;
        }

        if (message is ByeMessage)
        {
            return false;
        }

        switch (_state)
        {
            case SessionState.AwaitHello:
                if (message is not HelloMessage hello)
                {
                    EnqueueMessage(new ErrorMessage("not_authenticated", "Expected hello first."));
                    return false;
                }

                if (hello.V != ProtocolConstants.ProtocolVersion)
                {
                    await SendImmediateAndDrainAsync(new AuthFailMessage("version_mismatch")).ConfigureAwait(false);
                    return false;
                }

                DeviceId = hello.DeviceId;
                DeviceName = string.IsNullOrWhiteSpace(hello.DeviceName) ? "Android" : hello.DeviceName;
                _state = SessionState.AwaitAuth;

                EnqueueMessage(new HelloAckMessage(
                    V: ProtocolConstants.ProtocolVersion,
                    ServerId: _serverId,
                    ServerName: _serverNameProvider(),
                    PairingOpen: _pairingManager.PairingOpen));
                return true;

            case SessionState.AwaitAuth:
                if (message is AuthMessage auth)
                {
                    if (!string.IsNullOrWhiteSpace(auth.DeviceId))
                    {
                        DeviceId = auth.DeviceId;
                    }

                    AuthResult res = _pairingManager.TryAuth(DeviceId, auth.Token);
                    if (!res.Success)
                    {
                        _onAuthFailedIp?.Invoke(RemoteIpAddress);
                        await SendImmediateAndDrainAsync(new AuthFailMessage(res.FailReason ?? "bad_token")).ConfigureAwait(false);
                        return false;
                    }

                    _state = SessionState.Authenticated;
                    await _registry.RegisterAuthenticatedAsync(this).ConfigureAwait(false);
                    EnqueueMessage(new AuthOkMessage(SessionId, _serverNameProvider()));
                    _logger.Info($"Device authenticated: {DeviceName} ({DeviceId}) from {RemoteIp}");
                    return true;
                }

                if (message is PairRequestMessage pairReq)
                {
                    if (!string.IsNullOrWhiteSpace(pairReq.DeviceId))
                    {
                        DeviceId = pairReq.DeviceId;
                    }
                    if (!string.IsNullOrWhiteSpace(pairReq.DeviceName))
                    {
                        DeviceName = pairReq.DeviceName;
                    }

                    PairResult res = _pairingManager.TryPair(DeviceId, DeviceName, pairReq.Pin);
                    if (!res.Success || res.Token is null)
                    {
                        _onAuthFailedIp?.Invoke(RemoteIpAddress);
                        await SendImmediateAndDrainAsync(new PairFailMessage(
                            res.FailReason ?? "bad_pin",
                            res.AttemptsLeft,
                            res.RetryAfterSec)).ConfigureAwait(false);
                        return false;
                    }

                    _state = SessionState.Authenticated;
                    await _registry.RegisterAuthenticatedAsync(this).ConfigureAwait(false);
                    EnqueueMessage(new PairOkMessage(res.Token, _serverId, _serverNameProvider()));
                    _logger.Info($"New device paired: {DeviceName} ({DeviceId}) from {RemoteIp}");
                    return true;
                }

                EnqueueMessage(new ErrorMessage("not_authenticated", "Expected auth or pair_request."));
                return false;

            case SessionState.Authenticated:
                if (message is ClickMessage or ButtonMessage or TextMessage or KeyMessage or ComboMessage or MediaMessage or SystemMessage)
                {
                    _commandSink.EnqueueJsonCommand(DeviceId, message);
                    return true;
                }

                EnqueueMessage(new ErrorMessage("unsupported", $"Unexpected message type in authenticated state: {message.GetType().Name}"));
                return true;

            default:
                return false;
        }
    }

    public void EnqueueMessage(ProtocolMessage message)
    {
        try
        {
            byte[] encoded = FrameCodec.EncodeJson(message);
            _writeChannel.Writer.TryWrite(encoded);
        }
        catch
        {
            // Ignore serialization errors
        }
    }

    private async Task SendImmediateAndDrainAsync(ProtocolMessage message)
    {
        EnqueueMessage(message);
        await Task.Delay(60).ConfigureAwait(false);
    }

    public async Task CloseWithByeAsync(string reason)
    {
        if (Interlocked.Exchange(ref _closing, 1) == 1)
        {
            return;
        }

        try
        {
            EnqueueMessage(new ByeMessage(reason));
            await Task.Delay(60).ConfigureAwait(false);
        }
        catch
        {
            // Ignore
        }

        try
        {
            await _sessionCts.CancelAsync().ConfigureAwait(false);
        }
        catch
        {
            // Ignore
        }
    }

    private async Task WriterLoopAsync(Stream stream, CancellationToken ct)
    {
        try
        {
            await foreach (byte[] frame in _writeChannel.Reader.ReadAllAsync(ct).ConfigureAwait(false))
            {
                await FrameCodec.WriteFrameAsync(stream, frame, ct).ConfigureAwait(false);
            }
        }
        catch
        {
            // Connection closed
        }
    }

    private static TimeSpan MaxPositive(TimeSpan value, TimeSpan min) =>
        value > min ? value : min;

    public ValueTask DisposeAsync()
    {
        try { _sslStream?.Dispose(); } catch { }
        try { _tcpClient.Dispose(); } catch { }
        try { _sessionCts.Dispose(); } catch { }
        return ValueTask.CompletedTask;
    }
}
