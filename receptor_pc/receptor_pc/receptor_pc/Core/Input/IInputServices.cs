namespace receptor_pc.Core.Input;

public interface IInputSimulator
{
    void MoveRelative(short dx, short dy);
    void Click(string button, int count);
    void Button(string button, string action);
    void Scroll(short dx, short dy);
    void Zoom(short steps);
    void TypeText(string text);
    void Key(string keyName, string action);
    void Combo(IReadOnlyList<string> keys);
    void Media(string action);
    void ReleaseAll();
}

public interface ISystemActions
{
    void Execute(string action, bool confirm);
}
