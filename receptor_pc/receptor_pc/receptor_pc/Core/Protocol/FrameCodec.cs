using System.Buffers.Binary;
using System.IO;
using System.Text.Json;

namespace receptor_pc.Core.Protocol;

public readonly record struct RawFrame(byte Type, byte[] Payload)
{
    public FrameType? KnownType => Enum.IsDefined(typeof(FrameType), Type) ? (FrameType)Type : null;
}

public static class FrameCodec
{
    public const int HeaderSize = 3;

    public static byte[] EncodeRaw(byte type, ReadOnlySpan<byte> payload)
    {
        if (payload.Length > ProtocolConstants.MaxFramePayload)
        {
            throw new InvalidDataException(
                $"Frame payload ({payload.Length} bytes) exceeds maximum allowed ({ProtocolConstants.MaxFramePayload} bytes).");
        }

        byte[] buffer = new byte[HeaderSize + payload.Length];
        buffer[0] = type;
        BinaryPrimitives.WriteUInt16BigEndian(buffer.AsSpan(1, 2), (ushort)payload.Length);
        payload.CopyTo(buffer.AsSpan(HeaderSize));
        return buffer;
    }

    public static byte[] EncodeMouseMove(short dx, short dy)
    {
        byte[] buffer = new byte[HeaderSize + 4];
        buffer[0] = (byte)FrameType.MouseMove;
        BinaryPrimitives.WriteUInt16BigEndian(buffer.AsSpan(1, 2), 4);
        BinaryPrimitives.WriteInt16BigEndian(buffer.AsSpan(3, 2), dx);
        BinaryPrimitives.WriteInt16BigEndian(buffer.AsSpan(5, 2), dy);
        return buffer;
    }

    public static byte[] EncodeScroll(short dx, short dy)
    {
        byte[] buffer = new byte[HeaderSize + 4];
        buffer[0] = (byte)FrameType.Scroll;
        BinaryPrimitives.WriteUInt16BigEndian(buffer.AsSpan(1, 2), 4);
        BinaryPrimitives.WriteInt16BigEndian(buffer.AsSpan(3, 2), dx);
        BinaryPrimitives.WriteInt16BigEndian(buffer.AsSpan(5, 2), dy);
        return buffer;
    }

    public static byte[] EncodeZoom(short steps)
    {
        byte[] buffer = new byte[HeaderSize + 2];
        buffer[0] = (byte)FrameType.Zoom;
        BinaryPrimitives.WriteUInt16BigEndian(buffer.AsSpan(1, 2), 2);
        BinaryPrimitives.WriteInt16BigEndian(buffer.AsSpan(3, 2), steps);
        return buffer;
    }

    public static byte[] EncodeJson(ProtocolMessage message)
    {
        byte[] utf8 = JsonSerializer.SerializeToUtf8Bytes(
            message,
            ProtocolJsonContext.Relaxed.ProtocolMessage);
        return EncodeRaw((byte)FrameType.Json, utf8);
    }

    public static (short Dx, short Dy) DecodePairInt16(ReadOnlySpan<byte> payload)
    {
        if (payload.Length != 4)
        {
            throw new InvalidDataException($"Expected 4-byte payload for dx/dy frame, got {payload.Length}.");
        }

        short dx = BinaryPrimitives.ReadInt16BigEndian(payload.Slice(0, 2));
        short dy = BinaryPrimitives.ReadInt16BigEndian(payload.Slice(2, 2));
        return (dx, dy);
    }

    public static short DecodeSingleInt16(ReadOnlySpan<byte> payload)
    {
        if (payload.Length != 2)
        {
            throw new InvalidDataException($"Expected 2-byte payload for int16 frame, got {payload.Length}.");
        }

        return BinaryPrimitives.ReadInt16BigEndian(payload);
    }

    public static ProtocolMessage DecodeJson(ReadOnlySpan<byte> payload)
    {
        ProtocolMessage? msg = JsonSerializer.Deserialize(
            payload,
            ProtocolJsonContext.Relaxed.ProtocolMessage);

        return msg ?? throw new JsonException("Deserialized JSON message was null.");
    }

    public static async ValueTask WriteFrameAsync(
        Stream stream,
        byte[] encodedFrame,
        CancellationToken cancellationToken = default)
    {
        await stream.WriteAsync(encodedFrame.AsMemory(), cancellationToken).ConfigureAwait(false);
        await stream.FlushAsync(cancellationToken).ConfigureAwait(false);
    }

    /// <summary>
    /// Reads the next frame from <paramref name="stream"/>.
    /// Returns <c>null</c> on clean EOF before the header begins.
    /// Throws <see cref="EndOfStreamException"/> on truncated frame or <see cref="InvalidDataException"/> if payload exceeds <see cref="ProtocolConstants.MaxFramePayload"/>.
    /// </summary>
    public static async ValueTask<RawFrame?> ReadFrameAsync(
        Stream stream,
        CancellationToken cancellationToken = default)
    {
        byte[] header = new byte[HeaderSize];
        int firstRead = await stream.ReadAsync(header.AsMemory(0, 1), cancellationToken).ConfigureAwait(false);
        if (firstRead == 0)
        {
            return null;
        }

        await ReadExactlyAsync(stream, header.AsMemory(1, 2), cancellationToken).ConfigureAwait(false);

        byte type = header[0];
        ushort length = BinaryPrimitives.ReadUInt16BigEndian(header.AsSpan(1, 2));
        if (length > ProtocolConstants.MaxFramePayload)
        {
            throw new InvalidDataException(
                $"Incoming frame payload ({length} bytes) exceeds maximum allowed ({ProtocolConstants.MaxFramePayload} bytes).");
        }

        byte[] payload = length == 0 ? [] : new byte[length];
        if (length > 0)
        {
            await ReadExactlyAsync(stream, payload.AsMemory(), cancellationToken).ConfigureAwait(false);
        }

        return new RawFrame(type, payload);
    }

    private static async ValueTask ReadExactlyAsync(
        Stream stream,
        Memory<byte> buffer,
        CancellationToken cancellationToken)
    {
        int total = 0;
        while (total < buffer.Length)
        {
            int read = await stream.ReadAsync(buffer.Slice(total), cancellationToken).ConfigureAwait(false);
            if (read == 0)
            {
                throw new EndOfStreamException("Unexpected EOF while reading frame.");
            }
            total += read;
        }
    }
}
