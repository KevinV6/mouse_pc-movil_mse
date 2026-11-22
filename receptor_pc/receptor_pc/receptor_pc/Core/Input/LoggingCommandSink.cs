using receptor_pc.Core.Protocol;
using receptor_pc.Services;

namespace receptor_pc.Core.Input;

public sealed class LoggingCommandSink : ICommandSink
{
    private readonly FileLogger _logger;

    public LoggingCommandSink(FileLogger logger)
    {
        _logger = logger;
    }

    public void EnqueueMouseMove(string deviceId, short dx, short dy)
    {
        _logger.Info($"[{deviceId}] MOUSE_MOVE dx={dx} dy={dy}");
    }

    public void EnqueueScroll(string deviceId, short dx, short dy)
    {
        _logger.Info($"[{deviceId}] SCROLL dx={dx} dy={dy}");
    }

    public void EnqueueZoom(string deviceId, short steps)
    {
        _logger.Info($"[{deviceId}] ZOOM steps={steps}");
    }

    public void EnqueueJsonCommand(string deviceId, ProtocolMessage message)
    {
        _logger.Info($"[{deviceId}] CMD {message.GetType().Name}");
    }

    public void OnSessionClosed(string deviceId)
    {
        _logger.Info($"[{deviceId}] Session input state reset");
    }
}
