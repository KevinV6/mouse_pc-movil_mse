using System.IO;
using System.Text.Json;

namespace receptor_pc.Core.Settings;

public sealed class SettingsStore
{
    private readonly string _filePath;
    private readonly object _sync = new();
    private AppSettings _current;

    public event EventHandler<AppSettings>? Changed;

    public SettingsStore(string? storageDirectory = null)
    {
        string baseDir = storageDirectory
            ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "MseReceptor");
        Directory.CreateDirectory(baseDir);
        _filePath = Path.Combine(baseDir, "settings.json");
        _current = LoadInternal();
    }

    public AppSettings Current
    {
        get
        {
            lock (_sync)
            {
                return _current;
            }
        }
    }

    public void Update(Func<AppSettings, AppSettings> updater)
    {
        AppSettings updated;
        lock (_sync)
        {
            updated = updater(_current);
            _current = updated;
            SaveInternal(updated);
        }
        Changed?.Invoke(this, updated);
    }

    private AppSettings LoadInternal()
    {
        if (!File.Exists(_filePath))
        {
            var defaults = new AppSettings();
            SaveInternal(defaults);
            return defaults;
        }

        try
        {
            string json = File.ReadAllText(_filePath);
            return JsonSerializer.Deserialize<AppSettings>(json) ?? new AppSettings();
        }
        catch
        {
            return new AppSettings();
        }
    }

    private void SaveInternal(AppSettings settings)
    {
        try
        {
            string json = JsonSerializer.Serialize(settings, new JsonSerializerOptions { WriteIndented = true });
            File.WriteAllText(_filePath, json);
        }
        catch
        {
            // Ignore write errors
        }
    }
}
