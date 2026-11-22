package com.example.mouse_movil.core.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class RawFrame(
    val type: Byte,
    val payload: ByteArray
) {
    val knownType: FrameType? get() = FrameType.fromCode(type)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawFrame) return false
        return type == other.type && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * type.toInt() + payload.contentHashCode()
}

object FrameCodec {
    const val HEADER_SIZE: Int = 3

    fun encodeRaw(type: Byte, payload: ByteArray): ByteArray {
        if (payload.size > ProtocolConstants.MAX_FRAME_PAYLOAD) {
            throw IOException(
                "Frame payload (${payload.size} bytes) exceeds maximum allowed (${ProtocolConstants.MAX_FRAME_PAYLOAD} bytes)."
            )
        }
        val buffer = ByteBuffer.allocate(HEADER_SIZE + payload.size).order(ByteOrder.BIG_ENDIAN)
        buffer.put(type)
        buffer.putShort(payload.size.toShort())
        buffer.put(payload)
        return buffer.array()
    }

    fun encodeMouseMove(dx: Short, dy: Short): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + 4).order(ByteOrder.BIG_ENDIAN)
        buffer.put(FrameType.MOUSE_MOVE.code)
        buffer.putShort(4)
        buffer.putShort(dx)
        buffer.putShort(dy)
        return buffer.array()
    }

    fun encodeScroll(dx: Short, dy: Short): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + 4).order(ByteOrder.BIG_ENDIAN)
        buffer.put(FrameType.SCROLL.code)
        buffer.putShort(4)
        buffer.putShort(dx)
        buffer.putShort(dy)
        return buffer.array()
    }

    fun encodeZoom(steps: Short): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE + 2).order(ByteOrder.BIG_ENDIAN)
        buffer.put(FrameType.ZOOM.code)
        buffer.putShort(2)
        buffer.putShort(steps)
        return buffer.array()
    }

    fun encodeJson(message: ProtocolMessage): ByteArray {
        val jsonBytes = ProtocolJson.encodeToString(ProtocolMessage.serializer(), message)
            .toByteArray(Charsets.UTF_8)
        return encodeRaw(FrameType.JSON.code, jsonBytes)
    }

    fun decodePairInt16(payload: ByteArray): Pair<Short, Short> {
        if (payload.size != 4) {
            throw IOException("Expected 4-byte payload for dx/dy frame, got ${payload.size}.")
        }
        val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
        return buf.short to buf.short
    }

    fun decodeSingleInt16(payload: ByteArray): Short {
        if (payload.size != 2) {
            throw IOException("Expected 2-byte payload for int16 frame, got ${payload.size}.")
        }
        return ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN).short
    }

    fun decodeJson(payload: ByteArray): ProtocolMessage {
        val text = payload.toString(Charsets.UTF_8)
        return ProtocolJson.decodeFromString(ProtocolMessage.serializer(), text)
    }

    fun writeFrame(output: OutputStream, encodedFrame: ByteArray) {
        output.write(encodedFrame)
        output.flush()
    }

    /**
     * Reads the next frame from [input].
     * Returns `null` on clean EOF before the header begins.
     * Throws [EOFException] on truncated frame or [IOException] if length exceeds [ProtocolConstants.MAX_FRAME_PAYLOAD].
     */
    fun readFrame(input: InputStream): RawFrame? {
        val first = input.read()
        if (first == -1) return null

        val dis = DataInputStream(input)
        val length = dis.readUnsignedShort()
        if (length > ProtocolConstants.MAX_FRAME_PAYLOAD) {
            throw IOException(
                "Incoming frame payload ($length bytes) exceeds maximum allowed (${ProtocolConstants.MAX_FRAME_PAYLOAD} bytes)."
            )
        }

        val payload = ByteArray(length)
        if (length > 0) {
            dis.readFully(payload)
        }
        return RawFrame(first.toByte(), payload)
    }
}
