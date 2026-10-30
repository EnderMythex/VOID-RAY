//! Application state and the commands the interface calls.

use std::collections::VecDeque;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager};

use crate::config;
use crate::cores::{self, CoreProcess};
use crate::models::{ServerProfile, Subscription};
use crate::net::{self, ExitInfo, Release};
use crate::settings::{self, Settings};
use crate::subscription::{self, FetchError};
use crate::system;
use crate::tray;

#[derive(Clone, Copy, PartialEq, Eq, Serialize, Debug)]
#[serde(rename_all = "lowercase")]
pub enum ConnState {
    Disconnected,
    Connecting,
    Connected,
    Disconnecting,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerItem {
    pub key: String,
    pub name: String,
    pub full_name: String,
    pub protocol: String,
    pub network: String,
    pub security: Option<String>,
    pub bypass: bool,
    pub ping: Option<u32>,
    /// "unknown", "testing", "good", "medium", "bad" or "timeout".
    pub quality: String,
    pub raw_link: Option<String>,
    #[serde(skip)]
    pub profile: ServerProfile,
}

impl ServerItem {
    fn new(profile: ServerProfile) -> Self {
        // 3x-ui appends "-email" to every remark: keep the readable part.
        static EMAIL: std::sync::LazyLock<regex::Regex> = std::sync::LazyLock::new(|| {
            regex::Regex::new(r"\s*[-–—|]\s*[^\s@]+@[^\s@]+\.[A-Za-z]{2,}\s*$").unwrap()
        });
        let name = EMAIL.replace(&profile.remark, "").trim().to_string();
        let network = match profile.network.to_ascii_lowercase().as_str() {
            "raw" => "TCP".to_string(),
            "splithttp" => "XHTTP".to_string(),
            n => n.to_ascii_uppercase(),
        };
        Self {
            key: profile.key(),
            name: if name.is_empty() { profile.remark.clone() } else { name },
            full_name: profile.remark.clone(),
            protocol: if profile.protocol == "shadowsocks" { "SS".into() } else { profile.protocol.to_ascii_uppercase() },
            network,
            security: Some(profile.security.to_ascii_uppercase()).filter(|s| !s.is_empty() && s != "NONE"),
            bypass: profile.is_bypass_patched,
            ping: None,
            quality: "unknown".into(),
            raw_link: profile.raw_link.clone(),
            profile,
        }
    }

