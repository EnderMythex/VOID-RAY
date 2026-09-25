using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Windows;
using System.Windows.Input;
using System.Windows.Threading;
using VoidRay.Models;
using VoidRay.Services;

namespace VoidRay.ViewModels;

public enum ConnectionState { Disconnected, Connecting, Connected, Disconnecting }

public enum StatusKind { Neutral, Ok, Warning, Bad }

public sealed class MainViewModel : ObservableObject, IDisposable
{
    private const int MaxLogLines = 400;

    private readonly AppSettings _settings;
    private readonly SubscriptionService _subscriptions = new();
    private readonly XrayCore _core = new();
    private readonly DispatcherTimer _clock;
    private readonly DispatcherTimer _autoRefresh;
    private readonly DispatcherTimer _toastTimer;
    private readonly Dispatcher _ui = Application.Current.Dispatcher;

    private string _subscriptionUrl;
    private bool _isLoading;
    private SubscriptionInfo? _subscription;
    private ServerItemViewModel? _selectedServer;
    private ConnectionState _state;
    private DateTime _connectedAt;
    private string _elapsed = "00:00";
    private string? _publicIp;
    private string? _exitCountry;
    private string? _realDelay;
    private string? _toast;
    private bool _toastIsError;
    private int _socksPort;
    private int _httpPort;
    private bool _suppressReconnect;

    public MainViewModel()
    {
        _settings = SettingsStore.Load();
        _subscriptionUrl = _settings.SubscriptionUrl;
        _socksPort = _settings.SocksPort;
        _httpPort = _settings.HttpPort;

        _subscriptions.Log += AddLog;
        _core.Log += AddLog;
        _core.Crashed += () => _ui.BeginInvoke(OnCoreCrashed);

        _clock = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _clock.Tick += (_, _) => Elapsed = Format.Duration(DateTime.Now - _connectedAt);

        _autoRefresh = new DispatcherTimer { Interval = TimeSpan.FromHours(6) };
        _autoRefresh.Tick += async (_, _) => await RefreshAsync(silent: true);

        _toastTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(4) };
        _toastTimer.Tick += (_, _) => { _toastTimer.Stop(); Toast = null; };

        RefreshCommand = new RelayCommand(() => RefreshAsync(silent: false), () => !IsLoading);
        PasteCommand = new RelayCommand(PasteFromClipboard);
        ToggleConnectionCommand = new RelayCommand(ToggleConnectionAsync,
            () => State is ConnectionState.Connected or ConnectionState.Disconnected);
        PingAllCommand = new RelayCommand(PingAllAsync, () => Servers.Count > 0);
        OpenUrlCommand = new RelayCommand(p => { OpenUrl(p as string); return Task.CompletedTask; });
        CopyLogsCommand = new RelayCommand(() => Clipboard.SetText(string.Join(Environment.NewLine, Logs)));
        ClearLogsCommand = new RelayCommand(() => Logs.Clear());

        // Leftover proxy from a crash? Put the user's settings back.
        SystemProxy.Restore();

        if (_settings.CachedSubscription is { } cached)
            ApplySubscription(cached, persist: false);

