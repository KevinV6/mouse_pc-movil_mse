using System.Security.Cryptography;
using System.Text;

namespace receptor_pc.Core.Security;

public static class CryptoUtils
{
    public static byte[] Sha256(ReadOnlySpan<byte> data)
    {
        return SHA256.HashData(data);
    }

    public static byte[] Sha256Utf8(string text)
    {
        return SHA256.HashData(Encoding.UTF8.GetBytes(text));
    }

    public static string Sha256Base64Url(ReadOnlySpan<byte> data)
    {
        return ToBase64Url(SHA256.HashData(data));
    }

    public static string Sha256Base64UrlUtf8(string text)
    {
        return Sha256Base64Url(Encoding.UTF8.GetBytes(text));
    }

    public static string ToBase64Url(ReadOnlySpan<byte> bytes)
    {
        string base64 = Convert.ToBase64String(bytes);
        return base64.TrimEnd('=').Replace('+', '-').Replace('/', '_');
    }

    public static byte[] FromBase64Url(string base64Url)
    {
        string padded = base64Url.Replace('-', '+').Replace('_', '/');
        switch (padded.Length % 4)
        {
            case 2:
                padded += "==";
                break;
            case 3:
                padded += "=";
                break;
        }
        return Convert.FromBase64String(padded);
    }

    public static byte[] GenerateRandomBytes(int length = 32)
    {
        byte[] bytes = new byte[length];
        RandomNumberGenerator.Fill(bytes);
        return bytes;
    }

    public static string GenerateTokenBase64Url(int byteLength = 32)
    {
        return ToBase64Url(GenerateRandomBytes(byteLength));
    }

    public static string GenerateSixDigitPin()
    {
        int value = RandomNumberGenerator.GetInt32(0, 1_000_000);
        return value.ToString("D6");
    }

    public static bool FixedTimeEqualsUtf8(string a, string b)
    {
        byte[] bytesA = Encoding.UTF8.GetBytes(a);
        byte[] bytesB = Encoding.UTF8.GetBytes(b);
        return CryptographicOperations.FixedTimeEquals(bytesA, bytesB);
    }

    public static bool FixedTimeEquals(ReadOnlySpan<byte> a, ReadOnlySpan<byte> b)
    {
        return CryptographicOperations.FixedTimeEquals(a, b);
    }
}
