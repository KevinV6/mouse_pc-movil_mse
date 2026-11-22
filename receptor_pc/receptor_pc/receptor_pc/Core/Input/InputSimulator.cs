namespace receptor_pc.Core.Input;

public sealed class InputSimulator : IInputSimulator
{
    private readonly HashSet<string> _heldMouseButtons = new(StringComparer.OrdinalIgnoreCase);
    private readonly HashSet<VirtualKeyInfo> _heldKeys = new();
    private readonly object _stateLock = new();

    public void MoveRelative(short dx, short dy)
    {
        if (dx == 0 && dy == 0) return;

        var input = new NativeMethods.INPUT
        {
            type = NativeMethods.INPUT_MOUSE,
            U = new NativeMethods.InputUnion
            {
                mi = new NativeMethods.MOUSEINPUT
                {
                    dx = dx,
                    dy = dy,
                    dwFlags = NativeMethods.MOUSEEVENTF_MOVE
                }
            }
        };

        NativeMethods.SendInput(1, [input], NativeMethods.INPUT.Size);
    }

    public void Click(string button, int count)
    {
        if (!TryGetButtonFlags(button, out uint downFlag, out uint upFlag))
        {
            return;
        }

        int clicks = Math.Clamp(count, 1, 3);
        var inputs = new NativeMethods.INPUT[clicks * 2];
        for (int i = 0; i < clicks; i++)
        {
            inputs[i * 2] = CreateMouseFlagInput(downFlag);
            inputs[i * 2 + 1] = CreateMouseFlagInput(upFlag);
        }

        NativeMethods.SendInput((uint)inputs.Length, inputs, NativeMethods.INPUT.Size);
    }

    public void Button(string button, string action)
    {
        if (!TryGetButtonFlags(button, out uint downFlag, out uint upFlag))
        {
            return;
        }

        bool isDown = string.Equals(action, "down", StringComparison.OrdinalIgnoreCase);
        bool isUp = string.Equals(action, "up", StringComparison.OrdinalIgnoreCase);
        if (!isDown && !isUp)
        {
            return;
        }

        lock (_stateLock)
        {
            if (isDown) _heldMouseButtons.Add(button.ToLowerInvariant());
            else _heldMouseButtons.Remove(button.ToLowerInvariant());
        }

        var input = CreateMouseFlagInput(isDown ? downFlag : upFlag);
        NativeMethods.SendInput(1, [input], NativeMethods.INPUT.Size);
    }

    public void Scroll(short dx, short dy)
    {
        var list = new List<NativeMethods.INPUT>(2);
        if (dy != 0)
        {
            list.Add(new NativeMethods.INPUT
            {
                type = NativeMethods.INPUT_MOUSE,
                U = new NativeMethods.InputUnion
                {
                    mi = new NativeMethods.MOUSEINPUT
                    {
                        mouseData = dy,
                        dwFlags = NativeMethods.MOUSEEVENTF_WHEEL
                    }
                }
            });
        }

        if (dx != 0)
        {
            list.Add(new NativeMethods.INPUT
            {
                type = NativeMethods.INPUT_MOUSE,
                U = new NativeMethods.InputUnion
                {
                    mi = new NativeMethods.MOUSEINPUT
                    {
                        mouseData = dx,
                        dwFlags = NativeMethods.MOUSEEVENTF_HWHEEL
                    }
                }
            });
        }

        if (list.Count > 0)
        {
            NativeMethods.SendInput((uint)list.Count, list.ToArray(), NativeMethods.INPUT.Size);
        }
    }

