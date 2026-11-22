namespace receptor_pc.Services;

public sealed class SingleInstanceGuard : IDisposable
{
    private const string MutexName = @"Local\MseReceptor_SingleInstance_Mutex";
    private const string EventName = @"Local\MseReceptor_ActivatePopup_Event";

    private readonly Mutex _mutex;
    private readonly EventWaitHandle _activateEvent;
    private readonly CancellationTokenSource _cts = new();
    private Task? _listenTask;

    public bool IsFirstInstance { get; }
    public event EventHandler? SecondInstanceSignaled;

    public SingleInstanceGuard()
    {
        _mutex = new Mutex(true, MutexName, out bool createdNew);
        IsFirstInstance = createdNew;
        _activateEvent = new EventWaitHandle(false, EventResetMode.AutoReset, EventName);

        if (IsFirstInstance)
        {
            _listenTask = Task.Run(() => ListenLoop(_cts.Token));
        }
    }

    public void SignalFirstInstance()
    {
        try
        {
            _activateEvent.Set();
        }
        catch
        {
            // Ignore
        }
    }

    private void ListenLoop(CancellationToken ct)
    {
        WaitHandle[] handles = [_activateEvent, ct.WaitHandle];
        while (!ct.IsCancellationRequested)
        {
            int index = WaitHandle.WaitAny(handles);
            if (index == 0 && !ct.IsCancellationRequested)
            {
                SecondInstanceSignaled?.Invoke(this, EventArgs.Empty);
            }
            else
            {
                break;
            }
        }
    }

    public void Dispose()
    {
        _cts.Cancel();
        try { _listenTask?.Wait(200); } catch { }
        try
        {
            if (IsFirstInstance)
            {
                _mutex.ReleaseMutex();
            }
        }
        catch
        {
            // Ignore
        }
        _mutex.Dispose();
        _activateEvent.Dispose();
        _cts.Dispose();
    }
}
