using System.Collections.Specialized;
using System.ComponentModel;
using System.Windows;
using System.Windows.Media;
using VoidRay.Services;
using VoidRay.ViewModels;

namespace VoidRay;

public partial class MainWindow : Window
{
    private TrayIcon? _tray;
    private bool _quitting;

    public MainWindow()
    {
        InitializeComponent();
        StateChanged += (_, _) => ApplyWindowState();
        Loaded += (_, _) => _tray ??= new TrayIcon(this, (MainViewModel)DataContext);
        ((MainViewModel)DataContext).QuitRequested += Quit;
        ((INotifyCollectionChanged)LogList.Items).CollectionChanged += (_, _) =>
        {
            if (LogList.Items.Count > 0)
                LogList.ScrollIntoView(LogList.Items[^1]);
        };
    }

    /// <summary>Brings the window back from the notification area.</summary>
    public void ShowFromTray()
    {
        Show();
        if (WindowState == WindowState.Minimized)
            WindowState = WindowState.Normal;
        Activate();
        Topmost = true;   // pull in front of other windows…
        Topmost = false;  // …without staying on top
        Focus();
    }

    /// <summary>Really exits: disconnects, restores the proxy and removes the tray icon.</summary>
    public void Quit()
    {
        _quitting = true;
        Close();
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
        if (!_quitting && _tray is not null)
        {
            // Closing only hides: the VPN keeps running from the notification area.
            e.Cancel = true;
            Hide();
            _tray.NotifyStillRunning();
            return;
        }
        (DataContext as MainViewModel)?.Dispose();
        _tray?.Dispose();
        base.OnClosing(e);
    }
}
