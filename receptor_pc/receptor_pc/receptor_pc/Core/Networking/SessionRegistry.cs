using System.Collections.Concurrent;
using System.IO;
using System.Net;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography.X509Certificates;
using System.Threading.Channels;
using receptor_pc.Core.Input;
using receptor_pc.Core.Protocol;
using receptor_pc.Core.Security;
using receptor_pc.Services;

namespace receptor_pc.Core.Networking;

public sealed record ActiveSessionInfo(
    string SessionId,
    string DeviceId,
    string DeviceName,
    string RemoteIp,
    DateTimeOffset ConnectedAt,
    long LastRttMs
);

public sealed class SessionRegistry
{
    private readonly TrustedDeviceStore _trustedStore;
    private readonly FileLogger _logger;
    private readonly ConcurrentDictionary<string, ClientSession> _sessionsByDeviceId = new(StringComparer.Ordinal);

    public event EventHandler? SessionsChanged;
    public event EventHandler<ActiveSessionInfo>? DeviceConnected;
    public event EventHandler<ActiveSessionInfo>? DeviceDisconnected;

    public SessionRegistry(TrustedDeviceStore trustedStore, FileLogger logger)
    {
        _trustedStore = trustedStore;
        _logger = logger;
    }

    public IReadOnlyList<ActiveSessionInfo> GetActiveSessions()
    {
        return _sessionsByDeviceId.Values
            .Select(s => s.ToInfo())
            .OrderByDescending(s => s.ConnectedAt)
            .ToList();
    }

    internal async Task RegisterAuthenticatedAsync(ClientSession session)
    {
        string deviceId = session.DeviceId;
        if (_sessionsByDeviceId.TryRemove(deviceId, out ClientSession? previous) && !ReferenceEquals(previous, session))
        {
            _logger.Info($"Replacing existing session for device {deviceId}");
            await previous.CloseWithByeAsync("replaced").ConfigureAwait(false);
        }

        _sessionsByDeviceId[deviceId] = session;
        ActiveSessionInfo info = session.ToInfo();
        SessionsChanged?.Invoke(this, EventArgs.Empty);
        DeviceConnected?.Invoke(this, info);
    }

    internal void Unregister(ClientSession session)
    {
        if (string.IsNullOrEmpty(session.DeviceId))
        {
            return;
        }

        if (_sessionsByDeviceId.TryGetValue(session.DeviceId, out var current) && ReferenceEquals(current, session))
        {
            if (_sessionsByDeviceId.TryRemove(session.DeviceId, out _))
            {
                ActiveSessionInfo info = session.ToInfo();
                SessionsChanged?.Invoke(this, EventArgs.Empty);
                DeviceDisconnected?.Invoke(this, info);
            }
        }
    }

    public void NotifyUpdated()
    {
        SessionsChanged?.Invoke(this, EventArgs.Empty);
    }

    public async Task DisconnectAsync(string deviceId, string reason = "client_exit")
    {
        if (_sessionsByDeviceId.TryRemove(deviceId, out ClientSession? session))
        {
            ActiveSessionInfo info = session.ToInfo();
            await session.CloseWithByeAsync(reason).ConfigureAwait(false);
            SessionsChanged?.Invoke(this, EventArgs.Empty);
            DeviceDisconnected?.Invoke(this, info);
        }
    }

    public async Task RevokeAsync(string deviceId)
    {
        _trustedStore.Revoke(deviceId);
        await DisconnectAsync(deviceId, "revoked").ConfigureAwait(false);
    }

    public async Task DisconnectAllAsync(string reason = "server_shutdown")
    {
        var all = _sessionsByDeviceId.Values.ToList();
        _sessionsByDeviceId.Clear();
        foreach (var session in all)
        {
            await session.CloseWithByeAsync(reason).ConfigureAwait(false);
        }
        SessionsChanged?.Invoke(this, EventArgs.Empty);
    }
}
