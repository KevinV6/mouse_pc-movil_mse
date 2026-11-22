package com.example.mouse_movil

import com.example.mouse_movil.core.network.PairingUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PairingUriTest {

    @Test
    fun parse_validQrUri_extractsAllFields() {
        val raw = "mse://pair?v=1&h=192.168.1.20&p=47800&n=PC-KEVIN&fp=Q3pX_abc-123&pin=483920"
        val parsed = PairingUri.parse(raw)
        assertNotNull(parsed)
        assertEquals(1, parsed!!.version)
        assertEquals("192.168.1.20", parsed.host)
        assertEquals(47800, parsed.port)
        assertEquals("PC-KEVIN", parsed.serverName)
        assertEquals("Q3pX_abc-123", parsed.fingerprint)
        assertEquals("483920", parsed.pin)
    }

    @Test
    fun parse_urlEncodedServerName_decodesProperly() {
        val raw = "mse://pair?v=1&h=10.0.0.5&p=47800&n=Mi%20PC%20Windows&fp=fp123456&pin=009182"
        val parsed = PairingUri.parse(raw)
        assertNotNull(parsed)
        assertEquals("Mi PC Windows", parsed!!.serverName)
        assertEquals("009182", parsed.pin)
    }

    @Test
    fun parse_invalidSchemeOrPin_returnsNull() {
        assertNull(PairingUri.parse("https://example.com?h=1.2.3.4&fp=abc&pin=123456"))
        assertNull(PairingUri.parse("mse://pair?h=192.168.1.20&fp=abc&pin=12345")) // 5 digits
        assertNull(PairingUri.parse("mse://pair?h=192.168.1.20&fp=&pin=123456")) // empty fp
        assertNull(PairingUri.parse("mse://pair?h=&fp=abc&pin=123456")) // empty host
    }
}
