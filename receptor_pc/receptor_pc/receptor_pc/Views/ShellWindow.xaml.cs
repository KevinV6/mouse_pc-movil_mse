using Wpf.Ui.Appearance;
using Wpf.Ui.Controls;

namespace receptor_pc.Views;

public partial class ShellWindow : FluentWindow
{
    public ShellWindow()
    {
        InitializeComponent();
        SystemThemeWatcher.Watch(this);
    }
}
