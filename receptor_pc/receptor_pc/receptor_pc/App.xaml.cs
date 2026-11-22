using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
using CommunityToolkit.Mvvm.Input;
using H.NotifyIcon;
using H.NotifyIcon.Core;
using Microsoft.Extensions.DependencyInjection;
using receptor_pc.Core.Input;
using receptor_pc.Core.Networking;
using receptor_pc.Core.Security;
using receptor_pc.Core.Settings;
using receptor_pc.Services;
using receptor_pc.ViewModels;
using receptor_pc.Views;

namespace receptor_pc;

public partial class App : Application
{
    private SingleInstanceGuard? _singleInstanceGuard;
    private ServiceProvider? _serviceProvider;
    private TaskbarIcon? _trayIcon;
    private TrayPopupWindow? _popupWindow;
    private GuideWindow? _guideWindow;

    public IServiceProvider Services =>
        _serviceProvider ?? throw new InvalidOperationException("ServiceProvider not initialized.");

    protected override async void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        _singleInstanceGuard = new SingleInstanceGuard();
        if (!_singleInstanceGuard.IsFirstInstance)
        {
            _singleInstanceGuard.SignalFirstInstance();
            _singleInstanceGuard.Dispose();
            Shutdown();
            return;
        }

        _singleInstanceGuard.SecondInstanceSignaled += (_, _) =>
        {
            Dispatcher.BeginInvoke(ShowTrayPopup);
        };

        var services = new ServiceCollection();
        ConfigureServices(services);
        _serviceProvider = services.BuildServiceProvider();

        var serverHost = _serviceProvider.GetRequiredService<ServerHost>();
        var sessionRegistry = _serviceProvider.GetRequiredService<SessionRegistry>();

        await serverHost.StartAsync();

        InitializeTrayIcon(serverHost, sessionRegistry);

