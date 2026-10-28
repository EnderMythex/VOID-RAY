//! Settings in %APPDATA%\VoidRay\settings.json — the same file (and keys) the
//! previous C# version used, so an update keeps the user's link and choices.

use std::collections::HashMap;
use std::fs;
use std::path::PathBuf;

use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::models::Subscription;

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "PascalCase", default)]
pub struct Settings {
    pub subscription_url: String,
    pub selected_server_key: Option<String>,
    /// "tun" = the whole PC (games included), "proxy" = Windows system proxy only.
    pub mode: String,
    pub socks_port: u16,
    pub http_port: u16,
    /// Kept as raw JSON: an older cache that doesn't fit is simply ignored.
    pub cached_subscription: Option<Value>,
    /// "auto", "dark" or "light".
    pub theme: String,
    pub accent: i32,
    /// "auto", "en" or "fr".
    pub language: String,
    /// Usage snapshots per subscription id: [unix ms, used bytes].
    pub usage_history: HashMap<String, Vec<[i64; 2]>>,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            subscription_url: String::new(),
            selected_server_key: None,
            mode: "tun".into(),
            socks_port: 10808,
            http_port: 10809,
            cached_subscription: None,
            theme: "auto".into(),
            accent: 0,
            language: "auto".into(),
            usage_history: HashMap::new(),
        }
    }
}

pub fn data_dir() -> PathBuf {
    let base = std::env::var_os("APPDATA")
        .map(PathBuf::from)
        .or_else(|| std::env::var_os("HOME").map(|h| PathBuf::from(h).join(".config")))
        .unwrap_or_else(std::env::temp_dir);
    let dir = base.join("VoidRay");
    let _ = fs::create_dir_all(&dir);
    dir
}

pub fn core_dir() -> PathBuf {
    let dir = data_dir().join("core");
    let _ = fs::create_dir_all(&dir);
    dir
}

fn settings_path() -> PathBuf {
    data_dir().join("settings.json")
}

impl Settings {
    pub fn load() -> Self {
        fs::read_to_string(settings_path())
            .ok()
            .and_then(|text| serde_json::from_str::<Settings>(&text).ok())
            .unwrap_or_default()
    }

    pub fn save(&self) {
        let path = settings_path();
        let tmp = path.with_extension("json.tmp");
        if let Ok(text) = serde_json::to_string_pretty(self) {
            if fs::write(&tmp, text).is_ok() {
                let _ = fs::rename(&tmp, &path);
            }
        }
    }

    pub fn cached(&self) -> Option<Subscription> {
        self.cached_subscription
            .clone()
            .and_then(|v| serde_json::from_value::<Subscription>(v).ok())
    }

    pub fn set_cached(&mut self, sub: Option<&Subscription>) {
        self.cached_subscription = sub.and_then(|s| serde_json::to_value(s).ok());
    }
}

/// Keeps one usage point every 10 minutes (or when the counter moves), for 16 days.
pub fn record_usage(history: &mut Vec<[i64; 2]>, used: i64) {
    let now = chrono::Utc::now().timestamp_millis();
    if let Some(last) = history.last() {
        if now - last[0] < 600_000 && last[1] == used {
            return;
        }
    }
    history.push([now, used]);
    let cutoff = now - 16 * 86_400_000;
    history.retain(|e| e[0] >= cutoff);
    if history.len() > 400 {
        let extra = history.len() - 400;
        history.drain(..extra);
    }
}
