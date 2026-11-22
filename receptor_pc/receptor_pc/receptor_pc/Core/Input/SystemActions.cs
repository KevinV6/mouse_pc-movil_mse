using System.Diagnostics;
using receptor_pc.Services;

namespace receptor_pc.Core.Input;

public sealed class SystemActions : ISystemActions
{
    private readonly FileLogger _logger;

    public SystemActions(FileLogger logger)
    {
        _logger = logger;
    }

    public void Execute(string action, bool confirm)
    {
        string act = (action ?? string.Empty).Trim().ToLowerInvariant();
        switch (act)
        {
            case "lock":
                _logger.Info("Executing system action: lock");
                NativeMethods.LockWorkStation();
                break;

            case "sleep":
                if (!confirm)
                {
                    _logger.Warn("Rejected sleep action without confirm=true");
                    return;
                }
                _logger.Info("Executing system action: sleep");
                NativeMethods.SetSuspendState(false, false, false);
                break;

            case "shutdown":
                if (!confirm)
                {
                    _logger.Warn("Rejected shutdown action without confirm=true");
                    return;
                }
                _logger.Info("Executing system action: shutdown");
                StartShutdownProcess("/s /t 0");
                break;

            case "restart":
                if (!confirm)
                {
                    _logger.Warn("Rejected restart action without confirm=true");
                    return;
                }
                _logger.Info("Executing system action: restart");
                StartShutdownProcess("/r /t 0");
                break;

            default:
                _logger.Warn($"Unknown system action: {action}");
                break;
        }
    }

    private static void StartShutdownProcess(string arguments)
    {
        Process.Start(new ProcessStartInfo
        {
            FileName = "shutdown.exe",
            Arguments = arguments,
            CreateNoWindow = true,
            UseShellExecute = false
        });
    }
}
