using receptor_pc.ViewModels;
using Wpf.Ui.Appearance;
using Wpf.Ui.Controls;

namespace receptor_pc.Views;

public partial class GuideWindow : FluentWindow
{
    public GuideWindow(GuideViewModel viewModel)
    {
        InitializeComponent();
        SystemThemeWatcher.Watch(this);
        DataContext = viewModel;
    }
}
