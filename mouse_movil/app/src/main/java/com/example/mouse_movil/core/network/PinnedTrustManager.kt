package com.example.mouse_movil.core.network

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Base64
import javax.net.ssl.X509TrustManager

class CertificatePinMismatchException(
    val expectedFp: String,
    val actualFp: String
) : CertificateException("La identidad criptográfica de la PC cambió (esperada=$expectedFp, actual=$actualFp).")

class PinnedTrustManager(
    private val expectedFingerprint: String? = null
) : X509TrustManager {

    @Volatile
    var observedFingerprint: String = ""
        private set

    val observedVerificationCode: String
        get() = formatVerificationCode(observedFingerprint)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Client certificates not supported.")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) {
            throw CertificateException("Empty server certificate chain.")
        }
        val leaf = chain[0]
        val actualFp = computeFingerprintBase64Url(leaf.encoded)
        observedFingerprint = actualFp

        val expected = expectedFingerprint?.trim().orEmpty()
        if (expected.isNotEmpty() && !constantTimeEquals(expected, actualFp)) {
            throw CertificatePinMismatchException(expected, actualFp)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        fun computeFingerprintBase64Url(derBytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(derBytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        }

        fun formatVerificationCode(fp: String): String {
            val clean = fp.uppercase().replace("-", "").replace("_", "").padEnd(8, '0')
            return "${clean.substring(0, 4)}-${clean.substring(4, 8)}"
        }

        private fun constantTimeEquals(a: String, b: String): Boolean {
            val bytesA = a.toByteArray(Charsets.UTF_8)
            val bytesB = b.toByteArray(Charsets.UTF_8)
            return MessageDigest.isEqual(bytesA, bytesB)
        }
    }
}
