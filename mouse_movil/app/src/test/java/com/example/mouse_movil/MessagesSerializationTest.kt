package com.example.mouse_movil

import com.example.mouse_movil.core.protocol.AuthFailMessage
import com.example.mouse_movil.core.protocol.AuthMessage
import com.example.mouse_movil.core.protocol.AuthOkMessage
import com.example.mouse_movil.core.protocol.ButtonMessage
import com.example.mouse_movil.core.protocol.ByeMessage
import com.example.mouse_movil.core.protocol.ClickMessage
import com.example.mouse_movil.core.protocol.ComboMessage
import com.example.mouse_movil.core.protocol.DiscoverMessage
import com.example.mouse_movil.core.protocol.DiscoverReplyMessage
import com.example.mouse_movil.core.protocol.ErrorMessage
import com.example.mouse_movil.core.protocol.FrameCodec
import com.example.mouse_movil.core.protocol.HelloAckMessage
import com.example.mouse_movil.core.protocol.HelloMessage
import com.example.mouse_movil.core.protocol.KeyMessage
import com.example.mouse_movil.core.protocol.MediaMessage
import com.example.mouse_movil.core.protocol.PairFailMessage
import com.example.mouse_movil.core.protocol.PairOkMessage
import com.example.mouse_movil.core.protocol.PairRequestMessage
import com.example.mouse_movil.core.protocol.PingMessage
import com.example.mouse_movil.core.protocol.PongMessage
import com.example.mouse_movil.core.protocol.ProtocolJson
import com.example.mouse_movil.core.protocol.ProtocolMessage
import com.example.mouse_movil.core.protocol.SystemMessage
import com.example.mouse_movil.core.protocol.TextMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class MessagesSerializationTest {

    @Test
    fun allProtocolMessages_roundtripOverStreamAndContainDiscriminator() {
        val samples = listOf<ProtocolMessage>(
            HelloMessage(1, "dev-123", "Pixel 8", "android", "1.0"),
            HelloAckMessage(1, "srv-abc", "PC-KEVIN", true),
            AuthMessage("dev-123", "tok_base64url"),
            PairRequestMessage("dev-123", "Pixel 8", "483920"),
            AuthOkMessage("s-01", "PC-KEVIN"),
            PairOkMessage("tok_base64url", "srv-abc", "PC-KEVIN"),
            AuthFailMessage("revoked"),
            PairFailMessage("bad_pin", 3, 0),
            PingMessage(1732231200000L),
            PongMessage(1732231200000L),
            ByeMessage("server_shutdown"),
            ErrorMessage("bad_message", "Invalid payload"),
            ClickMessage("left", 2),
            ButtonMessage("left", "down"),
            TextMessage("Hola ñandú 😀"),
            KeyMessage("ENTER", "press"),
            ComboMessage(listOf("CTRL", "SHIFT", "ESC")),
            MediaMessage("vol_up"),
            SystemMessage("lock", true),
            DiscoverMessage(1),
            DiscoverReplyMessage(1, "srv-abc", "PC-KEVIN", "192.168.1.20", 47800, "fp_abc", true)
        )

        val out = ByteArrayOutputStream()
        for (msg in samples) {
            val json = ProtocolJson.encodeToString(ProtocolMessage.serializer(), msg)
            assertTrue("JSON must include discriminator \"t\": $json", json.contains("\"t\":\""))
            FrameCodec.writeFrame(out, FrameCodec.encodeJson(msg))
        }

        val input = ByteArrayInputStream(out.toByteArray())
        for (expected in samples) {
            val raw = FrameCodec.readFrame(input)
            assertNotNull(raw)
            val actual = FrameCodec.decodeJson(raw!!.payload)
            assertEquals(expected, actual)
        }
        assertNull(FrameCodec.readFrame(input))
    }

    @Test
    fun unknownJsonFields_areIgnoredGracefully() {
        val payload = """{"t":"ping","ts":42,"extraField":"ignored"}""".toByteArray(Charsets.UTF_8)
        val decoded = FrameCodec.decodeJson(payload)
        assertEquals(PingMessage(42L), decoded)
    }
}