    fn set_ping(&mut self, ms: Option<u32>) {
        self.ping = ms;
        self.quality = match ms {
            None => "timeout",
            Some(v) if v < 150 => "good",
            Some(v) if v < 350 => "medium",
            Some(_) => "bad",
        }
        .into();
    }
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SubView {
    title: Option<String>,
    username: Option<String>,
    emails: Vec<String>,
    upload: i64,
    download: i64,
    total: i64,
    expire: Option<i64>,
    enabled: bool,
    last_online: Option<i64>,
    support_url: String,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Snapshot {
    authenticated: bool,
    gate_error: Option<String>,
    sub_url: String,
    sid: String,
    sub: Option<SubView>,
    servers: Vec<ServerItem>,
    selected_key: Option<String>,
    mode: String,
    state: ConnState,
    connected_at: Option<i64>,
    exit: Option<ExitInfo>,
    exit_failed: bool,
    refreshing: bool,
    refresh_failed: bool,
    last_refresh: i64,
    history: Vec<[i64; 2]>,
    theme: String,
    accent: i32,
    language: String,
    version: String,
    update: Option<Release>,
    checking_update: bool,
    is_admin: bool,
}

#[derive(Clone, Serialize)]
pub struct Toast {
    key: String,
    args: Vec<String>,
    error: bool,
}

struct Inner {
    settings: Settings,
    authenticated: bool,
    gate_error: Option<String>,
    sub: Option<Subscription>,
    servers: Vec<ServerItem>,
    state: ConnState,
    connected_at: Option<i64>,
    exit: Option<ExitInfo>,
    exit_failed: bool,
    refreshing: bool,
    refresh_failed: bool,
    last_refresh: i64,
    socks_port: u16,
    http_port: u16,
    logs: VecDeque<String>,
    update: Option<Release>,
    checking_update: bool,
    ui_lang: String,
}

pub struct Core {
    pub handle: AppHandle,
    inner: Mutex<Inner>,
    xray: CoreProcess,
    tun: CoreProcess,
}

pub type Shared = Arc<Core>;

fn now_ms() -> i64 {
    chrono::Utc::now().timestamp_millis()
}

impl Core {
    pub fn new(handle: AppHandle) -> Shared {
        let settings = Settings::load();
        let mut inner = Inner {
            socks_port: settings.socks_port,
            http_port: settings.http_port,
            settings,
            authenticated: false,
            gate_error: None,
            sub: None,
            servers: Vec::new(),
            state: ConnState::Disconnected,
            connected_at: None,
            exit: None,
            exit_failed: false,
            refreshing: false,
            refresh_failed: false,
            last_refresh: now_ms(),
            logs: VecDeque::new(),
            update: None,
            checking_update: false,
            ui_lang: "en".into(),
        };
        if let Some((link, _)) = config::normalize_link(&inner.settings.subscription_url) {
            inner.settings.subscription_url = link;
            inner.authenticated = true;
            if let Some(cached) = inner.settings.cached() {
                inner.last_refresh = cached.fetched_at.max(0);
                inner.servers = cached.servers.iter().cloned().map(ServerItem::new).collect();
                inner.sub = Some(cached);
                Self::fix_selection(&mut inner);
            }
        }
        let core = Arc::new(Core {
            handle,
            inner: Mutex::new(inner),
            xray: CoreProcess::new("xray"),
            tun: CoreProcess::new("sing-box"),
        });
        core.log("VOID-RAY prêt.".into());
        core
    }

    fn with<R>(&self, f: impl FnOnce(&mut Inner) -> R) -> R {
        f(&mut self.inner.lock().unwrap())
    }

    // ---------------------------------------------------------------- events

    pub fn snapshot(&self) -> Snapshot {
        self.with(|i| {
            let sid = i
                .sub
                .as_ref()
                .and_then(|s| s.sid.clone())
                .unwrap_or_else(|| last_segment(&i.settings.subscription_url));
            let history = i.settings.usage_history.get(if sid.is_empty() { "default" } else { &sid }).cloned().unwrap_or_default();
            Snapshot {
                authenticated: i.authenticated,
                gate_error: i.gate_error.clone(),
                sub_url: i.settings.subscription_url.clone(),
                sub: i.sub.as_ref().map(|s| SubView {
                    title: s.title.clone(),
                    username: s.username.clone(),
                    emails: s.emails.clone(),
                    upload: s.upload,
                    download: s.download,
                    total: s.total,
                    expire: s.expire,
                    enabled: s.enabled,
                    last_online: s.last_online,
                    support_url: s.support_url.clone().filter(|u| !u.is_empty()).unwrap_or_else(|| config::DEFAULT_SUPPORT_URL.into()),
                }),
                sid,
                servers: i.servers.clone(),
                selected_key: i.settings.selected_server_key.clone(),
                mode: i.settings.mode.clone(),
                state: i.state,
                connected_at: i.connected_at,
                exit: i.exit.clone(),
                exit_failed: i.exit_failed,
                refreshing: i.refreshing,
                refresh_failed: i.refresh_failed,
                last_refresh: i.last_refresh,
                history,
                theme: i.settings.theme.clone(),
                accent: i.settings.accent,
                language: i.settings.language.clone(),
                version: format!("v{}", env!("CARGO_PKG_VERSION")),
                update: i.update.clone(),
                checking_update: i.checking_update,
                is_admin: system::is_admin(),
            }
        })
    }

    pub fn emit(&self) {
        let snap = self.snapshot();
        let _ = self.handle.emit("state", &snap);
        let (state, lang, auth) = self.with(|i| (i.state, i.ui_lang.clone(), i.authenticated));
        tray::refresh(&self.handle, state, &lang, auth);
    }

    pub fn log(&self, line: String) {
        let stamped = format!("{}  {line}", chrono::Local::now().format("%H:%M:%S"));
        self.with(|i| {
            i.logs.push_back(stamped.clone());
            while i.logs.len() > 500 {
                i.logs.pop_front();
            }
        });
        let _ = self.handle.emit("log", stamped);
    }

    fn toast(&self, key: &str, args: Vec<String>, error: bool) {
        let _ = self.handle.emit("toast", Toast { key: key.into(), args, error });
    }

    fn log_fn(self: &Arc<Self>) -> Arc<dyn Fn(String) + Send + Sync> {
        let me = Arc::downgrade(self);
        Arc::new(move |line| {
            if let Some(me) = me.upgrade() {
                me.log(line);
            }
        })
    }

    pub fn state(&self) -> ConnState {
        self.with(|i| i.state)
    }

    fn set_state(&self, s: ConnState) {
        self.with(|i| i.state = s);
        self.emit();
    }

    pub fn logs(&self) -> Vec<String> {
        self.with(|i| i.logs.iter().cloned().collect())
    }

    pub fn clear_logs(&self) {
        self.with(|i| i.logs.clear());
    }

    pub fn show_window(&self) {
        if let Some(w) = self.handle.get_webview_window("main") {
            let _ = w.show();
            let _ = w.unminimize();
            let _ = w.set_focus();
        }
    }

    // ---------------------------------------------------------------- subscription

    fn fix_selection(i: &mut Inner) {
        let keep = i.settings.selected_server_key.clone();
        let found = keep.as_ref().and_then(|k| i.servers.iter().find(|s| &s.key == k)).map(|s| s.key.clone());
        i.settings.selected_server_key = found.or_else(|| i.servers.first().map(|s| s.key.clone()));
    }

    async fn fetch(&self, link: &str) -> Result<Subscription, FetchError> {
        let (mut info, lines) = subscription::fetch(link).await?;
        for l in lines {
            self.log(l);
        }
        if info.sid.is_none() {
            info.sid = Some(last_segment(link));
        }
        let patched = config::apply_bypass(&mut info.servers);
        if patched > 0 {
            self.log(format!(
                "Firewall Bypass : {patched} serveur(s) reconfiguré(s) → {}:443 TLS, SNI xray.enderr.win.",
                config::BYPASS_IP
            ));
        }
        Ok(info)
    }

    fn apply(&self, info: Subscription, is_new: bool) {
        self.with(|i| {
            if is_new || i.servers.is_empty() {
                i.servers = info.servers.iter().cloned().map(ServerItem::new).collect();
                Self::fix_selection(i);
            }
            let key = info.sid.clone().filter(|s| !s.is_empty()).unwrap_or_else(|| "default".into());
            settings::record_usage(i.settings.usage_history.entry(key).or_default(), info.used());
            i.settings.set_cached(Some(&info));
            i.sub = Some(info);
            i.settings.save();
        });
    }

    pub async fn sign_in(self: &Arc<Self>, url: &str) -> Result<usize, String> {
        let Some((link, _)) = config::normalize_link(url) else { return Err("errFormat".into()) };
        let info = self.fetch(&link).await.map_err(|e| match e {
            FetchError::NotFound => "errNotFound".to_string(),
            FetchError::Network(m) => {
                self.log(format!("Erreur réseau : {m}"));
                "errNetwork".to_string()
            }
        })?;
        if info.servers.is_empty() {
            return Err("errEmpty".into());
        }
        let bypass = info.servers.iter().filter(|s| s.is_bypass_patched).count();
        let count = info.servers.len();
        self.with(|i| {
            i.settings.subscription_url = link;
            i.authenticated = true;
            i.gate_error = None;
            i.refresh_failed = false;
            i.last_refresh = now_ms();
        });
        self.apply(info, true);
        self.emit();
        self.toast(if bypass > 0 { "bypassApplied" } else { "loaded" }, vec![count.to_string()], false);
        let me = self.clone();
        tauri::async_runtime::spawn(async move { me.ping_all().await });
        Ok(count)
    }

    pub async fn refresh(self: &Arc<Self>, manual: bool) -> Result<(), String> {
        let link = {
            let mut guard = self.inner.lock().unwrap();
            if !guard.authenticated || guard.refreshing {
                return Ok(());
            }
            guard.refreshing = true;
            guard.settings.subscription_url.clone()
        };
        self.emit();
        let result = self.fetch(&link).await;
        self.with(|i| i.refreshing = false);
        match result {
            Ok(info) => {
                let changed = self.with(|i| {
                    let old: Vec<String> = i.sub.as_ref().map(|s| s.servers.iter().map(|p| p.key()).collect()).unwrap_or_default();
                    old != info.servers.iter().map(|p| p.key()).collect::<Vec<_>>()
                });
                let count = info.servers.len();
                self.apply(info, changed);
                self.with(|i| {
                    i.refresh_failed = false;
                    i.last_refresh = now_ms();
                });
                self.emit();
                if manual {
                    self.toast("loaded", vec![count.to_string()], false);
                }
                if changed || manual {
                    let me = self.clone();
                    tauri::async_runtime::spawn(async move { me.ping_all().await });
                }
                Ok(())
            }
            Err(FetchError::NotFound) => {
                self.log("Abonnement introuvable : retour à l'écran de connexion.".into());
                if self.state() != ConnState::Disconnected {
                    self.disconnect().await;
                }
                self.sign_out(Some("errRevoked".into()));
                Ok(())
            }
            Err(FetchError::Network(m)) => {
                self.with(|i| i.refresh_failed = true);
                self.log(format!("Erreur de mise à jour : {m}"));
                self.emit();
                if manual { Err("errNetwork".into()) } else { Ok(()) }
            }
        }
    }

    pub async fn change_link(self: &Arc<Self>) {
        if self.state() != ConnState::Disconnected {
            self.disconnect().await;
        }
        self.sign_out(None);
    }

    fn sign_out(&self, error: Option<String>) {
        self.with(|i| {
            i.sub = None;
            i.servers.clear();
            i.authenticated = false;
            i.gate_error = error;
            i.settings.subscription_url.clear();
            i.settings.set_cached(None);
            i.settings.selected_server_key = None;
            i.settings.save();
        });
        self.emit();
    }

    pub async fn select_server(self: &Arc<Self>, key: String) {
        let changed = self.with(|i| {
            if !i.servers.iter().any(|s| s.key == key) || i.settings.selected_server_key.as_deref() == Some(key.as_str()) {
                return false;
            }
            i.settings.selected_server_key = Some(key);
            i.settings.save();
            true
        });
        self.emit();
        if changed && self.state() == ConnState::Connected {
            self.disconnect().await;
            let _ = self.connect().await;
        }
    }

    pub async fn ping_all(self: &Arc<Self>) {
        let targets: Vec<(String, String, u16)> = self.with(|i| {
            i.servers
                .iter_mut()
                .map(|s| {
                    s.quality = "testing".into();
                    (s.key.clone(), s.profile.address.clone(), s.profile.port)
                })
                .collect()
        });
        self.emit();
        let gate = Arc::new(tokio::sync::Semaphore::new(8));
        let mut set = tokio::task::JoinSet::new();
        for (key, host, port) in targets {
            let gate = gate.clone();
            set.spawn(async move {
                let _permit = gate.acquire().await;
                (key, net::tcp_ping(&host, port).await)
            });
        }
        while let Some(Ok((key, ms))) = set.join_next().await {
            self.with(|i| {
                if let Some(s) = i.servers.iter_mut().find(|s| s.key == key) {
                    s.set_ping(ms);
                }
            });
            self.emit();
        }
    }

    pub fn set_prefs(&self, theme: String, accent: i32, language: String, ui_lang: String) {
        self.with(|i| {
            i.settings.theme = theme;
            i.settings.accent = accent;
            i.settings.language = language;
            i.ui_lang = ui_lang;
            i.settings.save();
        });
        self.emit();
    }

    pub async fn set_mode(self: &Arc<Self>, mode: String) -> Result<(), String> {
        let mode = if mode == "proxy" { "proxy" } else { "tun" }.to_string();
        if self.with(|i| i.settings.mode == mode) {
            return Ok(());
        }
        let was_connected = self.state() == ConnState::Connected;
        if was_connected {
            self.disconnect().await;
        }
        self.with(|i| {
            i.settings.mode = mode;
            i.settings.save();
        });
        self.emit();
        if was_connected {
            self.connect().await?;
        }
        Ok(())
    }

    // ---------------------------------------------------------------- connection

    pub async fn connect(self: &Arc<Self>) -> Result<(), String> {
        if self.state() != ConnState::Disconnected {
            return Ok(());
        }
        let (server, tun) = self.with(|i| {
            let s = i.settings.selected_server_key.as_ref().and_then(|k| i.servers.iter().find(|s| &s.key == k)).cloned();
            (s, i.settings.mode != "proxy")
        });
        let Some(server) = server else { return Err("pickServer".into()) };
        // The TUN card needs administrator rights: the UI offers to relaunch elevated.
        if tun && !system::is_admin() {
            return Err("needAdmin".into());
        }

        self.set_state(ConnState::Connecting);
        match self.start_tunnel(&server, tun).await {
            Ok(()) => {
                self.with(|i| {
                    i.connected_at = Some(now_ms());
                    i.exit = None;
                    i.exit_failed = false;
                });
                self.set_state(ConnState::Connected);
                self.log(if tun { "Connecté. Mode TUN : tout le PC passe par le VPN." } else { "Connecté. Proxy système activé." }.into());
                let me = self.clone();
                tauri::async_runtime::spawn(async move { me.check_exit().await });
                Ok(())
            }
            Err(e) => {
                self.stop_all();
                self.set_state(ConnState::Disconnected);
                self.log(format!("Échec de connexion : {e}"));
                Err(format!("errConnect:{e}"))
            }
        }
    }

    async fn start_tunnel(self: &Arc<Self>, server: &ServerItem, tun: bool) -> Result<(), String> {
        let log = self.log_fn();
        let xray_exe = cores::ensure_xray(&log).await?;
        let tun_exe = if tun { Some(cores::ensure_sing_box(&log).await?) } else { None };

        let (pref_socks, pref_http) = self.with(|i| (i.settings.socks_port, i.settings.http_port));
        let socks = net::free_port(pref_socks);
        let http = net::free_port(pref_http);
        self.with(|i| {
            i.socks_port = socks;
            i.http_port = http;
        });

        let profile = if tun { self.pin_address(&server.profile).await } else { server.profile.clone() };
        let dir = settings::data_dir();
        let xray_cfg = dir.join("config.json");
        std::fs::write(&xray_cfg, config::xray_config(&profile, socks, http)?).map_err(|e| e.to_string())?;
        self.log(format!(
            "Connexion à {} ({} · {}{})…",
            server.name,
            server.protocol,
            server.network,
            server.security.as_ref().map(|s| format!(" · {s}")).unwrap_or_default()
        ));

        let me = Arc::downgrade(self);
        let on_crash: Arc<dyn Fn() + Send + Sync> = Arc::new(move || {
            if let Some(me) = me.upgrade() {
                tauri::async_runtime::spawn(async move { me.on_core_crashed() });
            }
        });
        let cfg = xray_cfg.to_string_lossy().into_owned();
        self.xray.start(&xray_exe, &["run", "-c", &cfg], log.clone(), on_crash.clone())?;
        net::wait_for_port(socks, Duration::from_secs(8), || match self.xray.exited() {
            Some(code) => Err(format!("Xray s'est arrêté (code {code}). Consulte le journal.")),
            None => Ok(()),
        })
        .await?;

        if let Some(tun_exe) = tun_exe {
            let tun_cfg = dir.join("tun.json");
            std::fs::write(&tun_cfg, config::tun_config(socks)).map_err(|e| e.to_string())?;
            let cfg = tun_cfg.to_string_lossy().into_owned();
            self.tun.start(&tun_exe, &["run", "-c", &cfg, "--disable-color"], log, on_crash)?;
            // The TUN card needs a moment; a bad config makes sing-box exit right away.
            for _ in 0..20 {
                tokio::time::sleep(Duration::from_millis(150)).await;
                if let Some(code) = self.tun.exited() {
                    return Err(format!("Le mode TUN n'a pas démarré (code {code}). Consulte le journal."));
                }
            }
        } else {
            system::enable_proxy("127.0.0.1", http)?;
        }
        Ok(())
    }

    /// In TUN mode Windows' DNS goes into the tunnel, so Xray couldn't resolve
    /// its own server without looping: resolve it now, keep the name for TLS.
    async fn pin_address(&self, p: &ServerProfile) -> ServerProfile {
        if p.raw_xray_json.is_some() || p.address.parse::<std::net::IpAddr>().is_ok() {
            return p.clone();
        }
        let Some(ip) = net::resolve_v4(&p.address).await else { return p.clone() };
        let mut copy = p.clone();
        if !p.security.eq_ignore_ascii_case("none") && copy.sni.is_none() {
            copy.sni = Some(p.address.clone());
        }
        if matches!(p.network.as_str(), "ws" | "httpupgrade" | "xhttp" | "splithttp") && copy.host.is_none() {
            copy.host = Some(p.address.clone());
        }
        self.log(format!("Serveur {} → {ip}", p.address));
        copy.address = ip;
        copy
    }

    fn stop_all(&self) {
        self.tun.stop();
        self.xray.stop();
        // After Xray is gone, so a leftover local proxy is seen as dead and cleared.
        system::restore_proxy();
    }

    pub async fn disconnect(self: &Arc<Self>) {
        if self.state() == ConnState::Disconnected {
            return;
        }
        self.set_state(ConnState::Disconnecting);
        let me = self.clone();
        let _ = tokio::task::spawn_blocking(move || me.stop_all()).await;
        self.with(|i| {
            i.connected_at = None;
            i.exit = None;
            i.exit_failed = false;
        });
        self.set_state(ConnState::Disconnected);
        self.log("Déconnecté.".into());
    }

    fn on_core_crashed(self: Arc<Self>) {
        if self.state() != ConnState::Connected {
            return;
        }
        self.stop_all();
        self.with(|i| {
            i.connected_at = None;
            i.exit = None;
        });
        self.set_state(ConnState::Disconnected);
        self.toast("errCore", vec![], true);
    }

    async fn check_exit(self: Arc<Self>) {
        let port = self.with(|i| i.http_port);
        let exit = net::check_exit(port).await;
        if self.state() != ConnState::Connected {
            return;
        }
        match exit {
            Some(e) => {
                self.log(format!("IP de sortie : {} {}", e.ip, e.country.clone().unwrap_or_default()));
                self.with(|i| i.exit = Some(e));
            }
            None => {
                self.log("Le test de connectivité a échoué : le serveur ne laisse pas passer le trafic.".into());
                self.with(|i| i.exit_failed = true);
                self.toast("errNoTraffic", vec![], true);
            }
        }
        self.emit();
    }

    /// Relaunches as administrator (for TUN). False when the UAC prompt was declined.
    pub fn restart_elevated(&self, connect: bool) -> bool {
        let args = if connect { "--elevated-restart --connect" } else { "--elevated-restart" };
        if system::start_elevated(args) {
            self.shutdown();
            self.handle.exit(0);
            true
        } else {
            false
        }
    }

    // ---------------------------------------------------------------- updates

    pub async fn check_update(&self, manual: bool) -> Result<Option<Release>, String> {
        self.with(|i| i.checking_update = true);
        self.emit();
        let latest = net::latest_release().await;
        self.with(|i| i.checking_update = false);
        let Some(latest) = latest else {
            self.emit();
            return if manual { Err("updFailed".into()) } else { Ok(None) };
        };
        let newer = latest.version > net::current_version();
        if newer {
            self.log(format!("Mise à jour disponible : {}.", latest.tag));
        }
        self.with(|i| i.update = newer.then_some(latest));
        self.emit();
        Ok(self.with(|i| i.update.clone()))
    }

    pub async fn install_update(self: &Arc<Self>) -> Result<(), String> {
        let Some(release) = self.with(|i| i.update.clone()) else { return Ok(()) };
        let Some(url) = release.url.clone() else {
            system::open_url(&release.page);
            return Ok(());
        };
        let handle = self.handle.clone();
        if let Err(e) = net::install_release(&url, |p| {
            let _ = handle.emit("updateProgress", p);
        })
        .await
        {
            self.log(format!("Échec de la mise à jour : {e}"));
            system::open_url(&release.page);
            return Err(format!("updInstallFailed:{e}"));
        }
        self.disconnect().await;
        system::relaunch("--elevated-restart");
        self.shutdown();
        self.handle.exit(0);
        Ok(())
    }

    /// Stops the tunnel and restores the proxy; called before the process exits.
    pub fn shutdown(&self) {
        self.stop_all();
    }

    pub fn spawn_background(self: &Arc<Self>, connect_now: bool) {
        // Subscription refresh every 2 minutes, like the previous version.
        let me = self.clone();
        tauri::async_runtime::spawn(async move {
            let _ = me.refresh(false).await;
            if me.with(|i| i.authenticated) {
                me.ping_all().await;
            }
            loop {
                tokio::time::sleep(Duration::from_secs(120)).await;
                let _ = me.refresh(false).await;
            }
        });
        let me = self.clone();
        tauri::async_runtime::spawn(async move {
            let _ = me.check_update(false).await;
        });
        if connect_now {
            let me = self.clone();
            tauri::async_runtime::spawn(async move {
                tokio::time::sleep(Duration::from_millis(800)).await;
                if let Err(e) = me.connect().await {
                    let (key, arg) = e.split_once(':').map(|(k, a)| (k.to_string(), vec![a.to_string()])).unwrap_or((e, vec![]));
                    me.toast(&key, arg, true);
                }
            });
        }
    }
}

fn last_segment(url: &str) -> String {
    url.trim_end_matches('/').rsplit('/').next().unwrap_or("").to_string()
}

// ==================================================================== commands

type S<'a> = tauri::State<'a, Shared>;

#[tauri::command]
pub fn get_state(core: S<'_>) -> Snapshot {
    core.snapshot()
}

#[tauri::command]
pub fn get_logs(core: S<'_>) -> Vec<String> {
    core.logs()
}

#[tauri::command]
pub fn clear_logs(core: S<'_>) {
    core.clear_logs();
}

#[tauri::command]
pub async fn sign_in(core: S<'_>, url: String) -> Result<usize, String> {
    core.inner().clone().sign_in(&url).await
}

#[tauri::command]
pub async fn refresh(core: S<'_>) -> Result<(), String> {
    core.inner().clone().refresh(true).await
}

#[tauri::command]
pub async fn change_link(core: S<'_>) -> Result<(), String> {
    core.inner().clone().change_link().await;
    Ok(())
}

#[tauri::command]
pub async fn select_server(core: S<'_>, key: String) -> Result<(), String> {
    core.inner().clone().select_server(key).await;
    Ok(())
}

#[tauri::command]
pub async fn ping_all(core: S<'_>) -> Result<(), String> {
    core.inner().clone().ping_all().await;
    Ok(())
}

#[tauri::command]
pub fn set_prefs(core: S<'_>, theme: String, accent: i32, language: String, ui_lang: String) {
    core.set_prefs(theme, accent, language, ui_lang);
}

#[tauri::command]
pub async fn set_mode(core: S<'_>, mode: String) -> Result<(), String> {
    core.inner().clone().set_mode(mode).await
}

#[tauri::command]
pub async fn connect(core: S<'_>) -> Result<(), String> {
    core.inner().clone().connect().await
}

#[tauri::command]
pub async fn disconnect(core: S<'_>) -> Result<(), String> {
    core.inner().clone().disconnect().await;
    Ok(())
}

#[tauri::command]
pub fn restart_elevated(core: S<'_>) -> Result<(), String> {
    if core.restart_elevated(true) { Ok(()) } else { Err("adminDeclined".into()) }
}

#[tauri::command]
pub async fn check_update(core: S<'_>, manual: bool) -> Result<Option<Release>, String> {
    core.check_update(manual).await
}

#[tauri::command]
pub async fn install_update(core: S<'_>) -> Result<(), String> {
    core.inner().clone().install_update().await
}

#[tauri::command]
pub fn open_url(url: String) {
    system::open_url(&url);
}

#[tauri::command]
pub fn quit(core: S<'_>) {
    core.shutdown();
    core.handle.exit(0);
}
