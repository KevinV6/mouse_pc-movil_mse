using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace receptor_pc.Core.Security;

public sealed record TrustedDevice(
    [property: JsonPropertyName("deviceId")] string DeviceId,
    [property: JsonPropertyName("deviceName")] string DeviceName,
    [property: JsonPropertyName("tokenHash")] string TokenHash,
    [property: JsonPropertyName("pairedAt")] DateTimeOffset PairedAt,
    [property: JsonPropertyName("lastSeen")] DateTimeOffset LastSeen
);

public sealed class TrustedDeviceStore
{
    private readonly string _filePath;
    private readonly object _sync = new();
    private readonly Dictionary<string, TrustedDevice> _devices = new(StringComparer.Ordinal);
    private readonly HashSet<string> _revokedIds = new(StringComparer.Ordinal);

    public event EventHandler? Changed;

    public TrustedDeviceStore(string? storageDirectory = null)
    {
        string baseDir = storageDirectory
            ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "MseReceptor");
        Directory.CreateDirectory(baseDir);
        _filePath = Path.Combine(baseDir, "trusted_devices.bin");
        Load();
    }

    public IReadOnlyList<TrustedDevice> GetAll()
    {
        lock (_sync)
        {
            return _devices.Values.OrderByDescending(d => d.LastSeen).ToList();
        }
    }

    public TrustedDevice? Get(string deviceId)
    {
        lock (_sync)
        {
            return _devices.TryGetValue(deviceId, out var dev) ? dev : null;
        }
    }

    public bool WasRevoked(string deviceId)
    {
        lock (_sync)
        {
            return _revokedIds.Contains(deviceId);
        }
    }

    public void AddOrUpdate(string deviceId, string deviceName, string rawToken)
    {
        string tokenHash = CryptoUtils.Sha256Base64UrlUtf8(rawToken);
        DateTimeOffset now = DateTimeOffset.UtcNow;

        lock (_sync)
        {
            DateTimeOffset pairedAt = _devices.TryGetValue(deviceId, out var existing)
                ? existing.PairedAt
                : now;

            _devices[deviceId] = new TrustedDevice(deviceId, deviceName, tokenHash, pairedAt, now);
            _revokedIds.Remove(deviceId);
            SaveInternal();
        }

        Changed?.Invoke(this, EventArgs.Empty);
    }

    public bool ValidateToken(string deviceId, string rawToken, out string failReason)
    {
        string candidateHash = CryptoUtils.Sha256Base64UrlUtf8(rawToken);
        bool valid = false;

        lock (_sync)
        {
            if (!_devices.TryGetValue(deviceId, out var device))
            {
                failReason = _revokedIds.Contains(deviceId) ? "revoked" : "unknown_device";
                return false;
            }

            if (!CryptoUtils.FixedTimeEqualsUtf8(device.TokenHash, candidateHash))
            {
                failReason = "bad_token";
                return false;
            }

            _devices[deviceId] = device with { LastSeen = DateTimeOffset.UtcNow };
            SaveInternal();
            valid = true;
            failReason = string.Empty;
        }

        if (valid)
        {
            Changed?.Invoke(this, EventArgs.Empty);
        }
        return valid;
    }

    public bool Revoke(string deviceId)
    {
        bool removed;
        lock (_sync)
        {
            removed = _devices.Remove(deviceId);
            if (removed)
            {
                _revokedIds.Add(deviceId);
                SaveInternal();
            }
        }

        if (removed)
        {
            Changed?.Invoke(this, EventArgs.Empty);
        }
        return removed;
    }

    private void Load()
    {
        if (!File.Exists(_filePath))
        {
            return;
        }

        try
        {
            byte[] encrypted = File.ReadAllBytes(_filePath);
            byte[] plain = DpapiProtector.Unprotect(encrypted);
            var payload = JsonSerializer.Deserialize<PersistedStorePayload>(plain);
            if (payload is null)
            {
                return;
            }

            _devices.Clear();
            foreach (var d in payload.Devices ?? [])
            {
                _devices[d.DeviceId] = d;
            }

            _revokedIds.Clear();
            foreach (var r in payload.RevokedIds ?? [])
            {
                _revokedIds.Add(r);
            }
        }
        catch
        {
            // Ignore corrupted file
        }
    }

    private void SaveInternal()
    {
        try
        {
            var payload = new PersistedStorePayload(
                _devices.Values.ToList(),
                _revokedIds.ToList());
            byte[] plain = JsonSerializer.SerializeToUtf8Bytes(payload);
            byte[] encrypted = DpapiProtector.Protect(plain);
            File.WriteAllBytes(_filePath, encrypted);
        }
        catch
        {
            // Ignore write errors
        }
    }

    private sealed record PersistedStorePayload(
        List<TrustedDevice>? Devices,
        List<string>? RevokedIds);
}
