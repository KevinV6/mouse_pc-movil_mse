using System.Collections.Concurrent;
using receptor_pc.Core.Input;
using receptor_pc.Core.Protocol;
using receptor_pc.Services;
using Xunit;

namespace receptor_pc.Tests;

public class KeyMapTests
{
    [Fact]
    public void AllProtocolKeys_AreMappedAndNonZero()
    {
        string[] required =
        [
            "CTRL", "ALT", "SHIFT", "WIN",
            "ENTER", "BACKSPACE", "DELETE", "TAB", "ESC", "SPACE", "INSERT",
            "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PAGE_UP", "PAGE_DOWN",
            "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
            "A", "Z", "0", "9",
            "PRINT_SCREEN", "CAPS_LOCK", "CONTEXT_MENU"
        ];

        foreach (string key in required)
        {
            Assert.True(KeyMap.TryResolve(key, out VirtualKeyInfo info), $"Key '{key}' was not resolved.");
            Assert.NotEqual(0, info.Vk);
        }

        Assert.True(KeyMap.TryResolve("UP", out var upInfo) && upInfo.IsExtended);
        Assert.True(KeyMap.TryResolve("WIN", out var winInfo) && winInfo.IsExtended);
    }
}

internal sealed class FakeInputSimulator : IInputSimulator
{
    public ConcurrentQueue<string> Events { get; } = new();

    public void MoveRelative(short dx, short dy) => Events.Enqueue($"move:{dx},{dy}");
    public void Click(string button, int count) => Events.Enqueue($"click:{button}:{count}");
    public void Button(string button, string action) => Events.Enqueue($"button:{button}:{action}");
    public void Scroll(short dx, short dy) => Events.Enqueue($"scroll:{dx},{dy}");
    public void Zoom(short steps) => Events.Enqueue($"zoom:{steps}");
    public void TypeText(string text) => Events.Enqueue($"text:{text}");
    public void Key(string keyName, string action) => Events.Enqueue($"key:{keyName}:{action}");
    public void Combo(IReadOnlyList<string> keys) => Events.Enqueue($"combo:{string.Join('+', keys)}");
    public void Media(string action) => Events.Enqueue($"media:{action}");
    public void ReleaseAll() => Events.Enqueue("release_all");
}

internal sealed class FakeSystemActions : ISystemActions
{
    public ConcurrentQueue<string> Actions { get; } = new();

    public void Execute(string action, bool confirm) => Actions.Enqueue($"sys:{action}:{confirm}");
}

public class CommandDispatcherTests
{
    [Fact]
    public async Task PreservesOrder_AndReleasesOnSessionClose()
    {
        var fakeSim = new FakeInputSimulator();
        var fakeSys = new FakeSystemActions();
        var logger = new FileLogger();

        await using var dispatcher = new CommandDispatcher(fakeSim, fakeSys, logger);

        dispatcher.EnqueueJsonCommand("d1", new ButtonMessage("left", "down"));
        dispatcher.EnqueueMouseMove("d1", 10, 20);
        dispatcher.EnqueueScroll("d1", 0, -120);
        dispatcher.EnqueueZoom("d1", 2);
        dispatcher.EnqueueJsonCommand("d1", new TextMessage("Hola"));
        dispatcher.EnqueueJsonCommand("d1", new ComboMessage(["CTRL", "C"]));
        dispatcher.EnqueueJsonCommand("d1", new SystemMessage("lock", true));
        dispatcher.OnSessionClosed("d1");

        await Task.Delay(80);

        string[] expected =
        [
            "button:left:down",
            "move:10,20",
            "scroll:0,-120",
            "zoom:2",
            "text:Hola",
            "combo:CTRL+C",
            "release_all"
        ];

        Assert.Equal(expected, fakeSim.Events.ToArray());
        Assert.Single(fakeSys.Actions, "sys:lock:True");
    }
}
