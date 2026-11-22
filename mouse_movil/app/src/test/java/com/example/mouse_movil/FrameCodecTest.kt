package com.example.mouse_movil

import com.example.mouse_movil.core.protocol.FrameCodec
import com.example.mouse_movil.core.protocol.FrameType
import com.example.mouse_movil.core.protocol.PingMessage
import com.example.mouse_movil.core.protocol.ProtocolConstants
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class FrameCodecTest {

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        return ByteArray(clean.length / 2) { i ->
            clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    @Test
    fun vector1_mouseMove_5_minus3_matchesSpecBytes() {
        val encoded = FrameCodec.encodeMouseMove(5, -3)
        val expected = hexToBytes("10 00 04 00 05 FF FD")
        assertArrayEquals(expected, encoded)

        val payload = encoded.copyOfRange(FrameCodec.HEADER_SIZE, encoded.size)
        val (dx, dy) = FrameCodec.decodePairInt16(payload)
        assertEquals(5.toShort(), dx)
        assertEquals((-3).toShort(), dy)
    }

    @Test
    fun vector2_scroll_0_minus120_matchesSpecBytes() {
        val encoded = FrameCodec.encodeScroll(0, -120)
        val expected = hexToBytes("11 00 04 00 00 FF 88")
        assertArrayEquals(expected, encoded)

        val payload = encoded.copyOfRange(FrameCodec.HEADER_SIZE, encoded.size)
        val (dx, dy) = FrameCodec.decodePairInt16(payload)
        assertEquals(0.toShort(), dx)
        assertEquals((-120).toShort(), dy)
    }

    @Test
    fun vector3_zoom_1_matchesSpecBytes() {
        val encoded = FrameCodec.encodeZoom(1)
        val expected = hexToBytes("12 00 02 00 01")
        assertArrayEquals(expected, encoded)

        val payload = encoded.copyOfRange(FrameCodec.HEADER_SIZE, encoded.size)
        val steps = FrameCodec.decodeSingleInt16(payload)
        assertEquals(1.toShort(), steps)
    }

    @Test
    fun vector4_jsonPing_matchesSpecBytes() {
        val encoded = FrameCodec.encodeJson(PingMessage(1L))
        val expected = hexToBytes("01 00 13 7B 22 74 22 3A 22 70 69 6E 67 22 2C 22 74 73 22 3A 31 7D")
        assertArrayEquals(expected, encoded)

        val raw = FrameCodec.readFrame(ByteArrayInputStream(encoded))
        assertNotNull(raw)
        assertEquals(FrameType.JSON, raw!!.knownType)
        val decoded = FrameCodec.decodeJson(raw.payload)
        assertTrue(decoded is PingMessage)
        assertEquals(1L, (decoded as PingMessage).ts)
    }

    @Test(expected = IOException::class)
    fun encodeRaw_oversizedPayload_throwsIOException() {
        val oversized = ByteArray(ProtocolConstants.MAX_FRAME_PAYLOAD + 1)
        FrameCodec.encodeRaw(FrameType.JSON.code, oversized)
    }

    @Test(expected = IOException::class)
    fun readFrame_oversizedHeader_throwsIOException() {
        val bogus = byteArrayOf(0x01, 0x40, 0x01, 0x00)
        FrameCodec.readFrame(ByteArrayInputStream(bogus))
    }

    @Test
    fun readFrame_unknownType_isReturnedWithoutThrowing() {
        val out = ByteArrayOutputStream()
        FrameCodec.writeFrame(out, FrameCodec.encodeRaw(0xFE.toByte(), byteArrayOf(0x11, 0x22)))
        val input = ByteArrayInputStream(out.toByteArray())

        val frame = FrameCodec.readFrame(input)
        assertNotNull(frame)
        assertEquals(0xFE.toByte(), frame!!.type)
        assertNull(frame.knownType)
        assertArrayEquals(byteArrayOf(0x11, 0x22), frame.payload)
        assertNull(FrameCodec.readFrame(input))
    }
}