        AddLog("VOID-RAY prêt.");
        if (!string.IsNullOrWhiteSpace(_subscriptionUrl))
            _ = RefreshAsync(silent: true);
    }

    // ================================================================ commands

    public ICommand RefreshCommand { get; }
    public ICommand PasteCommand { get; }
    public ICommand ToggleConnectionCommand { get; }
    public ICommand PingAllCommand { get; }
    public ICommand OpenUrlCommand { get; }
    public ICommand CopyLogsCommand { get; }
    public ICommand ClearLogsCommand { get; }

    // ================================================================ subscription

    public string SubscriptionUrl
    {
        get => _subscriptionUrl;
        set => Set(ref _subscriptionUrl, value);
    }

    public bool IsLoading
    {
        get => _isLoading;
        private set
        {
            if (Set(ref _isLoading, value))
                OnPropertyChanged(nameof(RefreshLabel));
        }
    }

    public string RefreshLabel => IsLoading ? "Chargement…" : _subscription is null ? "Importer" : "Actualiser";

    public bool HasSubscription => _subscription is not null;

    public string ProfileTitle => _subscription?.Title ?? "Mon abonnement";
    public string? Username => _subscription?.Username;
    public bool HasUsername => !string.IsNullOrEmpty(Username);

    public StatusKind StatusKind
    {
        get
        {
            if (_subscription is null) return StatusKind.Neutral;
            if (_subscription.Expire is { } exp && exp < DateTimeOffset.Now) return StatusKind.Bad;
            if (_subscription.Total > 0 && _subscription.Used >= _subscription.Total) return StatusKind.Bad;
            var status = _subscription.Status?.ToLowerInvariant();
            if (status is "disabled" or "expired" or "limited") return StatusKind.Bad;
            if (status is "on_hold") return StatusKind.Warning;
            if (_subscription.Expire is { } e && e - DateTimeOffset.Now < TimeSpan.FromDays(3)) return StatusKind.Warning;
            return StatusKind.Ok;
        }
    }

    public string StatusText
    {
        get
        {
            if (_subscription is null) return "";
            if (_subscription.Expire is { } exp && exp < DateTimeOffset.Now) return "EXPIRÉ";
            if (_subscription.Total > 0 && _subscription.Used >= _subscription.Total) return "QUOTA ATTEINT";
            return _subscription.Status?.ToLowerInvariant() switch
            {
                "disabled" => "DÉSACTIVÉ",
                "limited" => "LIMITÉ",
                "expired" => "EXPIRÉ",
                "on_hold" => "EN ATTENTE",
                _ => "ACTIF",
            };
        }
    }

    public string UsedText => Format.Bytes(_subscription?.Used ?? 0);
    public string TotalText => _subscription is { Total: > 0 } s ? Format.Bytes(s.Total) : "∞";
    public string UploadText => Format.Bytes(_subscription?.Upload ?? 0);
    public string DownloadText => Format.Bytes(_subscription?.Download ?? 0);
    public bool IsUnlimited => _subscription is not { Total: > 0 };

    public double UsagePercent => _subscription is { Total: > 0 } s
        ? Math.Clamp(s.Used * 100.0 / s.Total, 0, 100)
        : 0;

    public string RemainingText => _subscription is { Total: > 0 } s
        ? $"{Format.Bytes(Math.Max(0, s.Total - s.Used))} restants · {UsagePercent:0.#}%"
        : "Trafic illimité";

    public string ExpireText => _subscription?.Expire is { } e ? Format.Date(e) : "Jamais";

    public string DaysLeftText
    {
        get
        {
            if (_subscription?.Expire is not { } e) return "Sans date d'expiration";
            var left = e - DateTimeOffset.Now;
            if (left <= TimeSpan.Zero) return "Abonnement expiré";
            if (left.TotalDays >= 2) return $"{(int)left.TotalDays} jours restants";
            if (left.TotalHours >= 1) return $"{(int)left.TotalHours} heures restantes";
            return "Expire dans moins d'une heure";
        }
    }

    public string UpdatedText
    {
        get
        {
            if (_subscription is null) return "";
            var text = "Mis à jour " + _subscription.FetchedAt.ToLocalTime().ToString("dd/MM à HH:mm");
            return _subscription.UpdateIntervalHours is { } h ? $"{text} · auto toutes les {h} h" : text;
        }
    }

    public string? Announce => _subscription?.Announce;
    public bool HasAnnounce => !string.IsNullOrWhiteSpace(Announce);
    public string? SupportUrl => _subscription?.SupportUrl;
    public bool HasSupportUrl => !string.IsNullOrWhiteSpace(SupportUrl);
    public string? WebPageUrl => _subscription?.WebPageUrl;
    public bool HasWebPageUrl => !string.IsNullOrWhiteSpace(WebPageUrl);

    // ================================================================ servers

    public ObservableCollection<ServerItemViewModel> Servers { get; } = new();

    public int ServerCount => Servers.Count;

    public ServerItemViewModel? SelectedServer
    {
        get => _selectedServer;
        set
        {
            var previous = _selectedServer;
            if (!Set(ref _selectedServer, value))
                return;
            OnPropertyChanged(nameof(SelectedServerName));
            _settings.SelectedServerKey = value?.Profile.Key;
            SettingsStore.Save(_settings);
            if (!_suppressReconnect && value is not null && previous is not null && State == ConnectionState.Connected)
                _ = ReconnectAsync();
        }
    }

    public string SelectedServerName => SelectedServer?.Name ?? "Aucun serveur sélectionné";

    // ================================================================ connection

    public ConnectionState State
    {
        get => _state;
        private set
        {
            if (!Set(ref _state, value))
                return;
            OnPropertyChanged(nameof(StateText));
            OnPropertyChanged(nameof(IsConnected));
            OnPropertyChanged(nameof(ActionHint));
            CommandManager.InvalidateRequerySuggested();
        }
    }

    public bool IsConnected => State == ConnectionState.Connected;

    public string StateText => State switch
    {
        ConnectionState.Connecting => "CONNEXION…",
        ConnectionState.Connected => "PROTÉGÉ",
        ConnectionState.Disconnecting => "DÉCONNEXION…",
        _ => "NON PROTÉGÉ",
    };

    public string ActionHint => State switch
    {
        ConnectionState.Connected => "Appuie pour te déconnecter",
        ConnectionState.Disconnected => "Appuie pour te connecter",
        _ => "Patiente un instant…",
    };

    public string Elapsed
    {
        get => _elapsed;
        private set => Set(ref _elapsed, value);
    }

    public string PublicIp
    {
        get => _publicIp ?? "—";
        private set => Set(ref _publicIp, value);
    }

    public string ExitCountry
    {
        get => _exitCountry ?? "—";
        private set => Set(ref _exitCountry, value);
    }

    public string RealDelay
    {
        get => _realDelay ?? "—";
        private set => Set(ref _realDelay, value);
    }

    public string LocalEndpoints => $"HTTP 127.0.0.1:{_httpPort}  ·  SOCKS5 127.0.0.1:{_socksPort}";

    // ================================================================ misc

    public ObservableCollection<string> Logs { get; } = new();

    public string? Toast
    {
        get => _toast;
        private set => Set(ref _toast, value);
    }

    public bool ToastIsError
    {
        get => _toastIsError;
        private set => Set(ref _toastIsError, value);
    }

    // ================================================================ logic

    private async Task RefreshAsync(bool silent)
    {
        var url = SubscriptionUrl?.Trim() ?? "";
        if (url.Length == 0)
        {
            if (!silent) ShowToast("Colle d'abord ton lien d'abonnement.", error: true);
            return;
        }

        IsLoading = true;
        try
        {
            AddLog("Récupération de l'abonnement…");
            var info = await _subscriptions.FetchAsync(url);
            _settings.SubscriptionUrl = url;
            ApplySubscription(info, persist: true);
            AddLog($"Abonnement chargé : {info.Servers.Count} serveur(s).");
            if (!silent)
                ShowToast(info.Servers.Count > 0
                    ? $"Abonnement chargé · {info.Servers.Count} serveur(s)"
                    : "Abonnement chargé, mais aucun serveur compatible.", error: info.Servers.Count == 0);
            _ = PingAllAsync();
        }
        catch (Exception ex)
        {
            AddLog("Erreur abonnement : " + ex.Message);
            if (!silent || _subscription is null)
                ShowToast(ex is HttpRequestException or TaskCanceledException
                    ? "Impossible de joindre le serveur d'abonnement."
                    : ex.Message, error: true);
        }
        finally
        {
            IsLoading = false;
        }
    }

    private void ApplySubscription(SubscriptionInfo info, bool persist)
    {
        _subscription = info;
        if (persist)
        {
            _settings.CachedSubscription = info;
            SettingsStore.Save(_settings);
        }

        var selectedKey = SelectedServer?.Profile.Key ?? _settings.SelectedServerKey;
        _suppressReconnect = true;
        Servers.Clear();
        foreach (var s in info.Servers)
            Servers.Add(new ServerItemViewModel(s));
        SelectedServer = Servers.FirstOrDefault(s => s.Profile.Key == selectedKey) ?? Servers.FirstOrDefault();
        _suppressReconnect = false;

        _autoRefresh.Interval = TimeSpan.FromHours(Math.Clamp(info.UpdateIntervalHours ?? 6, 1, 48));
        _autoRefresh.Start();

        foreach (var name in new[]
                 {
                     nameof(HasSubscription), nameof(ProfileTitle), nameof(Username), nameof(HasUsername),
                     nameof(StatusKind), nameof(StatusText), nameof(UsedText), nameof(TotalText),
                     nameof(UploadText), nameof(DownloadText), nameof(IsUnlimited), nameof(UsagePercent),
                     nameof(RemainingText), nameof(ExpireText), nameof(DaysLeftText), nameof(UpdatedText),
                     nameof(Announce), nameof(HasAnnounce), nameof(SupportUrl), nameof(HasSupportUrl),
                     nameof(WebPageUrl), nameof(HasWebPageUrl), nameof(ServerCount), nameof(RefreshLabel),
                 })
            OnPropertyChanged(name);
    }

    private async Task PingAllAsync()
    {
        var items = Servers.ToList();
        foreach (var item in items)
            item.BeginTest();
        using var gate = new SemaphoreSlim(8);
        await Task.WhenAll(items.Select(async item =>
        {
            await gate.WaitAsync();
            try
            {
                var ms = await NetTools.TcpPingAsync(item.Profile.Address, item.Profile.Port);
                item.SetPing(ms);
            }
            finally
            {
                gate.Release();
            }
        }));
    }

    private Task ToggleConnectionAsync() =>
        State == ConnectionState.Connected ? DisconnectAsync() : ConnectAsync();

    private async Task ConnectAsync()
    {
        var server = SelectedServer;
        if (server is null)
        {
            ShowToast(HasSubscription ? "Choisis un serveur dans la liste." : "Importe d'abord ton abonnement.", error: true);
            return;
        }
        if (StatusKind == StatusKind.Bad)
            AddLog("Attention : l'abonnement semble expiré ou épuisé.");

        State = ConnectionState.Connecting;
        try
        {
            var exe = await _core.EnsureInstalledAsync();
            _socksPort = NetTools.FindFreePort(_settings.SocksPort);
            _httpPort = NetTools.FindFreePort(_settings.HttpPort);
            OnPropertyChanged(nameof(LocalEndpoints));

            await File.WriteAllTextAsync(AppPaths.XrayConfig,
                XrayConfigBuilder.Build(server.Profile, _socksPort, _httpPort));
            AddLog($"Connexion à {server.Name} ({server.Protocol} · {server.Transport})…");
            await _core.StartAsync(exe, AppPaths.XrayConfig, _socksPort);
            SystemProxy.Enable("127.0.0.1", _httpPort);

            _connectedAt = DateTime.Now;
            Elapsed = "00:00";
            _clock.Start();
            State = ConnectionState.Connected;
            AddLog("Connecté. Proxy système activé.");
            _ = CheckExitAsync();
        }
        catch (Exception ex)
        {
            _core.Stop();
            SystemProxy.Restore();
            State = ConnectionState.Disconnected;
            AddLog("Échec de connexion : " + ex.Message);
            ShowToast("Échec de connexion : " + ex.Message, error: true);
        }
    }

    private async Task DisconnectAsync()
    {
        State = ConnectionState.Disconnecting;
        await Task.Run(() =>
        {
            SystemProxy.Restore();
            _core.Stop();
        });
        ResetConnectionInfo();
        State = ConnectionState.Disconnected;
        AddLog("Déconnecté. Proxy système restauré.");
    }

    private async Task ReconnectAsync()
    {
        await DisconnectAsync();
        await ConnectAsync();
    }

    private async Task CheckExitAsync()
    {
        var exit = await NetTools.CheckExitAsync(_httpPort);
        if (State != ConnectionState.Connected)
            return;
        if (exit is null)
        {
            AddLog("Le test de connectivité a échoué : le serveur ne laisse pas passer le trafic.");
            ShowToast("Connecté, mais aucun trafic ne passe. Essaie un autre serveur.", error: true);
            RealDelay = "échec";
            return;
        }
        PublicIp = exit.Ip;
        ExitCountry = exit.Country is null ? "—" : $"{exit.Country} ({exit.CountryCode})";
        RealDelay = $"{exit.DelayMs} ms";
        AddLog($"IP de sortie : {exit.Ip} {exit.Country}");
    }

    private void OnCoreCrashed()
    {
        if (State != ConnectionState.Connected)
            return;
        SystemProxy.Restore();
        ResetConnectionInfo();
        State = ConnectionState.Disconnected;
        ShowToast("Le moteur Xray s'est arrêté. Consulte le journal.", error: true);
    }

    private void ResetConnectionInfo()
    {
        _clock.Stop();
        Elapsed = "00:00";
        PublicIp = null!;
        ExitCountry = null!;
        RealDelay = null!;
    }

    private void PasteFromClipboard()
    {
        if (!Clipboard.ContainsText())
            return;
        SubscriptionUrl = Clipboard.GetText().Trim();
        RefreshCommand.Execute(null);
    }

    private void OpenUrl(string? url)
    {
        if (string.IsNullOrWhiteSpace(url))
            return;
        try
        {
            Process.Start(new ProcessStartInfo(url) { UseShellExecute = true });
        }
        catch (Exception ex)
        {
            ShowToast("Impossible d'ouvrir le lien : " + ex.Message, error: true);
        }
    }

    private void ShowToast(string message, bool error = false)
    {
        ToastIsError = error;
        Toast = message;
        _toastTimer.Stop();
        _toastTimer.Start();
    }

    private void AddLog(string line)
    {
        var entry = $"[{DateTime.Now:HH:mm:ss}] {line}";
        _ui.BeginInvoke(() =>
        {
            Logs.Add(entry);
            while (Logs.Count > MaxLogLines)
                Logs.RemoveAt(0);
        });
    }

    public void Dispose()
    {
        _clock.Stop();
        _autoRefresh.Stop();
        SystemProxy.Restore();
        _core.Dispose();
    }
}