        bool launchedSilent = e.Args.Any(a => string.Equals(a, "--tray", StringComparison.OrdinalIgnoreCase));
        if (!launchedSilent)
        {
            // Keep running quietly in the notification area, as required by spec
        }
    }

    private void ConfigureServices(IServiceCollection services)
    {
        services.AddSingleton<FileLogger>();
        services.AddSingleton<SettingsStore>();
        services.AddSingleton<AutoStartManager>();
        services.AddSingleton<CertificateProvider>();
        services.AddSingleton<TrustedDeviceStore>();
        services.AddSingleton<PairingManager>();
        services.AddSingleton<SessionRegistry>();
        services.AddSingleton<IInputSimulator, InputSimulator>();
        services.AddSingleton<ISystemActions, SystemActions>();
        services.AddSingleton<CommandDispatcher>();
        services.AddSingleton<ICommandSink>(sp => sp.GetRequiredService<CommandDispatcher>());
        services.AddSingleton<ServerHost>();
        services.AddTransient<GuideViewModel>();
        services.AddSingleton<TrayPopupViewModel>(sp => new TrayPopupViewModel(
            sp.GetRequiredService<ServerHost>(),
            sp.GetRequiredService<CertificateProvider>(),
            sp.GetRequiredService<PairingManager>(),
            sp.GetRequiredService<SessionRegistry>(),
            sp.GetRequiredService<TrustedDeviceStore>(),
            sp.GetRequiredService<SettingsStore>(),
            sp.GetRequiredService<AutoStartManager>(),
            OpenGuideWindow,
            () => _ = ExitCompletelyAsync()));
    }

    private void InitializeTrayIcon(ServerHost serverHost, SessionRegistry sessionRegistry)
    {
        var contextMenu = new ContextMenu();

        var openItem = new MenuItem { Header = "Abrir panel MSE" };
        openItem.Click += (_, _) => ShowTrayPopup();

        var guideItem = new MenuItem { Header = "Guía de Uso" };
        guideItem.Click += (_, _) => OpenGuideWindow();

        var pauseResumeItem = new MenuItem { Header = "Pausar / Reanudar servidor" };
        pauseResumeItem.Click += async (_, _) =>
        {
            if (serverHost.State == ServerHostState.Running)
            {
                await serverHost.StopAsync();
            }
            else
            {
                await serverHost.StartAsync();
            }
        };

        var exitItem = new MenuItem { Header = "Salir completamente" };
        exitItem.Click += (_, _) => _ = ExitCompletelyAsync();

        contextMenu.Items.Add(openItem);
        contextMenu.Items.Add(guideItem);
        contextMenu.Items.Add(new Separator());
        contextMenu.Items.Add(pauseResumeItem);
        contextMenu.Items.Add(new Separator());
        contextMenu.Items.Add(exitItem);

        _trayIcon = new TaskbarIcon
        {
            ToolTipText = $"MSE Receptor — {NetworkInfo.GetPreferredLanIp()}:{serverHost.ActivePort}",
            IconSource = LoadTrayIconSource(serverHost.State == ServerHostState.Running),
            ContextMenu = contextMenu,
            LeftClickCommand = new RelayCommand(ToggleTrayPopup)
        };
        _trayIcon.ForceCreate();

        serverHost.StateChanged += (_, state) =>
        {
            Dispatcher.BeginInvoke(() =>
            {
                if (_trayIcon is null) return;
                bool running = state == ServerHostState.Running;
                _trayIcon.IconSource = LoadTrayIconSource(running);
                _trayIcon.ToolTipText = running
                    ? $"MSE Receptor — {NetworkInfo.GetPreferredLanIp()}:{serverHost.ActivePort}"
                    : "MSE Receptor — Pausado";
            });
        };

        sessionRegistry.DeviceConnected += (_, info) =>
        {
            Dispatcher.BeginInvoke(() =>
            {
                try
                {
                    _trayIcon?.ShowNotification(
                        "Dispositivo conectado",
                        $"{info.DeviceName} ({info.RemoteIp})",
                        NotificationIcon.Info);
                }
                catch
                {
                    // Ignore notification errors
                }
            });
        };
    }

    private static BitmapImage LoadTrayIconSource(bool running)
    {
        string file = running ? "tray_on.ico" : "tray_off.ico";
        return new BitmapImage(new Uri($"pack://application:,,,/Assets/{file}", UriKind.Absolute));
    }

    private TrayPopupWindow EnsurePopupWindow()
    {
        if (_popupWindow is null)
        {
            var vm = Services.GetRequiredService<TrayPopupViewModel>();
            _popupWindow = new TrayPopupWindow(vm);
        }
        return _popupWindow;
    }

    private void ShowTrayPopup()
    {
        EnsurePopupWindow().ShowNearTray();
    }

    private void ToggleTrayPopup()
    {
        EnsurePopupWindow().ToggleNearTray();
    }

    private void OpenGuideWindow()
    {
        if (_guideWindow is null || !_guideWindow.IsLoaded)
        {
            var vm = Services.GetRequiredService<GuideViewModel>();
            _guideWindow = new GuideWindow(vm);
            _guideWindow.Closed += (_, _) => _guideWindow = null;
            _guideWindow.Show();
        }
        else
        {
            _guideWindow.Activate();
        }
    }

    private async Task ExitCompletelyAsync()
    {
        try
        {
            if (_popupWindow is not null)
            {
                _popupWindow.AllowFullClose = true;
                _popupWindow.Close();
            }

            _guideWindow?.Close();

            if (_serviceProvider is not null)
            {
                var serverHost = _serviceProvider.GetRequiredService<ServerHost>();
                await serverHost.StopAsync();

                var dispatcher = _serviceProvider.GetRequiredService<CommandDispatcher>();
                await dispatcher.DisposeAsync();
            }
        }
        catch
        {
            // Ignore shutdown errors
        }
        finally
        {
            try { _trayIcon?.Dispose(); } catch { }
            try { _serviceProvider?.Dispose(); } catch { }
            try { _singleInstanceGuard?.Dispose(); } catch { }
            Shutdown();
        }
    }
}
