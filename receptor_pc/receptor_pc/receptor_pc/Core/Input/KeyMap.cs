namespace receptor_pc.Core.Input;

public readonly record struct VirtualKeyInfo(ushort Vk, bool IsExtended = false);

public static class KeyMap
{
    // Virtual-Key codes (WinUser.h)
    public const ushort VK_BACK = 0x08;
    public const ushort VK_TAB = 0x09;
    public const ushort VK_RETURN = 0x0D;
    public const ushort VK_SHIFT = 0x10;
    public const ushort VK_CONTROL = 0x11;
    public const ushort VK_MENU = 0x12; // ALT
    public const ushort VK_CAPITAL = 0x14; // CAPS_LOCK
    public const ushort VK_ESCAPE = 0x1B;
    public const ushort VK_SPACE = 0x20;
    public const ushort VK_PRIOR = 0x21; // PAGE_UP
    public const ushort VK_NEXT = 0x22;  // PAGE_DOWN
    public const ushort VK_END = 0x23;
    public const ushort VK_HOME = 0x24;
    public const ushort VK_LEFT = 0x25;
    public const ushort VK_UP = 0x26;
    public const ushort VK_RIGHT = 0x27;
    public const ushort VK_DOWN = 0x28;
    public const ushort VK_SNAPSHOT = 0x2C; // PRINT_SCREEN
    public const ushort VK_INSERT = 0x2D;
    public const ushort VK_DELETE = 0x2E;
    public const ushort VK_LWIN = 0x5B;
    public const ushort VK_APPS = 0x5D; // CONTEXT_MENU

    public const ushort VK_VOLUME_MUTE = 0xAD;
    public const ushort VK_VOLUME_DOWN = 0xAE;
    public const ushort VK_VOLUME_UP = 0xAF;
    public const ushort VK_MEDIA_NEXT_TRACK = 0xB0;
    public const ushort VK_MEDIA_PREV_TRACK = 0xB1;
    public const ushort VK_MEDIA_STOP = 0xB2;
    public const ushort VK_MEDIA_PLAY_PAUSE = 0xB3;

    private static readonly Dictionary<string, VirtualKeyInfo> Map = BuildMap();

    public static IReadOnlyDictionary<string, VirtualKeyInfo> All => Map;

    public static bool TryResolve(string keyName, out VirtualKeyInfo info)
    {
        if (string.IsNullOrWhiteSpace(keyName))
        {
            info = default;
            return false;
        }
        return Map.TryGetValue(keyName.Trim().ToUpperInvariant(), out info);
    }

    private static Dictionary<string, VirtualKeyInfo> BuildMap()
    {
        var dict = new Dictionary<string, VirtualKeyInfo>(StringComparer.OrdinalIgnoreCase)
        {
            // Modificadores
            ["CTRL"] = new(VK_CONTROL, false),
            ["ALT"] = new(VK_MENU, false),
            ["SHIFT"] = new(VK_SHIFT, false),
            ["WIN"] = new(VK_LWIN, true),

            // Edición
            ["ENTER"] = new(VK_RETURN, false),
            ["BACKSPACE"] = new(VK_BACK, false),
            ["DELETE"] = new(VK_DELETE, true),
            ["TAB"] = new(VK_TAB, false),
            ["ESC"] = new(VK_ESCAPE, false),
            ["SPACE"] = new(VK_SPACE, false),
            ["INSERT"] = new(VK_INSERT, true),

            // Navegación (extended)
            ["UP"] = new(VK_UP, true),
            ["DOWN"] = new(VK_DOWN, true),
            ["LEFT"] = new(VK_LEFT, true),
            ["RIGHT"] = new(VK_RIGHT, true),
            ["HOME"] = new(VK_HOME, true),
            ["END"] = new(VK_END, true),
            ["PAGE_UP"] = new(VK_PRIOR, true),
            ["PAGE_DOWN"] = new(VK_NEXT, true),

            // Otras
            ["PRINT_SCREEN"] = new(VK_SNAPSHOT, true),
            ["CAPS_LOCK"] = new(VK_CAPITAL, false),
            ["CONTEXT_MENU"] = new(VK_APPS, true)
        };

        // F1 .. F12 (0x70 .. 0x7B)
        for (int i = 1; i <= 12; i++)
        {
            dict[$"F{i}"] = new((ushort)(0x70 + i - 1), false);
        }

        // A .. Z (0x41 .. 0x5A)
        for (char c = 'A'; c <= 'Z'; c++)
        {
            dict[c.ToString()] = new((ushort)c, false);
        }

        // 0 .. 9 (0x30 .. 0x39)
        for (char c = '0'; c <= '9'; c++)
        {
            dict[c.ToString()] = new((ushort)c, false);
        }

        return dict;
    }
}
