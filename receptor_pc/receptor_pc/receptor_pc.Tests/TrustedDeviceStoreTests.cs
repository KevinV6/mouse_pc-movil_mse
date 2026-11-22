using System.IO;
using System.Text;
using receptor_pc.Core.Security;
using Xunit;

namespace receptor_pc.Tests;

public class TrustedDeviceStoreTests
{
    [Fact]
    public void AddValidateRevoke_PersistsAcrossInstances_AndDoesNotStoreRawToken()
    {
        string tempDir = Path.Combine(Path.GetTempPath(), $"mse-test-store-{Guid.NewGuid():N}");
        Directory.CreateDirectory(tempDir);

        try
        {
            const string rawToken = "SUPER_SECRET_TOKEN_VALUE_1234567890";
            var store1 = new TrustedDeviceStore(tempDir);
            store1.AddOrUpdate("dev-01", "Pixel 8 Pro", rawToken);

            Assert.True(store1.ValidateToken("dev-01", rawToken, out string reason1));
            Assert.Equal(string.Empty, reason1);

            Assert.False(store1.ValidateToken("dev-01", "wrong-token", out string reasonBad));
            Assert.Equal("bad_token", reasonBad);

            // Inspect raw binary file on disk: must NOT contain rawToken
            string binFile = Path.Combine(tempDir, "trusted_devices.bin");
            Assert.True(File.Exists(binFile));
            string rawDiskText = Encoding.UTF8.GetString(File.ReadAllBytes(binFile));
            Assert.DoesNotContain(rawToken, rawDiskText);

            // Reload from a second instance
            var store2 = new TrustedDeviceStore(tempDir);
            Assert.Single(store2.GetAll());
            Assert.True(store2.ValidateToken("dev-01", rawToken, out _));

            // Revoke and check reason
            Assert.True(store2.Revoke("dev-01"));
            Assert.False(store2.ValidateToken("dev-01", rawToken, out string revokedReason));
            Assert.Equal("revoked", revokedReason);

            // Unknown device
            Assert.False(store2.ValidateToken("unknown-99", rawToken, out string unknownReason));
            Assert.Equal("unknown_device", unknownReason);
        }
        finally
        {
            try { Directory.Delete(tempDir, true); } catch { }
        }
    }
}
