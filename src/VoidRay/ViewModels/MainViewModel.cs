using System.Collections.ObjectModel;
using System.Diagnostics;
using System.Windows;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Threading;
using Microsoft.Win32;
using VoidRay.Models;
using VoidRay.Services;

namespace VoidRay.ViewModels;

public enum ConnectionState { Disconnected, Connecting, Connected, Disconnecting }

public sealed class MainViewModel : ObservableObject, IDisposable
{
    private const int MaxLogLines = 400;
    private static readonly TimeSpan RefreshEvery = TimeSpan.FromMinutes(2);

    private readonly AppSettings _settings;
    private readonly SubscriptionService _subscriptions = new();
    private readonly XrayCore _core = new();
    private readonly DispatcherTimer _clock;
    private readonly DispatcherTimer _autoRefresh;
    private readonly DispatcherTimer _freshTimer;
    private readonly DispatcherTimer _toastTimer;
    private readonly Dispatcher _ui = Application.Current.Dispatcher;

    // gate
    private bool _isAuthenticated;
    private string _gateUrl = "";
    private string? _gateError;
    private bool _isValidating;

    // subscription
    private SubscriptionInfo? _sub;
    private bool _isRefreshing;
    private bool _refreshFailed;
    private DateTimeOffset _lastRefresh = DateTimeOffset.Now;
    private IReadOnlyList<double?> _history = Array.Empty<double?>();

    // servers / connection
    private ServerItemViewModel? _selectedServer;
    private ConnectionState _state;
    private DateTime _connectedAt;
    private string _elapsed = "00:00";
    private string? _publicIp, _exitCountry, _realDelay;
    private int _socksPort, _httpPort;
    private bool _suppressReconnect;

    // chrome
    private bool _isPaletteOpen;
    private bool _isLogOpen;
    private string? _toast;
    private bool _toastIsError;

    public MainViewModel()
    {
        _settings = SettingsStore.Load();
        _socksPort = _settings.SocksPort;
        _httpPort = _settings.HttpPort;

        Loc.I.SetMode(_settings.Language);
        ThemeManager.Apply(_settings.Theme, _settings.Accent);
        Accents = ThemeManager.Accents
            .Select((a, i) => new AccentOption(i, a.Name, Frozen(a.Dark)) { IsSelected = i == _settings.Accent })
            .ToList();
        SystemEvents.UserPreferenceChanged += OnSystemPreferenceChanged;
        Loc.I.Changed += RaiseEverything;

        _subscriptions.Log += AddLog;
        _core.Log += AddLog;
        _core.Crashed += () => _ui.BeginInvoke(OnCoreCrashed);

        _clock = new DispatcherTimer { Interval = TimeSpan.FromSeconds(1) };
        _clock.Tick += (_, _) => Elapsed = Format.Duration(DateTime.Now - _connectedAt);
        _autoRefresh = new DispatcherTimer { Interval = RefreshEvery };
        _autoRefresh.Tick += async (_, _) => await RefreshAsync(manual: false);
        _freshTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(30) };
        _freshTimer.Tick += (_, _) => { OnPropertyChanged(nameof(FreshText)); RebuildDetails(); };
        _freshTimer.Start();
        _toastTimer = new DispatcherTimer { Interval = TimeSpan.FromSeconds(3.5) };
        _toastTimer.Tick += (_, _) => { _toastTimer.Stop(); Toast = null; };

        SignInCommand = new RelayCommand(SignInAsync, () => !IsValidating);
        ChangeLinkCommand = new RelayCommand(ChangeLinkAsync);
        RefreshCommand = new RelayCommand(() => RefreshAsync(manual: true), () => !IsRefreshing);
        CycleThemeCommand = new RelayCommand(CycleTheme);
        CycleLangCommand = new RelayCommand(CycleLang);
        SetAccentCommand = new RelayCommand(p => { SetAccent(p); return Task.CompletedTask; });
        ToggleConnectionCommand = new RelayCommand(ToggleConnectionAsync,
            () => State is ConnectionState.Connected or ConnectionState.Disconnected);
        PingAllCommand = new RelayCommand(PingAllAsync, () => Servers.Count > 0);
        CopyLinkCommand = new RelayCommand(() => Copy(_settings.SubscriptionUrl, Loc.T("lblLink")));
        CopyIdCommand = new RelayCommand(() => Copy(Sid, Loc.T("lblId")));
        CopyConfigCommand = new RelayCommand(p =>
        {
            if (p is ServerItemViewModel s && s.RawLink is not null)
                Copy(s.RawLink, Loc.T("lblConfig"));
            return Task.CompletedTask;
        });
        OpenSupportCommand = new RelayCommand(() => OpenUrl(SupportUrl));
        ToggleLogCommand = new RelayCommand(() => IsLogOpen = !IsLogOpen);
        CopyLogsCommand = new RelayCommand(() => Copy(string.Join(Environment.NewLine, Logs), Loc.T("lblLog")));
        ClearLogsCommand = new RelayCommand(() => Logs.Clear());

