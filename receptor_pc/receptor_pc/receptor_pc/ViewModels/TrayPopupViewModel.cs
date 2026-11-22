using System.Collections.ObjectModel;
using System.IO;
using System.Windows;
using System.Windows.Media.Imaging;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using QRCoder;
using receptor_pc.Core.Networking;
using receptor_pc.Core.Protocol;
using receptor_pc.Core.Security;
using receptor_pc.Core.Settings;
using receptor_pc.Services;

namespace receptor_pc.ViewModels;

public sealed partial class TrayPopupViewModel : ObservableObject
{
    private readonly ServerHost _serverHost;
    private readonly CertificateProvider _certificateProvider;
    private readonly PairingManager _pairingManager;
    private readonly SessionRegistry _sessionRegistry;
    private readonly TrustedDeviceStore _trustedStore;
    private readonly SettingsStore _settingsStore;
    private readonly AutoStartManager _autoStartManager;
    private readonly Action _openGuideAction;
    private readonly Action _exitApplicationAction;

    [ObservableProperty]
    private string _serverName = Environment.MachineName;

    [ObservableProperty]
    private string _ipAddress = "127.0.0.1";

    [ObservableProperty]
    private int _port = ProtocolConstants.TcpPort;

    [ObservableProperty]
    private bool _isServerRunning;

    [ObservableProperty]
    private string _statusText = "Activo";

    [ObservableProperty]
    private string _pinFormatted = "000 000";

    [ObservableProperty]
    private string _verificationCode = "0000-0000";

    [ObservableProperty]
    private BitmapImage? _qrImage;

    [ObservableProperty]
    private bool _pairingOpen = true;

    [ObservableProperty]
    private bool _startWithWindows;

    [ObservableProperty]
    private string _copyFeedback = "Copiar";

    public ObservableCollection<ConnectedDeviceViewModel> ConnectedDevices { get; } = [];
    public ObservableCollection<ConnectedDeviceViewModel> TrustedDevices { get; } = [];

    public string AddressDisplay => $"{IpAddress}:{Port}";

    public TrayPopupViewModel(
        ServerHost serverHost,
        CertificateProvider certificateProvider,
        PairingManager pairingManager,
        SessionRegistry sessionRegistry,
        TrustedDeviceStore trustedStore,
        SettingsStore settingsStore,
        AutoStartManager autoStartManager,
        Action openGuideAction,
        Action exitApplicationAction)
    {
        _serverHost = serverHost;
        _certificateProvider = certificateProvider;
        _pairingManager = pairingManager;
        _sessionRegistry = sessionRegistry;
        _trustedStore = trustedStore;
        _settingsStore = settingsStore;
        _autoStartManager = autoStartManager;
        _openGuideAction = openGuideAction;
        _exitApplicationAction = exitApplicationAction;

        _serverHost.StateChanged += (_, _) => RunOnUi(RefreshServerState);
        _pairingManager.PinChanged += (_, _) => RunOnUi(RefreshPairingData);
        _sessionRegistry.SessionsChanged += (_, _) => RunOnUi(RefreshDevices);
        _trustedStore.Changed += (_, _) => RunOnUi(RefreshDevices);

        RefreshAll();
    }

    public void RefreshAll()
    {
        ServerName = _settingsStore.Current.ServerName;
        VerificationCode = _certificateProvider.VerificationCode;
        StartWithWindows = _autoStartManager.IsEnabled();
        RefreshServerState();
        RefreshPairingData();
        RefreshDevices();
    }

    private void RefreshServerState()
    {
        IpAddress = NetworkInfo.GetPreferredLanIp();
        Port = _serverHost.ActivePort;
        OnPropertyChanged(nameof(AddressDisplay));

        IsServerRunning = _serverHost.State == ServerHostState.Running;
        StatusText = _serverHost.State switch
        {
            ServerHostState.Running => "En línea (TLS)",
            ServerHostState.Error => $"Error: {_serverHost.LastError}",
            _ => "Pausado"
        };
    }

    private void RefreshPairingData()
    {
        PairingOpen = _pairingManager.PairingOpen;
        PinFormatted = _pairingManager.CurrentPinFormatted;
        QrImage = BuildQrBitmap(BuildPairingUri());
    }

