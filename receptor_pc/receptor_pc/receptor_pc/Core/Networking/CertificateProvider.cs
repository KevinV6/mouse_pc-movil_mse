using System.IO;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using receptor_pc.Core.Security;

namespace receptor_pc.Core.Networking;

public sealed class CertificateProvider : IDisposable
{
    private readonly string _certFilePath;

    public X509Certificate2 Certificate { get; }
    public string Fingerprint { get; }
    public string ServerId { get; }
    public string VerificationCode { get; }

    public CertificateProvider(string? storageDirectory = null)
    {
        string baseDir = storageDirectory
            ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "MseReceptor");
        Directory.CreateDirectory(baseDir);

        _certFilePath = Path.Combine(baseDir, "server_cert.bin");
        Certificate = LoadOrCreateCertificate();
        Fingerprint = CryptoUtils.Sha256Base64Url(Certificate.RawData);
        ServerId = Fingerprint.Length >= 16 ? Fingerprint[..16] : Fingerprint;

        string upper = Fingerprint.ToUpperInvariant().Replace("-", "").Replace("_", "");
        if (upper.Length < 8)
        {
            upper = upper.PadRight(8, '0');
        }
        VerificationCode = $"{upper[..4]}-{upper.Substring(4, 4)}";
    }

    private X509Certificate2 LoadOrCreateCertificate()
    {
        if (File.Exists(_certFilePath))
        {
            try
            {
                byte[] encrypted = File.ReadAllBytes(_certFilePath);
                byte[] pfxBytes = DpapiProtector.Unprotect(encrypted);
                var loaded = new X509Certificate2(
                    pfxBytes,
                    string.Empty,
                    X509KeyStorageFlags.Exportable | X509KeyStorageFlags.UserKeySet);

                if (loaded.NotAfter > DateTime.UtcNow.AddDays(7) && loaded.HasPrivateKey)
                {
                    return loaded;
                }
                loaded.Dispose();
            }
            catch
            {
                // Regenerate on corruption or key mismatch
            }
        }

        using var ecdsa = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var subject = new X500DistinguishedName($"CN=MSE Receptor {Environment.MachineName}");
        var request = new CertificateRequest(subject, ecdsa, HashAlgorithmName.SHA256);

        request.CertificateExtensions.Add(
            new X509BasicConstraintsExtension(false, false, 0, false));
        request.CertificateExtensions.Add(
            new X509KeyUsageExtension(
                X509KeyUsageFlags.DigitalSignature | X509KeyUsageFlags.KeyEncipherment,
                false));
        request.CertificateExtensions.Add(
            new X509EnhancedKeyUsageExtension(
                [new Oid("1.3.6.1.5.5.7.3.1")], // Server Authentication
                false));

        DateTimeOffset notBefore = DateTimeOffset.UtcNow.AddMinutes(-5);
        DateTimeOffset notAfter = DateTimeOffset.UtcNow.AddYears(10);

        using X509Certificate2 ephemeral = request.CreateSelfSigned(notBefore, notAfter);
        byte[] exportedPfx = ephemeral.Export(X509ContentType.Pfx, string.Empty);

        try
        {
            byte[] protectedBytes = DpapiProtector.Protect(exportedPfx);
            File.WriteAllBytes(_certFilePath, protectedBytes);
        }
        catch
        {
            // Ignore write errors in restricted test environments
        }

        // Re-import from PFX so Windows SChannel (SslStream) has a persistent handle to the private key
        return new X509Certificate2(
            exportedPfx,
            string.Empty,
            X509KeyStorageFlags.Exportable | X509KeyStorageFlags.UserKeySet);
    }

    public void Dispose()
    {
        Certificate.Dispose();
    }
}
