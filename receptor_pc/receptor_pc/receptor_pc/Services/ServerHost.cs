using receptor_pc.Core.Input;
using receptor_pc.Core.Networking;
using receptor_pc.Core.Security;
using receptor_pc.Core.Settings;

namespace receptor_pc.Services;

public enum ServerHostState
{
    Stopped,
    Running,
    Error
}

public sealed class ServerHost : IAsyncDisposable
{
    private readonly SettingsStore _settingsStore;
    private readonly CertificateProvider _certificateProvider;
    private readonly PairingManager _pairingManager;
    private readonly SessionRegistry _sessionRegistry;
    private readonly ICommandSink _commandSink;
    private readonly FileLogger _logger;

    private RemoteServer? _remoteServer;
    private DiscoveryResponder? _discoveryResponder;

    public ServerHostState State { get; private set; } = ServerHostState.Stopped;
    public string? LastError { get; private set; }
    public event EventHandler<ServerHostState>? StateChanged;

    public ServerHost(
        SettingsStore settingsStore,
        CertificateProvider certificateProvider,
        PairingManager pairingManager,
        SessionRegistry sessionRegistry,
        ICommandSink commandSink,
        FileLogger logger)
    {
        _settingsStore = settingsStore;
        _certificateProvider = certificateProvider;
        _pairingManager = pairingManager;
        _sessionRegistry = sessionRegistry;
        _commandSink = commandSink;
        _logger = logger;
    }

    public int ActivePort => _remoteServer?.Port ?? _settingsStore.Current.Port;

    public Task StartAsync()
    {
        if (State == ServerHostState.Running)
        {
            return Task.CompletedTask;
        }

        try
        {
            int port = _settingsStore.Current.Port;
            _pairingManager.PairingOpen = _settingsStore.Current.PairingOpen;

            _remoteServer = new RemoteServer(
                _certificateProvider,
                _pairingManager,
                _sessionRegistry,
                _commandSink,
                () => _settingsStore.Current.ServerName,
                _logger,
                port);
            _remoteServer.Start();

            _discoveryResponder = new DiscoveryResponder(
                _certificateProvider,
                _pairingManager,
                () => _settingsStore.Current.ServerName,
                _logger,
                _remoteServer.Port);
            _discoveryResponder.Start();

            LastError = null;
            SetState(ServerHostState.Running);
            _logger.Info($"ServerHost started on {NetworkInfo.GetPreferredLanIp()}:{_remoteServer.Port}");
        }
        catch (Exception ex)
        {
            LastError = ex.Message;
            _logger.Error("Failed to start ServerHost", ex);
            SetState(ServerHostState.Error);
        }

        return Task.CompletedTask;
    }

    public async Task StopAsync()
    {
        if (_discoveryResponder is not null)
        {
            await _discoveryResponder.DisposeAsync().ConfigureAwait(false);
            _discoveryResponder = null;
        }

        if (_remoteServer is not null)
        {
            await _remoteServer.DisposeAsync().ConfigureAwait(false);
            _remoteServer = null;
        }

        SetState(ServerHostState.Stopped);
        _logger.Info("ServerHost stopped");
    }

    private void SetState(ServerHostState newState)
    {
        State = newState;
        StateChanged?.Invoke(this, newState);
    }

    public async ValueTask DisposeAsync()
    {
        await StopAsync().ConfigureAwait(false);
    }
}
