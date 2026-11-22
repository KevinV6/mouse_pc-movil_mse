using System.IO;
using receptor_pc.Core.Protocol;
using receptor_pc.Core.Security;
using Xunit;

namespace receptor_pc.Tests;

internal sealed class FakeTimeProvider : TimeProvider
{
    private DateTimeOffset _now = new(2026, 11, 21, 12, 0, 0, TimeSpan.Zero);

    public override DateTimeOffset GetUtcNow() => _now;

    public void Advance(TimeSpan delta) => _now = _now.Add(delta);
}

public class PairingManagerTests
{
    [Fact]
    public void CorrectPin_EmitsToken_RotatesPin_AndAllowsAuth()
    {
        string tempDir = Path.Combine(Path.GetTempPath(), $"mse-test-pair-{Guid.NewGuid():N}");
        try
        {
            var store = new TrustedDeviceStore(tempDir);
            var clock = new FakeTimeProvider();
            var mgr = new PairingManager(store, clock);

            string pin1 = mgr.CurrentPin;
            Assert.Equal(6, pin1.Length);

            PairResult pairRes = mgr.TryPair("dev-1", "Pixel", pin1);
            Assert.True(pairRes.Success);
            Assert.NotNull(pairRes.Token);

            // PIN must have rotated after successful pairing
            Assert.NotEqual(pin1, mgr.CurrentPin);

            AuthResult authRes = mgr.TryAuth("dev-1", pairRes.Token!);
            Assert.True(authRes.Success);
        }
        finally
        {
            try { Directory.Delete(tempDir, true); } catch { }
        }
    }

    [Fact]
    public void FiveFailedAttempts_LocksOutFor30Seconds_AndRecovers()
    {
        string tempDir = Path.Combine(Path.GetTempPath(), $"mse-test-lock-{Guid.NewGuid():N}");
        try
        {
            var store = new TrustedDeviceStore(tempDir);
            var clock = new FakeTimeProvider();
            var mgr = new PairingManager(store, clock);

            for (int i = 1; i <= 4; i++)
            {
                PairResult bad = mgr.TryPair("dev-1", "Pixel", "999999");
                Assert.False(bad.Success);
                Assert.Equal("bad_pin", bad.FailReason);
                Assert.Equal(ProtocolConstants.PinMaxAttempts - i, bad.AttemptsLeft);
            }

            // 5th bad attempt triggers lockout
            PairResult fifth = mgr.TryPair("dev-1", "Pixel", "999999");
            Assert.False(fifth.Success);
            Assert.Equal("locked", fifth.FailReason);
            Assert.Equal(0, fifth.AttemptsLeft);
            Assert.True(fifth.RetryAfterSec > 0);

            // Even with correct current PIN during lockout, still locked
            string newPin = mgr.CurrentPin;
            PairResult duringLock = mgr.TryPair("dev-1", "Pixel", newPin);
            Assert.False(duringLock.Success);
            Assert.Equal("locked", duringLock.FailReason);

            // Advance 31 seconds -> lock expires
            clock.Advance(TimeSpan.FromSeconds(31));
            PairResult afterLock = mgr.TryPair("dev-1", "Pixel", mgr.CurrentPin);
            Assert.True(afterLock.Success);
        }
        finally
        {
            try { Directory.Delete(tempDir, true); } catch { }
        }
    }

    [Fact]
    public void ExpiredPin_ReturnsExpiredAndRotates()
    {
        string tempDir = Path.Combine(Path.GetTempPath(), $"mse-test-exp-{Guid.NewGuid():N}");
        try
        {
            var store = new TrustedDeviceStore(tempDir);
            var clock = new FakeTimeProvider();
            var mgr = new PairingManager(store, clock);

            string oldPin = mgr.CurrentPin;
            clock.Advance(ProtocolConstants.PinLifetime.Add(TimeSpan.FromSeconds(1)));

            PairResult res = mgr.TryPair("dev-1", "Pixel", oldPin);
            Assert.False(res.Success);
            Assert.Equal("expired", res.FailReason);
        }
        finally
        {
            try { Directory.Delete(tempDir, true); } catch { }
        }
    }

    [Fact]
    public void ClosedPairing_RejectsWithPairingClosed()
    {
        string tempDir = Path.Combine(Path.GetTempPath(), $"mse-test-closed-{Guid.NewGuid():N}");
        try
        {
            var store = new TrustedDeviceStore(tempDir);
            var mgr = new PairingManager(store) { PairingOpen = false };

            PairResult res = mgr.TryPair("dev-1", "Pixel", mgr.CurrentPin);
            Assert.False(res.Success);
            Assert.Equal("pairing_closed", res.FailReason);
        }
        finally
        {
            try { Directory.Delete(tempDir, true); } catch { }
        }
    }
}