    public void Zoom(short steps)
    {
        if (steps == 0) return;

        int wheelDelta = Math.Clamp((int)steps, -20, 20) * NativeMethods.WHEEL_DELTA;
        var ctrlInfo = new VirtualKeyInfo(KeyMap.VK_CONTROL, false);

        NativeMethods.INPUT[] inputs =
        [
            CreateKeyVkInput(ctrlInfo, isUp: false),
            new NativeMethods.INPUT
            {
                type = NativeMethods.INPUT_MOUSE,
                U = new NativeMethods.InputUnion
                {
                    mi = new NativeMethods.MOUSEINPUT
                    {
                        mouseData = wheelDelta,
                        dwFlags = NativeMethods.MOUSEEVENTF_WHEEL
                    }
                }
            },
            CreateKeyVkInput(ctrlInfo, isUp: true)
        ];

        NativeMethods.SendInput((uint)inputs.Length, inputs, NativeMethods.INPUT.Size);
    }

    public void TypeText(string text)
    {
        if (string.IsNullOrEmpty(text)) return;

        var inputs = new List<NativeMethods.INPUT>(text.Length * 2);
        var enterInfo = new VirtualKeyInfo(KeyMap.VK_RETURN, false);

        for (int i = 0; i < text.Length; i++)
        {
            char c = text[i];
            if (c == '\r')
            {
                continue;
            }

            if (c == '\n')
            {
                inputs.Add(CreateKeyVkInput(enterInfo, isUp: false));
                inputs.Add(CreateKeyVkInput(enterInfo, isUp: true));
                continue;
            }

            inputs.Add(CreateUnicodeInput(c, isUp: false));
            inputs.Add(CreateUnicodeInput(c, isUp: true));
        }

        if (inputs.Count > 0)
        {
            NativeMethods.SendInput((uint)inputs.Count, inputs.ToArray(), NativeMethods.INPUT.Size);
        }
    }

    public void Key(string keyName, string action)
    {
        if (!KeyMap.TryResolve(keyName, out VirtualKeyInfo info))
        {
            return;
        }

        string act = (action ?? "press").Trim().ToLowerInvariant();
        switch (act)
        {
            case "down":
                lock (_stateLock) { _heldKeys.Add(info); }
                NativeMethods.SendInput(1, [CreateKeyVkInput(info, isUp: false)], NativeMethods.INPUT.Size);
                break;

            case "up":
                lock (_stateLock) { _heldKeys.Remove(info); }
                NativeMethods.SendInput(1, [CreateKeyVkInput(info, isUp: true)], NativeMethods.INPUT.Size);
                break;

            default: // press
                NativeMethods.SendInput(
                    2,
                    [CreateKeyVkInput(info, isUp: false), CreateKeyVkInput(info, isUp: true)],
                    NativeMethods.INPUT.Size);
                break;
        }
    }

    public void Combo(IReadOnlyList<string> keys)
    {
        if (keys is null || keys.Count == 0) return;

        var resolved = new List<VirtualKeyInfo>(keys.Count);
        foreach (string k in keys)
        {
            if (KeyMap.TryResolve(k, out VirtualKeyInfo info))
            {
                resolved.Add(info);
            }
        }

        if (resolved.Count == 0) return;

        var inputs = new List<NativeMethods.INPUT>(resolved.Count * 2);
        for (int i = 0; i < resolved.Count; i++)
        {
            inputs.Add(CreateKeyVkInput(resolved[i], isUp: false));
        }
        for (int i = resolved.Count - 1; i >= 0; i--)
        {
            inputs.Add(CreateKeyVkInput(resolved[i], isUp: true));
        }

        NativeMethods.SendInput((uint)inputs.Count, inputs.ToArray(), NativeMethods.INPUT.Size);
    }

    public void Media(string action)
    {
        ushort? vk = (action ?? string.Empty).Trim().ToLowerInvariant() switch
        {
            "vol_up" => KeyMap.VK_VOLUME_UP,
            "vol_down" => KeyMap.VK_VOLUME_DOWN,
            "mute" => KeyMap.VK_VOLUME_MUTE,
            "play_pause" => KeyMap.VK_MEDIA_PLAY_PAUSE,
            "next" => KeyMap.VK_MEDIA_NEXT_TRACK,
            "prev" => KeyMap.VK_MEDIA_PREV_TRACK,
            "stop" => KeyMap.VK_MEDIA_STOP,
            _ => null
        };

        if (vk is null) return;

        var info = new VirtualKeyInfo(vk.Value, true);
        NativeMethods.SendInput(
            2,
            [CreateKeyVkInput(info, isUp: false), CreateKeyVkInput(info, isUp: true)],
            NativeMethods.INPUT.Size);
    }

