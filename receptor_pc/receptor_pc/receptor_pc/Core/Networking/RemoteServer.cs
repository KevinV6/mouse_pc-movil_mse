using System.Collections.Concurrent;
using System.Net;
using System.Net.Sockets;
using receptor_pc.Core.Input;
using receptor_pc.Core.Protocol;
using receptor_pc.Core.Security;
using receptor_pc.Services;

namespace receptor_pc.Core.Networking;

public sealed class RemoteServer : IAsyncDisposable
{
    private readonly CertificateProvider _certificateProvider;
    private readonly PairingManager _pairingManager;
    private readonly SessionRegistry _sessionRegistry;
    private readonly ICommandSink _commandSink;
    private readonly Func<string> _serverNameProvider;
    private readonly FileLogger _logger;
    private readonly TimeProvider _timeProvider;

    private readonly ConcurrentDictionary<IPAddress, IpFailureRecord> _ipFailures = new();
    private readonly ConcurrentDictionary<ClientSession, Task> _runningSessions = new();

    private TcpListener? _listener;
    private CancellationTokenSource? _cts;
    private Task? _acceptTask;

    public int Port { get; private set; }
    public bool IsRunning => _acceptTask is not null && !_acceptTask.IsCompleted;

    public RemoteServer(
        CertificateProvider certificateProvider,
        PairingManager pairingManager,
        SessionRegistry sessionRegistry,
        ICommandSink commandSink,
        Func<string> serverNameProvider,
        FileLogger logger,
        int port = ProtocolConstants.TcpPort,
        TimeProvider? timeProvider = null)
    {
        _certificateProvider = certificateProvider;
        _pairingManager = pairingManager;
        _sessionRegistry = sessionRegistry;
        _commandSink = commandSink;
        _serverNameProvider = serverNameProvider;
        _logger = logger;
        Port = port;
        _timeProvider = timeProvider ?? TimeProvider.System;
    }

    public void Start(IPAddress? bindAddress = null)
    {
        if (_acceptTask is not null)
        {
            return;
        }

        _cts = new CancellationTokenSource();
        _listener = new TcpListener(bindAddress ?? IPAddress.Any, Port);
        _listener.Start();
        if (_listener.LocalEndpoint is IPEndPoint ep)
        {
            Port = ep.Port;
        }

        _acceptTask = Task.Run(() => AcceptLoopAsync(_listener, _cts.Token));
        _logger.Info($"RemoteServer TLS listening on port {Port} (ServerId={_certificateProvider.ServerId})");
    }

    private async Task AcceptLoopAsync(TcpListener listener, CancellationToken ct)
    {
        while (!ct.IsCancellationRequested)
        {
            TcpClient? tcpClient = null;
            try
            {
                tcpClient = await listener.AcceptTcpClientAsync(ct).ConfigureAwait(false);
                if (tcpClient.Client.RemoteEndPoint is IPEndPoint remoteEp && IsIpRateLimited(remoteEp.Address))
                {
                    _logger.Warn($"Rejected connection from rate-limited IP {remoteEp.Address}");
                    tcpClient.Dispose();
                    continue;
                }

                var session = new ClientSession(
                    tcpClient,
                    _certificateProvider.Certificate,
                    _certificateProvider.ServerId,
                    _serverNameProvider,
                    _pairingManager,
                    _sessionRegistry,
                    _commandSink,
                    _logger,
                    RecordAuthFailure);

                Task sessionTask = RunSessionTrackedAsync(session, ct);
                _runningSessions[session] = sessionTask;
            }
            catch (OperationCanceledException)
            {
                tcpClient?.Dispose();
                break;
            }
            catch (ObjectDisposedException)
            {
                tcpClient?.Dispose();
                break;
            }
            catch (Exception ex)
            {
                tcpClient?.Dispose();
                _logger.Error("AcceptLoop error", ex);
            }
        }
    }

    private async Task RunSessionTrackedAsync(ClientSession session, CancellationToken ct)
    {
        try
        {
            await session.RunAsync(ct).ConfigureAwait(false);
        }
        finally
        {
            _runningSessions.TryRemove(session, out _);
        }
    }

    private bool IsIpRateLimited(IPAddress ip)
    {
        if (!_ipFailures.TryGetValue(ip, out IpFailureRecord? record))
        {
            return false;
        }

        DateTimeOffset now = _timeProvider.GetUtcNow();
        lock (record)
        {
            return now < record.BlockedUntil;
        }
    }

    private void RecordAuthFailure(IPAddress ip)
    {
        DateTimeOffset now = _timeProvider.GetUtcNow();
        IpFailureRecord record = _ipFailures.GetOrAdd(ip, _ => new IpFailureRecord());

        lock (record)
        {
            record.Timestamps.RemoveAll(t => (now - t) > TimeSpan.FromMinutes(1));
            record.Timestamps.Add(now);
            if (record.Timestamps.Count >= 10)
            {
                record.BlockedUntil = now.AddSeconds(60);
                record.Timestamps.Clear();
                _logger.Warn($"IP {ip} blocked for 60s after 10 auth failures/min.");
            }
        }
    }

    public async ValueTask DisposeAsync()
    {
        await _sessionRegistry.DisconnectAllAsync("server_shutdown").ConfigureAwait(false);

        if (_cts is not null)
        {
            await _cts.CancelAsync().ConfigureAwait(false);
        }

        try { _listener?.Stop(); } catch { }

        if (_acceptTask is not null)
        {
            try { await _acceptTask.ConfigureAwait(false); } catch { }
        }

        Task[] activeTasks = _runningSessions.Values.ToArray();
        if (activeTasks.Length > 0)
        {
            try { await Task.WhenAll(activeTasks).WaitAsync(TimeSpan.FromSeconds(2)).ConfigureAwait(false); } catch { }
        }

        _cts?.Dispose();
        _cts = null;
        _acceptTask = null;
    }

    private sealed class IpFailureRecord
    {
        public List<DateTimeOffset> Timestamps { get; } = new();
        public DateTimeOffset BlockedUntil { get; set; } = DateTimeOffset.MinValue;
    }
}
