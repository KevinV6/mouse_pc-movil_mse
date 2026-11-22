using System.Threading.Channels;
using receptor_pc.Core.Protocol;
using receptor_pc.Services;

namespace receptor_pc.Core.Input;

public sealed class CommandDispatcher : ICommandSink, IAsyncDisposable
{
    private abstract record QueuedItem;
    private sealed record MouseMoveItem(short Dx, short Dy) : QueuedItem;
    private sealed record ScrollItem(short Dx, short Dy) : QueuedItem;
    private sealed record ZoomItem(short Steps) : QueuedItem;
    private sealed record JsonItem(ProtocolMessage Message) : QueuedItem;
    private sealed record SessionClosedItem(string DeviceId) : QueuedItem;

    private readonly IInputSimulator _inputSimulator;
    private readonly ISystemActions _systemActions;
    private readonly FileLogger _logger;
    private readonly Channel<QueuedItem> _channel = Channel.CreateUnbounded<QueuedItem>(
        new UnboundedChannelOptions { SingleReader = true, SingleWriter = false });
    private readonly CancellationTokenSource _cts = new();
    private readonly Task _consumerTask;

    public CommandDispatcher(
        IInputSimulator inputSimulator,
        ISystemActions systemActions,
        FileLogger logger)
    {
        _inputSimulator = inputSimulator;
        _systemActions = systemActions;
        _logger = logger;
        _consumerTask = Task.Run(() => ConsumeLoopAsync(_cts.Token));
    }

    public void EnqueueMouseMove(string deviceId, short dx, short dy) =>
        _channel.Writer.TryWrite(new MouseMoveItem(dx, dy));

    public void EnqueueScroll(string deviceId, short dx, short dy) =>
        _channel.Writer.TryWrite(new ScrollItem(dx, dy));

    public void EnqueueZoom(string deviceId, short steps) =>
        _channel.Writer.TryWrite(new ZoomItem(steps));

    public void EnqueueJsonCommand(string deviceId, ProtocolMessage message) =>
        _channel.Writer.TryWrite(new JsonItem(message));

    public void OnSessionClosed(string deviceId) =>
        _channel.Writer.TryWrite(new SessionClosedItem(deviceId));

    private async Task ConsumeLoopAsync(CancellationToken ct)
    {
        try
        {
            await foreach (QueuedItem item in _channel.Reader.ReadAllAsync(ct).ConfigureAwait(false))
            {
                try
                {
                    DispatchItem(item);
                }
                catch (Exception ex)
                {
                    _logger.Error("CommandDispatcher execution error", ex);
                }
            }
        }
        catch (OperationCanceledException)
        {
            // Shutdown
        }
    }

    private void DispatchItem(QueuedItem item)
    {
        switch (item)
        {
            case MouseMoveItem m:
                _inputSimulator.MoveRelative(m.Dx, m.Dy);
                break;

            case ScrollItem s:
                _inputSimulator.Scroll(s.Dx, s.Dy);
                break;

            case ZoomItem z:
                _inputSimulator.Zoom(z.Steps);
                break;

            case SessionClosedItem:
                _inputSimulator.ReleaseAll();
                break;

            case JsonItem { Message: var msg }:
                switch (msg)
                {
                    case ClickMessage click:
                        _inputSimulator.Click(click.Button, click.Count);
                        break;
                    case ButtonMessage btn:
                        _inputSimulator.Button(btn.Button, btn.Action);
                        break;
                    case TextMessage txt:
                        _inputSimulator.TypeText(txt.Text);
                        break;
                    case KeyMessage key:
                        _inputSimulator.Key(key.Key, key.Action);
                        break;
                    case ComboMessage combo:
                        _inputSimulator.Combo(combo.Keys);
                        break;
                    case MediaMessage media:
                        _inputSimulator.Media(media.Action);
                        break;
                    case SystemMessage sys:
                        _systemActions.Execute(sys.Action, sys.Confirm);
                        break;
                }
                break;
        }
    }

    public async ValueTask DisposeAsync()
    {
        _channel.Writer.TryComplete();
        await _cts.CancelAsync().ConfigureAwait(false);
        try
        {
            await _consumerTask.ConfigureAwait(false);
        }
        catch
        {
            // Ignore
        }
        _inputSimulator.ReleaseAll();
        _cts.Dispose();
    }
}
