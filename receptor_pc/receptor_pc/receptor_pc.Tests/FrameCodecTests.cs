using System.IO;
using receptor_pc.Core.Protocol;
using Xunit;

namespace receptor_pc.Tests;

public class FrameCodecTests
{
    [Fact]
    public void Vector1_MouseMove_5_Minus3_MatchesSpecBytes()
    {
        byte[] encoded = FrameCodec.EncodeMouseMove(5, -3);
        byte[] expected = Convert.FromHexString("1000040005FFFD");
        Assert.Equal(expected, encoded);

        var (dx, dy) = FrameCodec.DecodePairInt16(encoded.AsSpan(FrameCodec.HeaderSize));
        Assert.Equal(5, dx);
        Assert.Equal(-3, dy);
    }

    [Fact]
    public void Vector2_Scroll_0_Minus120_MatchesSpecBytes()
    {
        byte[] encoded = FrameCodec.EncodeScroll(0, -120);
        byte[] expected = Convert.FromHexString("1100040000FF88");
        Assert.Equal(expected, encoded);

        var (dx, dy) = FrameCodec.DecodePairInt16(encoded.AsSpan(FrameCodec.HeaderSize));
        Assert.Equal(0, dx);
        Assert.Equal(-120, dy);
    }

    [Fact]
    public void Vector3_Zoom_1_MatchesSpecBytes()
    {
        byte[] encoded = FrameCodec.EncodeZoom(1);
        byte[] expected = Convert.FromHexString("1200020001");
        Assert.Equal(expected, encoded);

        short steps = FrameCodec.DecodeSingleInt16(encoded.AsSpan(FrameCodec.HeaderSize));
        Assert.Equal(1, steps);
    }

    [Fact]
    public void Vector4_JsonPing_MatchesSpecBytes()
    {
        byte[] encoded = FrameCodec.EncodeJson(new PingMessage(1));
        byte[] expected = Convert.FromHexString("0100137B2274223A2270696E67222C227473223A317D");
        Assert.Equal(expected, encoded);

        ProtocolMessage decoded = FrameCodec.DecodeJson(encoded.AsSpan(FrameCodec.HeaderSize));
        var ping = Assert.IsType<PingMessage>(decoded);
        Assert.Equal(1L, ping.Ts);
    }

    [Fact]
    public async Task AllProtocolMessages_RoundtripOverStream()
    {
        ProtocolMessage[] samples =
        [
            new HelloMessage(1, "dev-123", "Pixel 8", "android", "1.0"),
            new HelloAckMessage(1, "srv-abc", "PC-KEVIN", true),
            new AuthMessage("dev-123", "tok_base64url"),
            new PairRequestMessage("dev-123", "Pixel 8", "483920"),
            new AuthOkMessage("s-01", "PC-KEVIN"),
            new PairOkMessage("tok_base64url", "srv-abc", "PC-KEVIN"),
            new AuthFailMessage("revoked"),
            new PairFailMessage("bad_pin", 3, 0),
            new PingMessage(1732231200000L),
            new PongMessage(1732231200000L),
            new ByeMessage("server_shutdown"),
            new ErrorMessage("bad_message", "Invalid payload"),
            new ClickMessage("left", 2),
            new ButtonMessage("left", "down"),
            new TextMessage("Hola ñandú 😀"),
            new KeyMessage("ENTER", "press"),
            new ComboMessage(["CTRL", "SHIFT", "ESC"]),
            new MediaMessage("vol_up"),
            new SystemMessage("lock", true),
            new DiscoverMessage(1),
            new DiscoverReplyMessage(1, "srv-abc", "PC-KEVIN", "192.168.1.20", 47800, "fp_abc", true)
        ];

        using var ms = new MemoryStream();
        foreach (var msg in samples)
        {
            byte[] frame = FrameCodec.EncodeJson(msg);
            await FrameCodec.WriteFrameAsync(ms, frame);
        }

        ms.Position = 0;

        foreach (var expected in samples)
        {
            RawFrame? raw = await FrameCodec.ReadFrameAsync(ms);
            Assert.NotNull(raw);
            Assert.Equal(FrameType.Json, raw.Value.KnownType);

            ProtocolMessage actual = FrameCodec.DecodeJson(raw.Value.Payload);
            Assert.Equal(expected.GetType(), actual.GetType());

            if (expected is ComboMessage expectedCombo && actual is ComboMessage actualCombo)
            {
                Assert.Equal(expectedCombo.Keys, actualCombo.Keys);
            }
            else
            {
                Assert.Equal(expected, actual);
            }
        }

        Assert.Null(await FrameCodec.ReadFrameAsync(ms));
    }

    [Fact]
    public async Task OversizedPayload_ThrowsInvalidDataException()
    {
        byte[] oversized = new byte[ProtocolConstants.MaxFramePayload + 1];
        Assert.Throws<InvalidDataException>(() => FrameCodec.EncodeRaw((byte)FrameType.Json, oversized));

        // Craft header claiming length = MaxFramePayload + 1 (0x4001)
        byte[] bogusStream = [0x01, 0x40, 0x01, 0x00];
        using var ms = new MemoryStream(bogusStream);
        await Assert.ThrowsAsync<InvalidDataException>(async () => await FrameCodec.ReadFrameAsync(ms));
    }

    [Fact]
    public async Task UnknownFrameType_IsReturnedWithoutThrowing()
    {
        byte[] raw = FrameCodec.EncodeRaw(0xFE, [0xAA, 0xBB]);
        using var ms = new MemoryStream(raw);
        RawFrame? read = await FrameCodec.ReadFrameAsync(ms);
        Assert.NotNull(read);
        Assert.Equal(0xFE, read.Value.Type);
        Assert.Null(read.Value.KnownType);
        Assert.Equal(new byte[] { 0xAA, 0xBB }, read.Value.Payload);
    }
}
