//! xray-core (the tunnel) and sing-box (the TUN card): download, start, stop.

use std::fs;
use std::io::{BufRead, BufReader, Read};
use std::path::{Path, PathBuf};
use std::process::{Child, Stdio};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use crate::settings::core_dir;
use crate::system::hidden_command;

pub const SING_BOX_VERSION: &str = "1.14.2";

type LogFn = Arc<dyn Fn(String) + Send + Sync>;
type CrashFn = Arc<dyn Fn() + Send + Sync>;

/// One background process whose output goes to the log and whose unexpected exit is reported.
pub struct CoreProcess {
    name: &'static str,
    child: Arc<Mutex<Option<Child>>>,
    stopping: Arc<AtomicBool>,
}

impl CoreProcess {
    pub fn new(name: &'static str) -> Self {
        Self { name, child: Arc::new(Mutex::new(None)), stopping: Arc::new(AtomicBool::new(false)) }
    }

    pub fn start(&self, exe: &Path, args: &[&str], log: LogFn, on_crash: CrashFn) -> Result<(), String> {
        self.stop();
        self.stopping.store(false, Ordering::SeqCst);
        let mut cmd = hidden_command(exe);
        cmd.args(args)
            .current_dir(exe.parent().unwrap_or(Path::new(".")))
            .env("XRAY_LOCATION_ASSET", exe.parent().unwrap_or(Path::new(".")))
            .stdin(Stdio::null())
            .stdout(Stdio::piped())
            .stderr(Stdio::piped());
        let mut child = cmd.spawn().map_err(|e| format!("Impossible de démarrer {} : {e}", self.name))?;

        let prefix = if self.name == "sing-box" { "[tun] " } else { "" };
        for stream in [child.stdout.take().map(|s| Box::new(s) as Box<dyn Read + Send>),
                       child.stderr.take().map(|s| Box::new(s) as Box<dyn Read + Send>)]
            .into_iter()
            .flatten()
        {
            let log = log.clone();
            std::thread::spawn(move || {
                for line in BufReader::new(stream).lines().map_while(Result::ok) {
                    if !line.trim().is_empty() {
                        log(format!("{prefix}{line}"));
                    }
                }
            });
        }
        *self.child.lock().unwrap() = Some(child);

        // Watch for an exit nobody asked for.
        let (slot, stopping) = (self.child.clone(), self.stopping.clone());
        std::thread::spawn(move || loop {
            std::thread::sleep(Duration::from_millis(400));
            let mut guard = slot.lock().unwrap();
            let Some(child) = guard.as_mut() else { return };
            if let Ok(Some(_)) = child.try_wait() {
                *guard = None;
                drop(guard);
                if !stopping.load(Ordering::SeqCst) {
                    on_crash();
                }
                return;
            }
        });
        Ok(())
    }

    /// Exit code if the process already died.
    pub fn exited(&self) -> Option<i32> {
        let mut guard = self.child.lock().unwrap();
        match guard.as_mut() {
            None => Some(-1),
            Some(c) => c.try_wait().ok().flatten().map(|s| s.code().unwrap_or(-1)),
        }
    }

    pub fn stop(&self) {
        self.stopping.store(true, Ordering::SeqCst);
        if let Some(mut child) = self.child.lock().unwrap().take() {
            // sing-box must shut down cleanly or Windows keeps its routes and
            // firewall rules and the PC stays offline: ask first, kill if it hangs.
            if self.name == "sing-box" && crate::system::interrupt_process(child.id()) && wait_exit(&mut child, Duration::from_secs(4)) {
                return;
            }
            let _ = child.kill();
            wait_exit(&mut child, Duration::from_secs(3));
        }
    }
}

fn wait_exit(child: &mut Child, timeout: Duration) -> bool {
    let deadline = Instant::now() + timeout;
    while Instant::now() < deadline {
        if let Ok(Some(_)) = child.try_wait() {
            return true;
        }
        std::thread::sleep(Duration::from_millis(50));
    }
    false
}

// ------------------------------------------------------------------ install

