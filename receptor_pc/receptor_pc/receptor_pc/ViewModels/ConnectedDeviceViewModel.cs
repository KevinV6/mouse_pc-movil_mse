using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace receptor_pc.ViewModels;

public sealed partial class ConnectedDeviceViewModel : ObservableObject
{
    private readonly Func<string, Task> _disconnectAction;
    private readonly Func<string, Task> _revokeAction;

    public string DeviceId { get; }

    [ObservableProperty]
    private string _deviceName;

    [ObservableProperty]
    private string _remoteIp;

    [ObservableProperty]
    private long _lastRttMs;

    [ObservableProperty]
    private bool _isOnline;

    [ObservableProperty]
    private string _statusSubtitle;

    public ConnectedDeviceViewModel(
        string deviceId,
        string deviceName,
        string remoteIp,
        long lastRttMs,
        bool isOnline,
        string statusSubtitle,
        Func<string, Task> disconnectAction,
        Func<string, Task> revokeAction)
    {
        DeviceId = deviceId;
        _deviceName = deviceName;
        _remoteIp = remoteIp;
        _lastRttMs = lastRttMs;
        _isOnline = isOnline;
        _statusSubtitle = statusSubtitle;
        _disconnectAction = disconnectAction;
        _revokeAction = revokeAction;
    }

    [RelayCommand]
    private async Task DisconnectAsync()
    {
        await _disconnectAction(DeviceId);
    }

    [RelayCommand]
    private async Task RevokeAsync()
    {
        await _revokeAction(DeviceId);
    }
}
