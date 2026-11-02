//! Latency tests, exit-IP check, free local ports, GitHub updates.

use std::net::{SocketAddr, TcpListener};
use std::time::{Duration, Instant};

use serde::Serialize;
use serde_json::Value;

/// TCP handshake latency to the server, in ms (None on failure).
/// Best of three TCP handshakes, the name resolved once: the first handshake pays for cold
/// caches and one slow packet shouldn't decide a server's latency. A dead server stops at
/// the first timeout.
pub async fn tcp_ping(host: &str, port: u16) -> Option<u32> {
    let lookup = tokio::time::timeout(Duration::from_secs(3), tokio::net::lookup_host((host, port)));
    let addrs: Vec<SocketAddr> = lookup.await.ok()?.ok()?.collect();
    let addr = addrs.iter().find(|a| a.is_ipv4()).or_else(|| addrs.first()).copied()?;
    let mut best: Option<u32> = None;
    for _ in 0..3 {
        let started = Instant::now();
        match tokio::time::timeout(Duration::from_secs(2), tokio::net::TcpStream::connect(addr)).await {
            Ok(Ok(_)) => {
                let ms = started.elapsed().as_millis().max(1) as u32;
                best = Some(best.map_or(ms, |b| b.min(ms)));
            }
            _ if best.is_none() => return None,
            _ => {}
        }
    }
    best
}

/// Resolves a host name to an IPv4 address (before the TUN exists).
pub async fn resolve_v4(host: &str) -> Option<String> {
    let addrs: Vec<SocketAddr> = tokio::net::lookup_host((host, 443)).await.ok()?.collect();
    addrs.iter().find(|a| a.is_ipv4()).or_else(|| addrs.first()).map(|a| a.ip().to_string())
}

#[derive(Clone, Debug, Serialize)]
pub struct ExitInfo {
    pub ip: String,
    pub country: Option<String>,
    pub code: Option<String>,
    pub city: Option<String>,
    pub region: Option<String>,
    pub delay: u32,
}

/// Public IP seen through the local proxy: proves the tunnel works end to end.
pub async fn check_exit(http_port: u16) -> Option<ExitInfo> {
    let proxy = reqwest::Proxy::all(format!("http://127.0.0.1:{http_port}")).ok()?;
    let http = reqwest::Client::builder()
        .proxy(proxy)
        .timeout(Duration::from_secs(10))
        .user_agent("VoidRay/1.0")
        .build()
        .ok()?;
    let started = Instant::now();
    if let Ok(res) = http.get("https://ipwho.is/?fields=ip,country,country_code,region,city").send().await {
        if let Ok(v) = res.json::<Value>().await {
            if let Some(ip) = v.get("ip").and_then(Value::as_str) {
                let text = |k: &str| v.get(k).and_then(Value::as_str).filter(|s| !s.is_empty()).map(str::to_string);
                return Some(ExitInfo {
                    ip: ip.to_string(),
                    country: text("country"),
                    code: text("country_code"),
                    city: text("city"),
                    region: text("region"),
                    delay: started.elapsed().as_millis() as u32,
                });
            }
        }
    }
    let started = Instant::now();
    let ip = http.get("https://api.ipify.org").send().await.ok()?.text().await.ok()?;
    Some(ExitInfo {
        ip: ip.trim().to_string(),
        country: None,
        code: None,
        city: None,
        region: None,
        delay: started.elapsed().as_millis() as u32,
    })
}

/// Client for the live latency: it keeps its connection through the tunnel open, so after
/// the first probe each one measures a round trip, not new handshakes.
pub fn tunnel_client(http_port: u16) -> Option<reqwest::Client> {
    reqwest::Client::builder()
        .proxy(reqwest::Proxy::all(format!("http://127.0.0.1:{http_port}")).ok()?)
        .timeout(Duration::from_secs(8))
        .pool_idle_timeout(Duration::from_secs(60))
        .user_agent("VoidRay/1.0")
        .build()
        .ok()
}

/// One tiny request through the tunnel (an empty 204 page), in ms.
pub async fn latency(client: &reqwest::Client) -> Option<u32> {
    let started = Instant::now();
    let res = client.get("https://www.gstatic.com/generate_204").send().await.ok()?;
    res.status().is_success().then(|| started.elapsed().as_millis().max(1) as u32)
}

/// Games that school or office firewalls usually block, reached directly (VPN off) over
/// HTTPS only. None when the network itself is down: nothing can be concluded then.
pub async fn blocked_services() -> Option<Vec<&'static str>> {
    let (google, cloudflare) = tokio::join!(tcp_ping("www.google.com", 443), tcp_ping("1.1.1.1", 443));
    if google.is_none() && cloudflare.is_none() {
        return None;
    }
    let (roblox, minecraft) = tokio::join!(tcp_ping("www.roblox.com", 443), tcp_ping("api.minecraftservices.com", 443));
    let mut blocked = Vec::new();
    for (name, ms) in [("Roblox", roblox), ("Minecraft", minecraft)] {
        if ms.is_none() {
            blocked.push(name);
        }
    }
    Some(blocked)
}

pub fn free_port(preferred: u16) -> u16 {
    if TcpListener::bind(("127.0.0.1", preferred)).is_ok() {
        return preferred;
    }
    TcpListener::bind(("127.0.0.1", 0))
        .and_then(|l| l.local_addr())
        .map(|a| a.port())
        .unwrap_or(preferred)
}

