//! Windows integration: system proxy, administrator rights, single instance,
//! opening links. Other platforms get harmless stand-ins (for development).

use std::path::PathBuf;

use serde::{Deserialize, Serialize};

use crate::settings::data_dir;

fn proxy_backup_path() -> PathBuf {
    data_dir().join("proxy-backup.json")
}

/// Same shape as the C# version's backup, so a leftover file is restored too.
#[derive(Serialize, Deserialize, Default)]
#[serde(rename_all = "PascalCase", default)]
struct ProxySnapshot {
    proxy_enable: u32,
    proxy_server: Option<String>,
    proxy_override: Option<String>,
    auto_config_url: Option<String>,
}

#[cfg(windows)]
mod win {
    use super::*;
    use std::fs;
    use winreg::enums::{HKEY_CURRENT_USER, KEY_READ, KEY_WRITE};
    use winreg::RegKey;

    const KEY: &str = r"Software\Microsoft\Windows\CurrentVersion\Internet Settings";
    const BYPASS: &str = "localhost;127.*;10.*;172.16.*;172.17.*;172.18.*;172.19.*;172.20.*;172.21.*;172.22.*;172.23.*;\
        172.24.*;172.25.*;172.26.*;172.27.*;172.28.*;172.29.*;172.30.*;172.31.*;192.168.*;<local>";

    fn refresh() {
        use windows::Win32::Networking::WinInet::{InternetSetOptionW, INTERNET_OPTION_REFRESH, INTERNET_OPTION_SETTINGS_CHANGED};
        unsafe {
            let _ = InternetSetOptionW(None, INTERNET_OPTION_SETTINGS_CHANGED, None, 0);
            let _ = InternetSetOptionW(None, INTERNET_OPTION_REFRESH, None, 0);
        }
    }

    pub fn enable_proxy(host: &str, port: u16) -> Result<(), String> {
        let key = RegKey::predef(HKEY_CURRENT_USER)
            .open_subkey_with_flags(KEY, KEY_READ | KEY_WRITE)
            .map_err(|e| format!("Paramètres Internet Windows inaccessibles : {e}"))?;
        let server = format!("{host}:{port}");
        let current: Option<String> = key.get_value("ProxyServer").ok();
        // Back up only when the current proxy isn't already ours (crash recovery).
        if !proxy_backup_path().exists() && current.as_deref() != Some(server.as_str()) {
            let snap = ProxySnapshot {
                proxy_enable: key.get_value("ProxyEnable").unwrap_or(0),
                proxy_server: current,
                proxy_override: key.get_value("ProxyOverride").ok(),
                auto_config_url: key.get_value("AutoConfigURL").ok(),
            };
            let _ = fs::write(proxy_backup_path(), serde_json::to_string(&snap).unwrap_or_default());
        }
        key.set_value("ProxyEnable", &1u32).map_err(|e| e.to_string())?;
        key.set_value("ProxyServer", &server).map_err(|e| e.to_string())?;
        key.set_value("ProxyOverride", &BYPASS).map_err(|e| e.to_string())?;
        let _ = key.delete_value("AutoConfigURL");
        refresh();
        Ok(())
    }

    /// Puts back the settings saved by `enable_proxy`. Safe to call repeatedly.
    pub fn restore_proxy() {
        let path = proxy_backup_path();
        let Ok(text) = fs::read_to_string(&path) else { return };
        let snap: ProxySnapshot = serde_json::from_str(&text).unwrap_or_default();
        let Ok(key) = RegKey::predef(HKEY_CURRENT_USER).open_subkey_with_flags(KEY, KEY_READ | KEY_WRITE) else {
            return;
        };
        let _ = key.set_value("ProxyEnable", &snap.proxy_enable);
        let set = |name: &str, value: &Option<String>| match value {
            Some(v) => {
                let _ = key.set_value(name, v);
            }
            None => {
                let _ = key.delete_value(name);
            }
        };
        set("ProxyServer", &snap.proxy_server);
        set("ProxyOverride", &snap.proxy_override);
        set("AutoConfigURL", &snap.auto_config_url);
        let _ = fs::remove_file(&path);
        refresh();
    }

    pub fn is_admin() -> bool {
        unsafe { windows::Win32::UI::Shell::IsUserAnAdmin().as_bool() }
    }

    fn wide(s: &str) -> Vec<u16> {
        s.encode_utf16().chain(std::iter::once(0)).collect()
    }