        // Leftover proxy from a crash? Put the user's settings back.
        SystemProxy.Restore();
        AddLog("VOID-RAY prêt.");

        if (Brand.TryNormalizeLink(_settings.SubscriptionUrl, out var link, out _))
        {
            _settings.SubscriptionUrl = link;
            if (_settings.CachedSubscription is { } cached)
                ApplySubscription(cached, isNew: true);
            IsAuthenticated = true;
            _ = RefreshAsync(manual: false);
        }
    }

    private static Brush Frozen(Color c)
    {
        var b = new SolidColorBrush(c);
        b.Freeze();
        return b;
    }

    // ================================================================ commands

    public ICommand SignInCommand { get; }
    public ICommand ChangeLinkCommand { get; }
    public ICommand RefreshCommand { get; }
    public ICommand CycleThemeCommand { get; }
    public ICommand CycleLangCommand { get; }
    public ICommand SetAccentCommand { get; }
    public ICommand ToggleConnectionCommand { get; }
    public ICommand PingAllCommand { get; }
    public ICommand CopyLinkCommand { get; }
    public ICommand CopyIdCommand { get; }
    public ICommand CopyConfigCommand { get; }
    public ICommand OpenSupportCommand { get; }
    public ICommand ToggleLogCommand { get; }
    public ICommand CopyLogsCommand { get; }
    public ICommand ClearLogsCommand { get; }

    // ================================================================ gate

    public bool IsAuthenticated
    {
        get => _isAuthenticated;
        private set
        {
            if (Set(ref _isAuthenticated, value))
                OnPropertyChanged(nameof(IdentityTitle));
        }
    }

    public string GateUrl
    {
        get => _gateUrl;
        set
        {
            if (Set(ref _gateUrl, value))
                GateError = null;
        }
    }

    public string? GateError
    {
        get => _gateError;
        private set => Set(ref _gateError, value);
    }

    public bool IsValidating
    {
        get => _isValidating;
        private set
        {
            if (Set(ref _isValidating, value))
                OnPropertyChanged(nameof(GateButtonText));
        }
    }

    public string GateButtonText => Loc.T(IsValidating ? "gateChecking" : "gateButton");

    private async Task SignInAsync()
    {
        if (!Brand.TryNormalizeLink(GateUrl, out var link, out _))
        {
            GateError = Loc.T("errFormat");
            return;
        }

        IsValidating = true;
        GateError = null;
        try
        {
            var info = await FetchAsync(link);
            if (info.Servers.Count == 0)
            {
                GateError = Loc.T("errEmpty");
                return;
            }
            _settings.SubscriptionUrl = link;
            ApplySubscription(info, isNew: true);
            IsAuthenticated = true;
            GateUrl = "";
            ShowToast(info.Servers.Any(x => x.IsBypassPatched)
                ? Loc.T("loaded", info.Servers.Count) + " · " + Loc.T("bypassApplied")
                : Loc.T("loaded", info.Servers.Count));
            _ = PingAllAsync();
        }
        catch (SubscriptionService.NotFoundException)
        {
            GateError = Loc.T("errNotFound");
        }
        catch (Exception ex)
        {
            AddLog("Erreur abonnement : " + ex.Message);
            GateError = Loc.T("errNetwork");
        }
        finally
        {
            IsValidating = false;
        }
    }

    private async Task ChangeLinkAsync()
    {
        if (State == ConnectionState.Connected)
            await DisconnectAsync();
        SignOut(null);
    }

    private void SignOut(string? error)
    {
        _autoRefresh.Stop();
        _sub = null;
        _settings.SubscriptionUrl = "";
        _settings.CachedSubscription = null;
        _settings.SelectedServerKey = null;
        SettingsStore.Save(_settings);
        _suppressReconnect = true;
        Servers.Clear();
        SelectedServer = null;
        _suppressReconnect = false;
        DetailRows.Clear();
        IsAuthenticated = false;
        GateError = error;
        RaiseEverything();
    }

    // ================================================================ theme / language

    public IReadOnlyList<AccentOption> Accents { get; }

    /// <summary>"auto", "dark" or "light" — drives the theme button icon.</summary>
    public string ThemeMode => _settings.Theme;

    public string ThemeLabel => Loc.T(_settings.Theme switch { "dark" => "themeDark", "light" => "themeLight", _ => "themeAuto" });

    public string LangBadge => Loc.I.Mode == "auto" ? "A/" + Loc.I.Lang.ToUpperInvariant() : Loc.I.Lang.ToUpperInvariant();

    public bool IsPaletteOpen
    {
        get => _isPaletteOpen;
        set => Set(ref _isPaletteOpen, value);
    }

    private void CycleTheme()
    {
        _settings.Theme = _settings.Theme switch { "auto" => "dark", "dark" => "light", _ => "auto" };
        SettingsStore.Save(_settings);
        ThemeManager.Apply(_settings.Theme, _settings.Accent);
        OnPropertyChanged(nameof(ThemeMode));
        OnPropertyChanged(nameof(ThemeLabel));
    }

    private void CycleLang()
    {
        _settings.Language = _settings.Language switch { "auto" => "en", "en" => "fr", _ => "auto" };
        SettingsStore.Save(_settings);
        Loc.I.SetMode(_settings.Language);
    }

    private void SetAccent(object? parameter)
    {
        if (parameter is not AccentOption option)
            return;
        _settings.Accent = option.Index;
        SettingsStore.Save(_settings);
        foreach (var a in Accents)
            a.IsSelected = a.Index == option.Index;
        ThemeManager.Apply(_settings.Theme, _settings.Accent);
        IsPaletteOpen = false;
    }

    private void OnSystemPreferenceChanged(object? sender, UserPreferenceChangedEventArgs e)
    {
        if (_settings.Theme == "auto" && e.Category == UserPreferenceCategory.General)
            _ui.BeginInvoke(() => ThemeManager.Apply(_settings.Theme, _settings.Accent));
    }

    // ================================================================ subscription: identity

    public string IdentityTitle => IsAuthenticated ? _sub?.Title ?? Brand.ServiceName : "VOID-RAY";
    public string Sid => _sub?.Sid ?? LastSegment(_settings.SubscriptionUrl);
    public string SubscriptionUrl => _settings.SubscriptionUrl;
    public string SupportUrl => _sub?.SupportUrl is { Length: > 0 } s ? s : Brand.DefaultSupportUrl;
    public string AccountsText => _sub is { Emails.Count: > 0 } s ? string.Join(" · ", s.Emails) : Sid.Length > 0 ? "ID " + Sid : "";

    private static string LastSegment(string url) => url.TrimEnd('/').Split('/').LastOrDefault() ?? "";

    // ================================================================ subscription: gauge & status

    private bool Unlimited => _sub is not { Total: > 0 };
    private double Ratio => _sub is { Total: > 0 } s ? Math.Min(1, s.Used / (double)s.Total) : 0;
    private bool Expired => _sub?.Expire is { } e && e < DateTimeOffset.Now;
    private bool Drained => _sub is { Total: > 0 } s && s.Used >= s.Total;

    public bool IsUnlimited => Unlimited;
    public bool IsLive => _sub is { Enabled: true } && !Expired && !Drained;
    public double GaugeRatio => Ratio;

    public Tone GaugeTone => !IsLive ? Tone.Alert : Ratio >= 0.9 ? Tone.Warn : Tone.Ok;

    private string GaugeFigure => _sub is null ? "—"
        : Unlimited ? Format.Bytes(_sub.Used) : Format.Bytes(Math.Max(0, _sub.Total - _sub.Used));

    public string GaugeValue => GaugeFigure.Split(' ')[0];
    public string GaugeUnit => GaugeFigure.Contains(' ') ? GaugeFigure[(GaugeFigure.IndexOf(' ') + 1)..] : "";
    public string GaugeCaption => Unlimited ? Loc.T("capUsed") : Loc.T("capLeft", Format.Bytes(_sub!.Total));

    public (string Label, Tone Tone) Status
    {
        get
        {
            if (_sub is null) return ("—", Tone.Normal);
            if (!_sub.Enabled) return (Loc.T("stDisabled"), Tone.Alert);
            if (Expired) return (Loc.T("stExpired"), Tone.Alert);
            if (Drained) return (Loc.T("stQuota"), Tone.Alert);
            if (Unlimited) return (Loc.T("stUnlimited"), Tone.Ok);
            if (Ratio >= 0.9) return (Loc.T("stAlmost"), Tone.Warn);
            return (Loc.T("stActive"), Tone.Ok);
        }
    }

    public string StatusLabel => Status.Label;
    public Tone StatusTone => Status.Tone;

    public string UploadText => Format.Bytes(_sub?.Upload ?? 0);
    public string DownloadText => Format.Bytes(_sub?.Download ?? 0);

    // ================================================================ subscription: history

    public IReadOnlyList<double?> History
    {
        get => _history;
        private set => Set(ref _history, value);
    }

    public bool HasHistory => _history.Count(v => v.HasValue) >= 2;

    public string HistoryAverage
    {
        get
        {
            var known = _history.Where(v => v.HasValue).Select(v => v!.Value).ToList();
            return known.Count >= 2 ? Loc.T("histAvg", Format.Bytes(known.Average())) : "";
        }
    }

    private void RecordHistory()
    {
        if (_sub is null)
            return;
        var key = Sid.Length > 0 ? Sid : "default";
        if (!_settings.UsageHistory.TryGetValue(key, out var list))
            _settings.UsageHistory[key] = list = new List<long[]>();
        UsageHistory.Record(list, _sub.Used);
        History = UsageHistory.Daily(list);
        OnPropertyChanged(nameof(HasHistory));
        OnPropertyChanged(nameof(HistoryAverage));
    }

    // ================================================================ subscription: details

    public ObservableCollection<DetailRow> DetailRows { get; } = new();

    private void RebuildDetails()
    {
        DetailRows.Clear();
        if (_sub is null)
            return;
        var s = _sub;
        var status = Status;

        DetailRows.Add(new DetailRow(Loc.T("rowId"), Sid.Length > 0 ? Sid : "—", Mono: true));
        if (s.Emails.Count > 0)
            DetailRows.Add(new DetailRow(Loc.T(s.Emails.Count > 1 ? "rowAccounts" : "rowAccount"), string.Join(" · ", s.Emails)));
        else if (!string.IsNullOrEmpty(s.Username))
            DetailRows.Add(new DetailRow(Loc.T("rowAccount"), s.Username));
        DetailRows.Add(new DetailRow(Loc.T("rowStatus"), status.Label, Tone: status.Tone, IsChip: true));
        DetailRows.Add(new DetailRow(Loc.T("rowDown"), Format.Bytes(s.Download), Mono: true));
        DetailRows.Add(new DetailRow(Loc.T("rowUp"), Format.Bytes(s.Upload), Mono: true));
        DetailRows.Add(new DetailRow(Loc.T("rowUsed"), Format.Bytes(s.Used), Mono: true));
        DetailRows.Add(new DetailRow(Loc.T("rowQuota"), Unlimited ? "∞" : Format.Bytes(s.Total), Mono: true));
        if (!Unlimited)
            DetailRows.Add(new DetailRow(Loc.T("rowLeft"), Format.Bytes(Math.Max(0, s.Total - s.Used)), Mono: true));
        DetailRows.Add(s.LastOnline is { } seen
            ? new DetailRow(Loc.T("rowOnline"), Format.Stamp(seen), Format.Ago(seen))
            : new DetailRow(Loc.T("rowOnline"), Loc.T("neverSeen")));

        if (s.Expire is not { } exp)
        {
            DetailRows.Add(new DetailRow(Loc.T("rowExpiry"), Loc.T("noExpiry")));
        }
        else
        {
            var days = (int)Math.Ceiling((exp - DateTimeOffset.Now).TotalDays);
            var hint = days < 0 ? Loc.T("expired") : days == 0 ? Loc.T("today")
                : days == 1 ? Loc.T("inDay", 1) : Loc.T("inDays", days);
            var tone = days <= 3 ? Tone.Alert : days <= 7 ? Tone.Warn : Tone.Normal;
            DetailRows.Add(new DetailRow(Loc.T("rowExpiry"), Format.ShortDate(exp), hint, Tone: tone));
        }
    }

    // ================================================================ refresh

    public bool IsRefreshing
    {
        get => _isRefreshing;
        private set
        {
            if (Set(ref _isRefreshing, value))
                OnPropertyChanged(nameof(FreshText));
        }
    }

    public string FreshText
    {
        get
        {
            if (_refreshFailed) return Loc.T("freshFail");
            var diff = DateTimeOffset.Now - _lastRefresh;
            if (diff < TimeSpan.FromSeconds(90)) return Loc.T("freshNow");
            if (diff < TimeSpan.FromHours(1)) return Loc.T("freshMin", (int)Math.Round(diff.TotalMinutes));
            return Loc.T("freshHour", (int)Math.Round(diff.TotalHours));
        }
    }

    private async Task<SubscriptionInfo> FetchAsync(string link)
    {
        var info = await _subscriptions.FetchAsync(link);
        info.Sid ??= LastSegment(link);
        var patched = FirewallBypass.Apply(info.Servers);
        if (patched > 0)
            AddLog($"Firewall Bypass : {patched} serveur(s) reconfiguré(s) → {FirewallBypass.CleanIp}:{FirewallBypass.Port} TLS, SNI {FirewallBypass.Domain}.");
        return info;
    }

    private async Task RefreshAsync(bool manual)
    {
        if (!IsAuthenticated || IsRefreshing)
            return;
        IsRefreshing = true;
        try
        {
            var info = await FetchAsync(_settings.SubscriptionUrl);
            var changed = !(_sub?.Servers.Select(s => s.Key) ?? Enumerable.Empty<string>())
                .SequenceEqual(info.Servers.Select(s => s.Key));
            ApplySubscription(info, isNew: changed);
            _refreshFailed = false;
            _lastRefresh = DateTimeOffset.Now;
            if (manual)
                ShowToast(Loc.T("loaded", info.Servers.Count));
            if (changed || manual)
                _ = PingAllAsync();
        }
        catch (SubscriptionService.NotFoundException)
        {
            AddLog("Abonnement introuvable : retour à l'écran de connexion.");
            if (State != ConnectionState.Disconnected)
                await DisconnectAsync();
            SignOut(Loc.T("errRevoked"));
        }
        catch (Exception ex)
        {
            _refreshFailed = true;
            AddLog("Erreur de mise à jour : " + ex.Message);
            if (manual)
                ShowToast(Loc.T("errNetwork"), error: true);
        }
        finally
        {
            IsRefreshing = false;
        }
    }

    private void ApplySubscription(SubscriptionInfo info, bool isNew)
    {
        _sub = info;
        _settings.CachedSubscription = info;

        if (isNew || Servers.Count == 0)
        {
            var selectedKey = SelectedServer?.Profile.Key ?? _settings.SelectedServerKey;
            _suppressReconnect = true;
            Servers.Clear();
            foreach (var s in info.Servers)
                Servers.Add(new ServerItemViewModel(s));
            SelectedServer = Servers.FirstOrDefault(s => s.Profile.Key == selectedKey) ?? Servers.FirstOrDefault();
            _suppressReconnect = false;
        }

        RecordHistory();
        SettingsStore.Save(_settings);
        _autoRefresh.Start();
        RaiseEverything();
    }

    private void RaiseEverything()
    {
        foreach (var name in new[]
                 {
                     nameof(IdentityTitle), nameof(Sid), nameof(SubscriptionUrl), nameof(SupportUrl), nameof(AccountsText),
                     nameof(IsUnlimited), nameof(IsLive), nameof(GaugeRatio), nameof(GaugeTone), nameof(GaugeValue),
                     nameof(GaugeUnit), nameof(GaugeCaption), nameof(StatusLabel), nameof(StatusTone),
                     nameof(UploadText), nameof(DownloadText), nameof(HasHistory), nameof(HistoryAverage),
                     nameof(ServerCount), nameof(FreshText), nameof(ThemeLabel), nameof(LangBadge),
                     nameof(GateButtonText), nameof(StateText), nameof(ActionHint), nameof(SelectedServerName),
                     nameof(RealDelay),
                 })
            OnPropertyChanged(name);
        RebuildDetails();
    }

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
            if (value is not null)
            {
                _settings.SelectedServerKey = value.Profile.Key;
                SettingsStore.Save(_settings);
            }
            if (!_suppressReconnect && value is not null && previous is not null && State == ConnectionState.Connected)
                _ = ReconnectAsync();
        }
    }

    public string SelectedServerName => SelectedServer?.Name ?? Loc.T("noServer");

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
                item.SetPing(await NetTools.TcpPingAsync(item.Profile.Address, item.Profile.Port));
            }
            finally
            {
                gate.Release();
            }
        }));
    }

    // ================================================================ connection

    public ConnectionState State
    {
        get => _state;
        private set
        {
            if (!Set(ref _state, value))
                return;
            OnPropertyChanged(nameof(StateText));
            OnPropertyChanged(nameof(ActionHint));
            OnPropertyChanged(nameof(IsConnected));
            CommandManager.InvalidateRequerySuggested();
        }
    }

    public bool IsConnected => State == ConnectionState.Connected;

    public string StateText => Loc.T(State switch
    {
        ConnectionState.Connecting => "stateConnecting",
        ConnectionState.Connected => "stateOn",
        ConnectionState.Disconnecting => "stateDisconnecting",
        _ => "stateOff",
    });

    public string ActionHint => Loc.T(State switch
    {
        ConnectionState.Connected => "hintOn",
        ConnectionState.Disconnected => "hintOff",
        _ => "hintWait",
    });

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
        get => _realDelay switch { null => "—", "failed" => Loc.T("failed"), var d => d };
        private set => Set(ref _realDelay, value);
    }

    public string LocalEndpoints => $"HTTP 127.0.0.1:{_httpPort}  ·  SOCKS5 127.0.0.1:{_socksPort}";

    private Task ToggleConnectionAsync() =>
        State == ConnectionState.Connected ? DisconnectAsync() : ConnectAsync();

    private async Task ConnectAsync()
    {
        var server = SelectedServer;
        if (server is null)
        {
            ShowToast(Loc.T("pickServer"), error: true);
            return;
        }

        State = ConnectionState.Connecting;
        try
        {
            var exe = await _core.EnsureInstalledAsync();
            _socksPort = NetTools.FindFreePort(_settings.SocksPort);
            _httpPort = NetTools.FindFreePort(_settings.HttpPort);
            OnPropertyChanged(nameof(LocalEndpoints));

            await File.WriteAllTextAsync(AppPaths.XrayConfig,
                XrayConfigBuilder.Build(server.Profile, _socksPort, _httpPort));
            AddLog($"Connexion à {server.Name} ({server.ProtocolTag} · {server.NetworkTag}"
                   + (server.SecurityTag is null ? "" : " · " + server.SecurityTag) + ")…");
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
            ShowToast(Loc.T("errConnect", ex.Message), error: true);
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
            ShowToast(Loc.T("errNoTraffic"), error: true);
            RealDelay = "failed";
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
        ShowToast(Loc.T("errCore"), error: true);
    }

    private void ResetConnectionInfo()
    {
        _clock.Stop();
        Elapsed = "00:00";
        PublicIp = null!;
        ExitCountry = null!;
        RealDelay = null!;
    }

    // ================================================================ misc

    public ObservableCollection<string> Logs { get; } = new();

    public bool IsLogOpen
    {
        get => _isLogOpen;
        set => Set(ref _isLogOpen, value);
    }

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

    private void Copy(string? text, string label)
    {
        if (string.IsNullOrEmpty(text))
            return;
        try
        {
            Clipboard.SetText(text);
            ShowToast(Loc.T("copied", label));
        }
        catch (Exception ex)
        {
            ShowToast(ex.Message, error: true);
        }
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
            ShowToast(ex.Message, error: true);
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
        SystemEvents.UserPreferenceChanged -= OnSystemPreferenceChanged;
        _clock.Stop();
        _autoRefresh.Stop();
        _freshTimer.Stop();
        SystemProxy.Restore();
        _core.Dispose();
    }
}
