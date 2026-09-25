using System.Collections.Specialized;
using System.ComponentModel;
using System.Windows;
using System.Windows.Input;
using VoidRay.ViewModels;

namespace VoidRay;

public partial class MainWindow : Window
{
    public MainWindow()
    {
        InitializeComponent();
        StateChanged += (_, _) => ApplyWindowState();
        ((INotifyCollectionChanged)LogList.Items).CollectionChanged += (_, _) =>
        {
            if (LogList.Items.Count > 0)
                LogList.ScrollIntoView(LogList.Items[^1]);
        };
    }

    private void ApplyWindowState()
    {
        var maximized = WindowState == WindowState.Maximized;
        // With AllowsTransparency the window would cover the taskbar: clamp to the work area.
        MaxHeight = maximized ? SystemParameters.WorkArea.Height + 12 : double.PositiveInfinity;
        Chrome.Margin = new Thickness(maximized ? 6 : 12);
        MaximizeGlyph.Text = maximized ? "" : "";
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ClickCount == 2)
            ToggleMaximize();
        else
            DragMove();
    }

    private void Minimize_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void Maximize_Click(object sender, RoutedEventArgs e) => ToggleMaximize();

    private void Close_Click(object sender, RoutedEventArgs e) => Close();

    private void ToggleMaximize() =>
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;

    protected override void OnClosing(CancelEventArgs e)
    {
        (DataContext as MainViewModel)?.Dispose();
        base.OnClosing(e);
    }
}