    public void ReleaseAll()
    {
        List<string> buttons;
        List<VirtualKeyInfo> keys;

        lock (_stateLock)
        {
            buttons = _heldMouseButtons.ToList();
            keys = _heldKeys.ToList();
            _heldMouseButtons.Clear();
            _heldKeys.Clear();
        }

        var inputs = new List<NativeMethods.INPUT>(buttons.Count + keys.Count);
        foreach (string btn in buttons)
        {
            if (TryGetButtonFlags(btn, out _, out uint upFlag))
            {
                inputs.Add(CreateMouseFlagInput(upFlag));
            }
        }

        foreach (VirtualKeyInfo k in keys)
        {
            inputs.Add(CreateKeyVkInput(k, isUp: true));
        }

        if (inputs.Count > 0)
        {
            NativeMethods.SendInput((uint)inputs.Count, inputs.ToArray(), NativeMethods.INPUT.Size);
        }
    }

    private static bool TryGetButtonFlags(string button, out uint downFlag, out uint upFlag)
    {
        switch ((button ?? string.Empty).Trim().ToLowerInvariant())
        {
            case "left":
                downFlag = NativeMethods.MOUSEEVENTF_LEFTDOWN;
                upFlag = NativeMethods.MOUSEEVENTF_LEFTUP;
                return true;
            case "right":
                downFlag = NativeMethods.MOUSEEVENTF_RIGHTDOWN;
                upFlag = NativeMethods.MOUSEEVENTF_RIGHTUP;
                return true;
            case "middle":
                downFlag = NativeMethods.MOUSEEVENTF_MIDDLEDOWN;
                upFlag = NativeMethods.MOUSEEVENTF_MIDDLEUP;
                return true;
            default:
                downFlag = 0;
                upFlag = 0;
                return false;
        }
    }

    private static NativeMethods.INPUT CreateMouseFlagInput(uint flag) =>
        new()
        {
            type = NativeMethods.INPUT_MOUSE,
            U = new NativeMethods.InputUnion
            {
                mi = new NativeMethods.MOUSEINPUT { dwFlags = flag }
            }
        };

    private static NativeMethods.INPUT CreateKeyVkInput(VirtualKeyInfo info, bool isUp)
    {
        uint flags = 0;
        if (info.IsExtended) flags |= NativeMethods.KEYEVENTF_EXTENDEDKEY;
        if (isUp) flags |= NativeMethods.KEYEVENTF_KEYUP;

        ushort scan = (ushort)NativeMethods.MapVirtualKeyW(info.Vk, 0);

        return new NativeMethods.INPUT
        {
            type = NativeMethods.INPUT_KEYBOARD,
            U = new NativeMethods.InputUnion
            {
                ki = new NativeMethods.KEYBDINPUT
                {
                    wVk = info.Vk,
                    wScan = scan,
                    dwFlags = flags
                }
            }
        };
    }

    private static NativeMethods.INPUT CreateUnicodeInput(char utf16Unit, bool isUp)
    {
        uint flags = NativeMethods.KEYEVENTF_UNICODE;
        if (isUp) flags |= NativeMethods.KEYEVENTF_KEYUP;

        return new NativeMethods.INPUT
        {
            type = NativeMethods.INPUT_KEYBOARD,
            U = new NativeMethods.InputUnion
            {
                ki = new NativeMethods.KEYBDINPUT
                {
                    wVk = 0,
                    wScan = utf16Unit,
                    dwFlags = flags
                }
            }
        };
    }
}