    private string BuildPairingUri()
    {
        string host = Uri.EscapeDataString(IpAddress);
        string name = Uri.EscapeDataString(ServerName);
        string fp = Uri.EscapeDataString(_certificateProvider.Fingerprint);
        string pin = Uri.EscapeDataString(_pairingManager.CurrentPin);
        return $"{ProtocolConstants.UriScheme}?v={ProtocolConstants.ProtocolVersion}&h={host}&p={Port}&n={name}&fp={fp}&pin={pin}";
    }

    private static BitmapImage? BuildQrBitmap(string payload)
    {
        try
        {
            using var generator = new QRCodeGenerator();
            using QRCodeData data = generator.CreateQrCode(payload, QRCodeGenerator.ECCLevel.M);
            using var qr = new PngByteQRCode(data);
            byte[] pngBytes = qr.GetGraphic(8);

            var image = new BitmapImage();
            using var ms = new MemoryStream(pngBytes);
            image.BeginInit();
            image.CacheOption = BitmapCacheOption.OnLoad;
            image.StreamSource = ms;
            image.EndInit();
            image.Freeze();
            return image;
        }
        catch
        {
            return null;
        }
    }

    private void RefreshDevices()
    {
        IReadOnlyList<ActiveSessionInfo> active = _sessionRegistry.GetActiveSessions();
        var activeIds = new HashSet<string>(active.Select(a => a.DeviceId), StringComparer.Ordinal);

        ConnectedDevices.Clear();
        foreach (ActiveSessionInfo s in active)
        {
            string rttLabel = s.LastRttMs > 0 ? $"{s.LastRttMs} ms" : "< 5 ms";
            ConnectedDevices.Add(new ConnectedDeviceViewModel(
                s.DeviceId,
                s.DeviceName,
                s.RemoteIp,
                s.LastRttMs,
                isOnline: true,
                statusSubtitle: $"{s.RemoteIp} · RTT {rttLabel}",
                disconnectAction: id => _sessionRegistry.DisconnectAsync(id),
                revokeAction: id => _sessionRegistry.RevokeAsync(id)));
        }

        TrustedDevices.Clear();
        foreach (TrustedDevice td in _trustedStore.GetAll())
        {
            bool online = activeIds.Contains(td.DeviceId);
            string sub = online
                ? "Conectado ahora"
                : $"Última vez: {td.LastSeen.LocalDateTime:dd/MM HH:mm}";

            TrustedDevices.Add(new ConnectedDeviceViewModel(
                td.DeviceId,
                td.DeviceName,
                string.Empty,
                0,
                isOnline: online,
                statusSubtitle: sub,
                disconnectAction: id => _sessionRegistry.DisconnectAsync(id),
                revokeAction: id => _sessionRegistry.RevokeAsync(id)));
        }
    }

    partial void OnPairingOpenChanged(bool value)
    {
        _pairingManager.PairingOpen = value;
        _settingsStore.Update(s => s with { PairingOpen = value });
    }

    partial void OnStartWithWindowsChanged(bool value)
    {
        _autoStartManager.SetEnabled(value);
        _settingsStore.Update(s => s with { StartWithWindows = value });
    }

    [RelayCommand]
    private void NewPin()
    {
        _pairingManager.RotatePin();
    }

    [RelayCommand]
    private async Task CopyAddressAsync()
    {
        try
        {
            Clipboard.SetText(AddressDisplay);
            CopyFeedback = "¡Copiado!";
            await Task.Delay(1500);
            CopyFeedback = "Copiar";
        }
        catch
        {
            // Ignore clipboard contention
        }
    }

    [RelayCommand]
    private async Task ToggleServerAsync()
    {
        if (IsServerRunning)
        {
            await _serverHost.StopAsync();
        }
        else
        {
            await _serverHost.StartAsync();
        }
    }

    [RelayCommand]
    private void OpenGuide()
    {
        _openGuideAction();
    }

    [RelayCommand]
    private void ExitApp()
    {
        _exitApplicationAction();
    }

    private static void RunOnUi(Action action)
    {
        if (Application.Current?.Dispatcher is { } dispatcher)
        {
            if (dispatcher.CheckAccess())
            {
                action();
            }
            else
            {
                dispatcher.BeginInvoke(action);
            }
        }
        else
        {
            action();
        }
    }
}
