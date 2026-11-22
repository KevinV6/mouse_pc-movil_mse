using receptor_pc.Core.Protocol;

namespace receptor_pc.Core.Security;

public sealed record PairResult(
    bool Success,
    string? Token,
    string? FailReason,
    int AttemptsLeft,
    int RetryAfterSec
);

public sealed record AuthResult(
    bool Success,
    string? FailReason
);

public sealed class PairingManager
{
    private readonly TrustedDeviceStore _store;
    private readonly TimeProvider _timeProvider;
    private readonly object _sync = new();

    private string _currentPin = string.Empty;
    private DateTimeOffset _pinExpiresAt;
    private DateTimeOffset _lockedUntil = DateTimeOffset.MinValue;
    private int _failedAttempts;
    private bool _pairingOpen = true;

    public event EventHandler? PinChanged;

    public PairingManager(TrustedDeviceStore store, TimeProvider? timeProvider = null, bool pairingOpen = true)
    {
        _store = store;
        _timeProvider = timeProvider ?? TimeProvider.System;
        _pairingOpen = pairingOpen;
        RotatePin();
    }

    public string CurrentPin
    {
        get
        {
            lock (_sync)
            {
                EnsurePinFreshInternal();
                return _currentPin;
            }
        }
    }

    public string CurrentPinFormatted
    {
        get
        {
            string pin = CurrentPin;
            return pin.Length == 6 ? $"{pin[..3]} {pin[3..]}" : pin;
        }
    }

    public bool PairingOpen
    {
        get
        {
            lock (_sync)
            {
                return _pairingOpen;
            }
        }
        set
        {
            bool changed = false;
            lock (_sync)
            {
                if (_pairingOpen != value)
                {
                    _pairingOpen = value;
                    changed = true;
                }
            }
            if (changed)
            {
                PinChanged?.Invoke(this, EventArgs.Empty);
            }
        }
    }

    public string RotatePin()
    {
        string pin;
        lock (_sync)
        {
            pin = RotatePinInternal();
        }
        PinChanged?.Invoke(this, EventArgs.Empty);
        return pin;
    }

    private string RotatePinInternal()
    {
        _currentPin = CryptoUtils.GenerateSixDigitPin();
        _pinExpiresAt = _timeProvider.GetUtcNow().Add(ProtocolConstants.PinLifetime);
        _failedAttempts = 0;
        return _currentPin;
    }

    private void EnsurePinFreshInternal()
    {
        DateTimeOffset now = _timeProvider.GetUtcNow();
        if (now >= _pinExpiresAt)
        {
            RotatePinInternal();
        }
    }

    public PairResult TryPair(string deviceId, string deviceName, string submittedPin)
    {
        if (string.IsNullOrWhiteSpace(deviceId))
        {
            return new PairResult(false, null, "bad_pin", 0, 0);
        }

        string cleanPin = (submittedPin ?? string.Empty).Replace(" ", "").Trim();
        bool rotated = false;
        string? issuedToken = null;
        PairResult result;

        lock (_sync)
        {
            DateTimeOffset now = _timeProvider.GetUtcNow();

            if (!_pairingOpen)
            {
                return new PairResult(false, null, "pairing_closed", ProtocolConstants.PinMaxAttempts - _failedAttempts, 0);
            }

            if (now < _lockedUntil)
            {
                int retrySec = Math.Max(1, (int)Math.Ceiling((_lockedUntil - now).TotalSeconds));
                return new PairResult(false, null, "locked", 0, retrySec);
            }

            if (now >= _pinExpiresAt)
            {
                RotatePinInternal();
                rotated = true;
                result = new PairResult(false, null, "expired", ProtocolConstants.PinMaxAttempts, 0);
            }
            else if (CryptoUtils.FixedTimeEqualsUtf8(_currentPin, cleanPin))
            {
                issuedToken = CryptoUtils.GenerateTokenBase64Url(32);
                RotatePinInternal();
                rotated = true;
                result = new PairResult(true, issuedToken, null, ProtocolConstants.PinMaxAttempts, 0);
            }
            else
            {
                _failedAttempts++;
                int attemptsLeft = Math.Max(0, ProtocolConstants.PinMaxAttempts - _failedAttempts);
                if (_failedAttempts >= ProtocolConstants.PinMaxAttempts)
                {
                    _lockedUntil = now.Add(ProtocolConstants.PinLockoutDuration);
                    RotatePinInternal();
                    rotated = true;
                    int retrySec = (int)ProtocolConstants.PinLockoutDuration.TotalSeconds;
                    result = new PairResult(false, null, "locked", 0, retrySec);
                }
                else
                {
                    result = new PairResult(false, null, "bad_pin", attemptsLeft, 0);
                }
            }
        }

        if (issuedToken is not null)
        {
            _store.AddOrUpdate(deviceId, string.IsNullOrWhiteSpace(deviceName) ? "Android" : deviceName, issuedToken);
        }

        if (rotated)
        {
            PinChanged?.Invoke(this, EventArgs.Empty);
        }

        return result;
    }

    public AuthResult TryAuth(string deviceId, string submittedToken)
    {
        if (string.IsNullOrWhiteSpace(deviceId) || string.IsNullOrWhiteSpace(submittedToken))
        {
            return new AuthResult(false, "bad_token");
        }

        if (_store.ValidateToken(deviceId, submittedToken, out string reason))
        {
            return new AuthResult(true, null);
        }

        return new AuthResult(false, reason);
    }
}
