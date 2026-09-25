using System.Windows;
using System.Windows.Threading;
using VoidRay.Services;

namespace VoidRay;

public partial class App : Application
{
    private const string InstanceName = "VoidRay.SingleInstance";
    private const string ShowEventName = "VoidRay.ShowWindow";

    private Mutex? _singleInstance;
    private EventWaitHandle? _showSignal;

    protected override void OnStartup(StartupEventArgs e)
    {
        _singleInstance = new Mutex(true, InstanceName, out var isFirst);
        if (!isFirst)
        {
            // Already running (maybe hidden in the tray): ask it to show itself.
            try { EventWaitHandle.OpenExisting(ShowEventName).Set(); }
            catch (WaitHandleCannotBeOpenedException) { }
            Shutdown();
            return;
        }

        _showSignal = new EventWaitHandle(false, EventResetMode.AutoReset, ShowEventName);
        new Thread(() =>
        {
            while (_showSignal.WaitOne())
                Dispatcher.BeginInvoke(() => (MainWindow as MainWindow)?.ShowFromTray());
        }) { IsBackground = true, Name = "VoidRay show signal" }.Start();

        DispatcherUnhandledException += OnUnhandled;
        AppDomain.CurrentDomain.ProcessExit += (_, _) => SystemProxy.Restore();
        base.OnStartup(e);

        var settings = SettingsStore.Load();
        Loc.I.SetMode(settings.Language);
        ThemeManager.Apply(settings.Theme, settings.Accent);

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
        // Windows is logging off: really quit instead of hiding to the tray.
        (MainWindow as MainWindow)?.Quit();
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
