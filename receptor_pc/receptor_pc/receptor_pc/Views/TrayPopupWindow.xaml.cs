using System.Windows;
using System.Windows.Media.Animation;
using receptor_pc.ViewModels;
using Wpf.Ui.Appearance;
using Wpf.Ui.Controls;

namespace receptor_pc.Views;

public partial class TrayPopupWindow : FluentWindow
{
    private readonly TrayPopupViewModel _viewModel;
    public bool AllowFullClose { get; set; }

    public TrayPopupWindow(TrayPopupViewModel viewModel)
    {
        InitializeComponent();
        SystemThemeWatcher.Watch(this);
        _viewModel = viewModel;
        DataContext = _viewModel;
    }

    public void ShowNearTray()
    {
        _viewModel.RefreshAll();

        Rect workArea = SystemParameters.WorkArea;
        Left = Math.Max(workArea.Left + 8, workArea.Right - Width - 12);
        Top = Math.Max(workArea.Top + 8, workArea.Bottom - Height - 12);

        Opacity = 0;
        Show();
        Activate();
        Focus();

        var fadeIn = new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(150))
        {
            EasingFunction = new QuadraticEase { EasingMode = EasingMode.EaseOut }
        };
        BeginAnimation(OpacityProperty, fadeIn);
    }

    public void ToggleNearTray()
    {
        if (IsVisible)
        {
            Hide();
        }
        else
        {
            ShowNearTray();
        }
    }

    private void OnWindowDeactivated(object? sender, EventArgs e)
    {
        if (!AllowFullClose && IsVisible)
        {
            Hide();
        }
    }

    protected override void OnClosing(System.ComponentModel.CancelEventArgs e)
    {
        if (!AllowFullClose)
        {
            e.Cancel = true;
            Hide();
            return;
        }
        base.OnClosing(e);
    }
}
