using System.Security.Cryptography;
using System.Text;

namespace receptor_pc.Core.Security;

public static class DpapiProtector
{
    private static readonly byte[] AppEntropy = Encoding.UTF8.GetBytes("MseReceptor_v1_DpapiEntropy");

    public static byte[] Protect(byte[] plaintext)
    {
        return ProtectedData.Protect(plaintext, AppEntropy, DataProtectionScope.CurrentUser);
    }

    public static byte[] Unprotect(byte[] ciphertext)
    {
        return ProtectedData.Unprotect(ciphertext, AppEntropy, DataProtectionScope.CurrentUser);
    }
}
