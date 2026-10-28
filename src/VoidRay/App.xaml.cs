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
    private bool _ownsMutex;

    protected override void OnStartup(StartupEventArgs e)
    {
        // After "restart as administrator" the previous instance may still be closing: wait for it.
        var restarting = e.Args.Contains(Elevation.RestartArg);
        _singleInstance = new Mutex(false, InstanceName);
        bool isFirst;
        try
        {
            isFirst = _singleInstance.WaitOne(restarting ? TimeSpan.FromSeconds(15) : TimeSpan.Zero);
        }
        catch (AbandonedMutexException)
        {
            isFirst = true;
        }
        _ownsMutex = isFirst;
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

        Updater.CleanupOldVersion();
        DispatcherUnhandledException += OnUnhandled;
        AppDomain.CurrentDomain.ProcessExit += (_, _) => SystemProxy.Restore();
        base.OnStartup(e);

        var settings = SettingsStore.Load();
        Loc.I.SetMode(settings.Language);
        ThemeManager.Apply(settings.Theme, settings.Accent);

        var window = new MainWindow();
        MainWindow = window;
        if (e.Args.Contains(Elevation.ConnectArg))
            window.Loaded += (_, _) => (window.DataContext as ViewModels.MainViewModel)?.ConnectOnStartup();
        window.Show();
    }

    /// <summary>The message plus its inner causes (and the XAML line, if any), so a report pinpoints the problem.</summary>
    private static string Describe(Exception ex)
    {
        var lines = new List<string>();
        for (var e = ex; e is not null; e = e.InnerException)
        {
            var line = e.Message;
            if (e is System.Windows.Markup.XamlParseException { LineNumber: > 0 } x)
                line += $" (XAML {x.LineNumber}:{x.LinePosition})";
            if (!lines.Contains(line))
                lines.Add(line);
        }
        return string.Join("\n\n", lines);
    }

    private static void OnUnhandled(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        MessageBox.Show(Describe(e.Exception), "VOID-RAY — erreur", MessageBoxButton.OK, MessageBoxImage.Error);
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
        if (_ownsMutex)
        {
            try { _singleInstance?.ReleaseMutex(); } catch (ApplicationException) { }
        }
        _singleInstance?.Dispose();
        base.OnExit(e);
    }
}
