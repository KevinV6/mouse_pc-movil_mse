using System.Net;
using System.Net.Sockets;
using System.Text.Json;
using receptor_pc.Core.Protocol;
using receptor_pc.Core.Security;
using receptor_pc.Services;

namespace receptor_pc.Core.Networking;

public sealed class DiscoveryResponder : IAsyncDisposable
{
    private readonly CertificateProvider _certificateProvider;
    private readonly PairingManager _pairingManager;
    private readonly Func<string> _serverNameProvider;
    private readonly int _tcpPort;
    private readonly int _discoveryPort;
    private readonly FileLogger _logger;

    private UdpClient? _udpClient;
    private CancellationTokenSource? _cts;
    private Task? _listenTask;

    public DiscoveryResponder(
        CertificateProvider certificateProvider,
        PairingManager pairingManager,
        Func<string> serverNameProvider,
        FileLogger logger,
        int tcpPort = ProtocolConstants.TcpPort,
        int discoveryPort = ProtocolConstants.DiscoveryPort)
    {
        _certificateProvider = certificateProvider;
        _pairingManager = pairingManager;
        _serverNameProvider = serverNameProvider;
        _logger = logger;
        _tcpPort = tcpPort;
        _discoveryPort = discoveryPort;
    }

    public void Start()
    {
        if (_listenTask is not null)
        {
            return;
        }

        _cts = new CancellationTokenSource();
        _udpClient = new UdpClient(AddressFamily.InterNetwork);
        _udpClient.Client.SetSocketOption(SocketOptionLevel.Socket, SocketOptionName.ReuseAddress, true);
        _udpClient.Client.Bind(new IPEndPoint(IPAddress.Any, _discoveryPort));
        _listenTask = Task.Run(() => ListenLoopAsync(_udpClient, _cts.Token));
        _logger.Info($"UDP discovery responder listening on port {_discoveryPort}");
    }

    private async Task ListenLoopAsync(UdpClient client, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            try
            {
                UdpReceiveResult received = await client.ReceiveAsync(ct).ConfigureAwait(false);
                if (received.Buffer.Length == 0 || received.Buffer.Length > 1024)
                {
                    continue;
                }

                ProtocolMessage? msg;
                try
                {
                    msg = JsonSerializer.Deserialize(
                        received.Buffer,
                        ProtocolJsonContext.Relaxed.ProtocolMessage);
                }
                catch
                {
                    continue;
                }

                if (msg is not DiscoverMessage)
                {
                    continue;
                }

                var reply = new DiscoverReplyMessage(
                    V: ProtocolConstants.ProtocolVersion,
                    ServerId: _certificateProvider.ServerId,
                    ServerName: _serverNameProvider(),
                    Host: NetworkInfo.GetPreferredLanIp(),
                    Port: _tcpPort,
                    Fp: _certificateProvider.Fingerprint,
                    PairingOpen: _pairingManager.PairingOpen);

                byte[] payload = JsonSerializer.SerializeToUtf8Bytes<ProtocolMessage>(
                    reply,
                    ProtocolJsonContext.Relaxed.ProtocolMessage);

                await client.SendAsync(payload, received.RemoteEndPoint, ct).ConfigureAwait(false);
            }
            catch (OperationCanceledException)
            {
                break;
            }
            catch (ObjectDisposedException)
            {
                break;
            }
            catch (Exception ex)
            {
                _logger.Warn($"DiscoveryResponder error: {ex.Message}");
            }
        }
    }

    public async ValueTask DisposeAsync()
    {
        if (_cts is not null)
        {
            await _cts.CancelAsync().ConfigureAwait(false);
        }

        _udpClient?.Dispose();

        if (_listenTask is not null)
        {
            try
            {
                await _listenTask.ConfigureAwait(false);
            }
            catch
            {
                // Ignore
            }
        }

        _cts?.Dispose();
        _cts = null;
        _listenTask = null;
    }
}