    fn shell_execute(verb: &str, file: &str, args: &str) -> bool {
        use windows::core::PCWSTR;
        use windows::Win32::UI::Shell::ShellExecuteW;
        use windows::Win32::UI::WindowsAndMessaging::SW_SHOWNORMAL;
        let (verb, file, args) = (wide(verb), wide(file), wide(args));
        let result = unsafe {
            ShellExecuteW(
                None,
                PCWSTR(verb.as_ptr()),
                PCWSTR(file.as_ptr()),
                PCWSTR(args.as_ptr()),
                PCWSTR::null(),
                SW_SHOWNORMAL,
            )
        };
        result.0 as isize > 32
    }

    /// Starts a new elevated instance. False when the UAC prompt was declined.
    pub fn start_elevated(args: &str) -> bool {
        let Ok(exe) = std::env::current_exe() else { return false };
        shell_execute("runas", &exe.to_string_lossy(), args)
    }

    pub fn relaunch(args: &str) -> bool {
        let Ok(exe) = std::env::current_exe() else { return false };
        shell_execute("open", &exe.to_string_lossy(), args)
    }

    pub fn open_url(url: &str) {
        if url.starts_with("https://") || url.starts_with("http://") {
            shell_execute("open", url, "");
        }
    }

    // ---------------------------------------------------------------- single instance

    use windows::core::PCWSTR;
    use windows::Win32::Foundation::{HANDLE, WAIT_ABANDONED, WAIT_OBJECT_0};
    use windows::Win32::System::Threading::{
        CreateEventW, CreateMutexW, OpenEventW, SetEvent, WaitForSingleObject, EVENT_MODIFY_STATE, INFINITE,
    };

    const INSTANCE: &str = "VoidRay.SingleInstance";
    const SHOW: &str = "VoidRay.ShowWindow";

    /// True when this is the only instance. Otherwise the running one is asked to show itself.
    /// After "restart as administrator" the previous instance may still be closing: `wait_ms` covers that.
    pub fn claim_single_instance(wait_ms: u32) -> bool {
        let name = wide(INSTANCE);
        unsafe {
            let Ok(mutex) = CreateMutexW(None, false, PCWSTR(name.as_ptr())) else { return true };
            let r = WaitForSingleObject(mutex, wait_ms);
            // The handle stays open (and owned) for the life of the process.
            if r == WAIT_OBJECT_0 || r == WAIT_ABANDONED {
                return true;
            }
            let ev = wide(SHOW);
            if let Ok(event) = OpenEventW(EVENT_MODIFY_STATE, false, PCWSTR(ev.as_ptr())) {
                let _ = SetEvent(event);
            }
            false
        }
    }

    /// Calls `on_show` whenever another launch asks this instance to come forward.
    pub fn listen_for_show(on_show: impl Fn() + Send + 'static) {
        let ev = wide(SHOW);
        let event: HANDLE = match unsafe { CreateEventW(None, false, false, PCWSTR(ev.as_ptr())) } {
            Ok(h) => h,
            Err(_) => return,
        };
        let raw = event.0 as isize;
        std::thread::spawn(move || {
            let event = HANDLE(raw as *mut core::ffi::c_void);
            loop {
                if unsafe { WaitForSingleObject(event, INFINITE) } != WAIT_OBJECT_0 {
                    break;
                }
                on_show();
            }
        });
    }
}

#[cfg(windows)]
pub use win::*;

#[cfg(not(windows))]
mod other {
    pub fn enable_proxy(_host: &str, _port: u16) -> Result<(), String> {
        Ok(())
    }
    pub fn restore_proxy() {}
    pub fn is_admin() -> bool {
        true
    }
    pub fn start_elevated(_args: &str) -> bool {
        false
    }
    pub fn relaunch(_args: &str) -> bool {
        false
    }
    pub fn open_url(url: &str) {
        let _ = std::process::Command::new("xdg-open").arg(url).spawn();
    }
    pub fn claim_single_instance(_wait_ms: u32) -> bool {
        true
    }
    pub fn listen_for_show(_on_show: impl Fn() + Send + 'static) {}
}

#[cfg(not(windows))]
pub use other::*;

/// Command without a console window (Windows) for the core processes.
pub fn hidden_command(exe: &std::path::Path) -> std::process::Command {
    #[allow(unused_mut)]
    let mut cmd = std::process::Command::new(exe);
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
    }
    cmd
}
