use serde::{Deserialize, Serialize};

/// A single proxy endpoint parsed from the subscription.
#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(rename_all = "PascalCase", default)]
pub struct ServerProfile {
    pub protocol: String,
    pub remark: String,
    pub address: String,
    pub port: u16,

    // Credentials
    /// uuid (vless/vmess) or password (trojan/ss)
    pub id: Option<String>,
    /// shadowsocks cipher
    pub method: Option<String>,
    pub flow: Option<String>,
    pub encryption: Option<String>,
    pub alter_id: i32,
    pub vmess_security: Option<String>,

    // Transport
    pub network: String,
    pub security: String,
    pub sni: Option<String>,
    pub fingerprint: Option<String>,
    pub alpn: Option<String>,
    pub public_key: Option<String>,
    pub short_id: Option<String>,
    pub spider_x: Option<String>,
    pub allow_insecure: bool,
    pub path: Option<String>,
    pub host: Option<String>,
    pub service_name: Option<String>,
    pub mode: Option<String>,
    pub header_type: Option<String>,
    pub extra: Option<String>,

    /// Full Xray JSON config when the subscription delivers JSON instead of links.
    pub raw_xray_json: Option<String>,
    /// Original share link, for the copy button.
    pub raw_link: Option<String>,
    /// True when the Firewall Bypass rewrite was applied.
    pub is_bypass_patched: bool,
}

impl ServerProfile {
    pub fn new(protocol: &str) -> Self {
        Self {
            protocol: protocol.to_string(),
            network: "tcp".into(),
            security: "none".into(),
            ..Default::default()
        }
    }

    pub fn key(&self) -> String {
        format!("{}|{}|{}|{}", self.protocol, self.remark, self.address, self.port)
    }
}

/// Account information and servers of a subscription.
#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "PascalCase", default)]
pub struct Subscription {
    pub url: String,
    pub title: Option<String>,
    pub username: Option<String>,
    pub status: Option<String>,
    pub sid: Option<String>,
    pub emails: Vec<String>,
    /// Unix milliseconds.
    pub last_online: Option<i64>,
    /// False when the panel reports the account as disabled.
    pub enabled: bool,
    pub upload: i64,
    pub download: i64,
    /// 0 = unlimited.
    pub total: i64,
    /// Unix seconds; None = never expires.
    pub expire: Option<i64>,
    pub update_interval_hours: Option<i32>,
    pub support_url: Option<String>,
    pub web_page_url: Option<String>,
    pub announce: Option<String>,
    /// Unix milliseconds.
    pub fetched_at: i64,
    pub servers: Vec<ServerProfile>,
}

impl Default for Subscription {
    fn default() -> Self {
        Self {
            url: String::new(),
            title: None,
            username: None,
            status: None,
            sid: None,
            emails: Vec::new(),
            last_online: None,
            enabled: true,
            upload: 0,
            download: 0,
            total: 0,
            expire: None,
            update_interval_hours: None,
            support_url: None,
            web_page_url: None,
            announce: None,
            fetched_at: 0,
            servers: Vec::new(),
        }
    }
}

impl Subscription {
    pub fn used(&self) -> i64 {
        self.upload + self.download
    }
}