fn exe_name(base: &str) -> String {
    if cfg!(windows) { format!("{base}.exe") } else { base.to_string() }
}

pub fn find_xray() -> Option<PathBuf> {
    let name = exe_name("xray");
    let here = std::env::current_exe().ok().and_then(|p| p.parent().map(Path::to_path_buf));
    let mut candidates = Vec::new();
    if let Some(dir) = here {
        candidates.push(dir.join("core").join(&name));
        candidates.push(dir.join(&name));
    }
    candidates.push(core_dir().join(&name));
    candidates.into_iter().find(|p| p.exists())
}

async fn download(url: &str) -> Result<Vec<u8>, String> {
    let res = reqwest::Client::builder()
        .timeout(Duration::from_secs(300))
        .build()
        .map_err(|e| e.to_string())?
        .get(url)
        .header("User-Agent", "VoidRay/1.0")
        .send()
        .await
        .map_err(|e| e.to_string())?;
    if !res.status().is_success() {
        return Err(format!("HTTP {} ({url})", res.status().as_u16()));
    }
    Ok(res.bytes().await.map_err(|e| e.to_string())?.to_vec())
}

/// Extracts every file of the archive (flattening folders) into `dir`.
fn unzip_flat(bytes: &[u8], dir: &Path) -> Result<(), String> {
    let mut zip = zip::ZipArchive::new(std::io::Cursor::new(bytes)).map_err(|e| e.to_string())?;
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i).map_err(|e| e.to_string())?;
        if entry.is_dir() {
            continue;
        }
        let Some(name) = Path::new(entry.name()).file_name().map(|n| n.to_owned()) else { continue };
        let mut out = fs::File::create(dir.join(name)).map_err(|e| e.to_string())?;
        std::io::copy(&mut entry, &mut out).map_err(|e| e.to_string())?;
    }
    Ok(())
}

pub async fn ensure_xray(log: &LogFn) -> Result<PathBuf, String> {
    if let Some(p) = find_xray() {
        return Ok(p);
    }
    let asset = match std::env::consts::ARCH {
        "aarch64" => "Xray-windows-arm64-v8a.zip",
        "x86" => "Xray-windows-32.zip",
        _ => "Xray-windows-64.zip",
    };
    log(format!("Moteur Xray absent, téléchargement de {asset}…"));
    let bytes = download(&format!("https://github.com/XTLS/Xray-core/releases/latest/download/{asset}")).await?;
    let dir = core_dir();
    tokio::task::spawn_blocking(move || unzip_flat(&bytes, &dir)).await.map_err(|e| e.to_string())??;
    log("Moteur Xray installé.".into());
    find_xray().ok_or_else(|| "xray.exe introuvable après l'installation.".into())
}

pub async fn ensure_sing_box(log: &LogFn) -> Result<PathBuf, String> {
    let exe = core_dir().join(exe_name("sing-box"));
    let version_file = core_dir().join("sing-box.version");
    if exe.exists() && fs::read_to_string(&version_file).map(|v| v.trim() == SING_BOX_VERSION).unwrap_or(false) {
        return Ok(exe);
    }
    let arch = match std::env::consts::ARCH {
        "aarch64" => "arm64",
        "x86" => "386",
        _ => "amd64",
    };
    log(format!("Moteur TUN absent, téléchargement de sing-box {SING_BOX_VERSION} ({arch})…"));
    let url = format!(
        "https://github.com/SagerNet/sing-box/releases/download/v{v}/sing-box-{v}-windows-{arch}.zip",
        v = SING_BOX_VERSION
    );
    let bytes = download(&url).await?;
    let dir = core_dir();
    tokio::task::spawn_blocking(move || unzip_flat(&bytes, &dir)).await.map_err(|e| e.to_string())??;
    if !exe.exists() {
        return Err("sing-box.exe introuvable dans l'archive.".into());
    }
    let _ = fs::write(&version_file, SING_BOX_VERSION);
    log("Moteur TUN installé.".into());
    Ok(exe)
}