/// Waits until something listens on the local port (or the deadline passes).
pub async fn wait_for_port(port: u16, timeout: Duration, mut alive: impl FnMut() -> Result<(), String>) -> Result<(), String> {
    let deadline = Instant::now() + timeout;
    while Instant::now() < deadline {
        alive()?;
        if tokio::net::TcpStream::connect(("127.0.0.1", port)).await.is_ok() {
            return Ok(());
        }
        tokio::time::sleep(Duration::from_millis(150)).await;
    }
    Err("Xray ne répond pas sur le port local.".into())
}

// ------------------------------------------------------------------ updates

pub const REPO: &str = "EnderMythex/VOID-RAY";
pub const ASSET: &str = "VoidRay-win-x64.exe";

#[derive(Clone, Debug, Serialize)]
pub struct Release {
    pub tag: String,
    #[serde(skip)]
    pub version: (u32, u32, u32),
    pub url: Option<String>,
    pub page: String,
    pub notes: String,
}

pub fn parse_version(text: &str) -> Option<(u32, u32, u32)> {
    let mut it = text.trim().trim_start_matches(['v', 'V']).split('.').map(|p| p.parse::<u32>().ok());
    Some((it.next()??, it.next().flatten().unwrap_or(0), it.next().flatten().unwrap_or(0)))
}

pub fn current_version() -> (u32, u32, u32) {
    parse_version(env!("CARGO_PKG_VERSION")).unwrap_or((0, 0, 0))
}

pub async fn latest_release() -> Option<Release> {
    let res = reqwest::Client::builder()
        .timeout(Duration::from_secs(15))
        .build()
        .ok()?
        .get(format!("https://api.github.com/repos/{REPO}/releases/latest"))
        .header("User-Agent", concat!("VoidRay/", env!("CARGO_PKG_VERSION")))
        .header("Accept", "application/vnd.github+json")
        .send()
        .await
        .ok()?;
    if !res.status().is_success() {
        return None;
    }
    let v: Value = res.json().await.ok()?;
    let tag = v.get("tag_name")?.as_str()?.to_string();
    let version = parse_version(&tag)?;
    let url = v.get("assets")?.as_array()?.iter().find_map(|a| {
        let name = a.get("name")?.as_str()?;
        name.eq_ignore_ascii_case(ASSET).then(|| a.get("browser_download_url")?.as_str().map(str::to_string))?
    });
    Some(Release {
        tag,
        version,
        url,
        page: v
            .get("html_url")
            .and_then(Value::as_str)
            .map(str::to_string)
            .unwrap_or_else(|| format!("https://github.com/{REPO}/releases/latest")),
        notes: v.get("body").and_then(Value::as_str).unwrap_or("").to_string(),
    })
}

/// Downloads the new exe next to the running one and swaps them (a running exe
/// can't be overwritten but can be renamed; the old file is removed next start).
pub async fn install_release(url: &str, mut progress: impl FnMut(u32)) -> Result<(), String> {
    let exe = std::env::current_exe().map_err(|e| e.to_string())?;
    let dir = exe.parent().ok_or("dossier de l'application inconnu")?.to_path_buf();
    let incoming = dir.join("VoidRay.update.exe");

    let mut res = reqwest::Client::builder()
        .timeout(Duration::from_secs(600))
        .build()
        .map_err(|e| e.to_string())?
        .get(url)
        .header("User-Agent", "VoidRay")
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !res.status().is_success() {
        return Err(format!("HTTP {}", res.status().as_u16()));
    }
    let total = res.content_length().unwrap_or(0);
    let mut data = Vec::with_capacity(total as usize);
    while let Some(chunk) = res.chunk().await.map_err(|e| e.to_string())? {
        data.extend_from_slice(&chunk);
        if total > 0 {
            progress((data.len() as u64 * 100 / total) as u32);
        }
    }
    std::fs::write(&incoming, &data).map_err(|e| e.to_string())?;

    let old = exe.with_extension("exe.old");
    let _ = std::fs::remove_file(&old);
    std::fs::rename(&exe, &old).map_err(|e| e.to_string())?;
    if let Err(e) = std::fs::rename(&incoming, &exe) {
        let _ = std::fs::rename(&old, &exe); // put the running version back
        return Err(e.to_string());
    }
    Ok(())
}

/// Deletes what the last update left: the previous exe and an unfinished download. Right
/// after an update the old process is still finishing its exit and Windows keeps its exe
/// locked for a moment, so this keeps trying in the background (up to 30 s).
pub fn cleanup_old_version() {
    let Ok(exe) = std::env::current_exe() else { return };
    let leftovers = [exe.with_extension("exe.old"), exe.with_file_name("VoidRay.update.exe")];
    if leftovers.iter().all(|p| !p.exists()) {
        return;
    }
    std::thread::spawn(move || {
        for _ in 0..60 {
            for path in leftovers.iter().filter(|p| p.exists()) {
                let _ = std::fs::remove_file(path);
            }
            if leftovers.iter().all(|p| !p.exists()) {
                return;
            }
            std::thread::sleep(Duration::from_millis(500));
        }
    });
}
