using receptor_pc.Core.Protocol;

namespace receptor_pc.Core.Input;

public interface ICommandSink
{
    void EnqueueMouseMove(string deviceId, short dx, short dy);
    void EnqueueScroll(string deviceId, short dx, short dy);
    void EnqueueZoom(string deviceId, short steps);
    void EnqueueJsonCommand(string deviceId, ProtocolMessage message);
    void OnSessionClosed(string deviceId);
}
