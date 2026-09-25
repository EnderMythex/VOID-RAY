using System.Windows;
using System.Windows.Threading;
using VoidRay.Services;

namespace VoidRay;

public partial class App : Application
{
    private Mutex? _singleInstance;

    protected override void OnStartup(StartupEventArgs e)
    {
        _singleInstance = new Mutex(true, "VoidRay.SingleInstance", out var isFirst);
        if (!isFirst)
        {
            MessageBox.Show("VOID-RAY est déjà ouvert.", "VOID-RAY", MessageBoxButton.OK, MessageBoxImage.Information);
            Shutdown();
            return;
        }

        DispatcherUnhandledException += OnUnhandled;
        AppDomain.CurrentDomain.ProcessExit += (_, _) => SystemProxy.Restore();
        base.OnStartup(e);

        MainWindow = new MainWindow();
        MainWindow.Show();
    }

    private static void OnUnhandled(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        MessageBox.Show(e.Exception.Message, "VOID-RAY — erreur", MessageBoxButton.OK, MessageBoxImage.Error);
        e.Handled = true;
    }

    protected override void OnSessionEnding(SessionEndingCancelEventArgs e)
    {
        SystemProxy.Restore();
        base.OnSessionEnding(e);
    }

    protected override void OnExit(ExitEventArgs e)
    {
        SystemProxy.Restore();
        _singleInstance?.Dispose();
        base.OnExit(e);
    }
}
