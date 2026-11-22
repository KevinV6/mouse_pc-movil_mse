using System.Windows;
using receptor_pc.Views;

namespace receptor_pc;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);

        var shellWindow = new ShellWindow();
        shellWindow.Closed += (_, _) => Shutdown();
        shellWindow.Show();
    }
}
