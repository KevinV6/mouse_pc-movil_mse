namespace receptor_pc.Core.Protocol;

public enum FrameType : byte
{
    Json = 0x01,
    MouseMove = 0x10,
    Scroll = 0x11,
    Zoom = 0x12
}
