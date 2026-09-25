using System.Collections.Specialized;
using System.ComponentModel;
using System.Windows;
using System.Windows.Media;
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
        // A maximised WindowChrome window overhangs the screen by the resize border.
        Root.Margin = maximized ? new Thickness(7) : new Thickness(0);
        MaximizeIcon.Data = (Geometry)FindResource(maximized ? "IcoRestore" : "IcoMax");
    }

    private void Minimize_Click(object sender, RoutedEventArgs e) => WindowState = WindowState.Minimized;

    private void Maximize_Click(object sender, RoutedEventArgs e) =>
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;

    private void Close_Click(object sender, RoutedEventArgs e) => Close();

    protected override void OnClosing(CancelEventArgs e)
    {
        (DataContext as MainViewModel)?.Dispose();
        base.OnClosing(e);
    }
}
