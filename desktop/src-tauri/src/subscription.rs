//! Downloads a subscription link and extracts the servers and the account
//! information (traffic, expiry, title…) from the 3x-ui panel.

use std::collections::HashMap;
use std::sync::LazyLock;
use std::time::Duration;

use regex::Regex;
use reqwest::header::HeaderMap;
use serde_json::Value;

use crate::links;
use crate::models::Subscription;

const USER_AGENTS: [&str; 3] = ["VoidRay/1.0", "v2rayN/6.45", "v2rayNG/1.8.5"];

#[derive(Debug)]
pub enum FetchError {
    /// The panel doesn't know the link (wrong or deleted subscription).
    NotFound,
    Network(String),
}

impl std::fmt::Display for FetchError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            FetchError::NotFound => write!(f, "abonnement introuvable"),
            FetchError::Network(m) => write!(f, "{m}"),
        }
    }
}

fn client(timeout: u64) -> reqwest::Client {
    reqwest::Client::builder()
        .timeout(Duration::from_secs(timeout))
        .build()
        .unwrap_or_default()
}

pub async fn fetch(url: &str) -> Result<(Subscription, Vec<String>), FetchError> {
    let http = client(20);
    let mut log = Vec::new();
    let mut info: Option<Subscription> = None;

    for ua in USER_AGENTS {
        let res = http
            .get(url)
            .header("User-Agent", ua)
            .header("Accept", "*/*")
            .send()
            .await
            .map_err(|e| FetchError::Network(e.to_string()))?;
        let code = res.status().as_u16();
        if matches!(code, 400 | 403 | 404) {
            return Err(FetchError::NotFound);
        }
        if !res.status().is_success() {
            return Err(FetchError::Network(format!("HTTP {code}")));
        }
        let mut candidate = Subscription { url: url.to_string(), fetched_at: now_ms(), ..Default::default() };
        read_headers(res.headers(), &mut candidate);
        let body = res.text().await.map_err(|e| FetchError::Network(e.to_string()))?;
        let (servers, unsupported) = links::parse_body(&body);
        candidate.servers = servers;
        if unsupported > 0 {
            log.push(format!("{unsupported} serveur(s) ignoré(s) (protocole non supporté par Xray)."));
        }

        // Keep the richest answer (headers may come with any of them).
        info = Some(match info.take() {
            None => candidate,
            Some(prev) if candidate.servers.len() > prev.servers.len() => merge(candidate, &prev),
            Some(prev) => merge(prev, &candidate),
        });
        if info.as_ref().is_some_and(|i| !i.servers.is_empty()) {
            break;
        }
        log.push(format!("Aucun serveur avec l'agent « {ua} », nouvel essai…"));
    }

    let mut info = info.unwrap_or_default();
    if !read_html_page(url, &mut info).await {
        read_info_endpoint(url, &mut info).await;
    }
    Ok((info, log))
}

fn now_ms() -> i64 {
    chrono::Utc::now().timestamp_millis()
}

fn merge(mut target: Subscription, other: &Subscription) -> Subscription {
    target.title = target.title.or_else(|| other.title.clone());
    target.username = target.username.or_else(|| other.username.clone());
    target.status = target.status.or_else(|| other.status.clone());
    if target.upload + target.download + target.total == 0 {
        target.upload = other.upload;
        target.download = other.download;
        target.total = other.total;
    }
    target.expire = target.expire.or(other.expire);
    target.update_interval_hours = target.update_interval_hours.or(other.update_interval_hours);
    target.support_url = target.support_url.or_else(|| other.support_url.clone());
    target.web_page_url = target.web_page_url.or_else(|| other.web_page_url.clone());
    target.announce = target.announce.or_else(|| other.announce.clone());
    target
}

fn decode_header(value: &str) -> Option<String> {
    let v = value.trim();
    if v.is_empty() {
        return None;
    }
    if v.len() > 7 && v[..7].eq_ignore_ascii_case("base64:") {
        return Some(links::b64_decode(&v[7..]).unwrap_or_else(|| v.to_string()));
    }
    Some(v.to_string())
}

fn read_headers(headers: &HeaderMap, info: &mut Subscription) {
    let h = |name: &str| headers.get(name).and_then(|v| v.to_str().ok()).and_then(decode_header);

    // subscription-userinfo: upload=123; download=456; total=789; expire=1700000000
    if let Some(user_info) = h("subscription-userinfo") {
        for part in user_info.split(';').map(str::trim).filter(|p| !p.is_empty()) {
            let Some((k, v)) = part.split_once('=') else { continue };
            let Ok(number) = v.trim().parse::<f64>() else { continue };
            let value = number as i64;
            match k.trim().to_ascii_lowercase().as_str() {
                "upload" => info.upload = value,
                "download" => info.download = value,
                "total" => info.total = value,
                "expire" => info.expire = (value > 0).then_some(value),
                _ => {}
            }
        }
    }
    info.title = h("profile-title");
    info.support_url = h("support-url");
    info.web_page_url = h("profile-web-page-url");
    info.announce = h("announce");
    info.update_interval_hours = h("profile-update-interval").and_then(|v| v.parse().ok()).filter(|h| *h > 0);
    if info.title.is_none() {
        // Fallback: the file name of Content-Disposition.
        if let Some(cd) = h("content-disposition") {
            if let Some(i) = cd.find("filename=") {
                info.title = Some(cd[i + 9..].trim_matches(|c| c == '"' || c == ';' || c == ' ').to_string());
            }
        }
    }
}

static DATA_BLOCK: LazyLock<Regex> = LazyLock::new(|| Regex::new(r#"(?i)<div[^>]*\bid="data"[^>]*>"#).unwrap());
static DATA_ATTR: LazyLock<Regex> = LazyLock::new(|| Regex::new(r#"(?i)data-([a-z-]+)="([^"]*)""#).unwrap());
static MAIL_ATTR: LazyLock<Regex> = LazyLock::new(|| Regex::new(r#"(?i)data-mail="([^"]*)""#).unwrap());
static LINK_ATTR: LazyLock<Regex> = LazyLock::new(|| Regex::new(r#"(?i)data-link="([^"]*)""#).unwrap());

fn html_decode(s: &str) -> String {
    s.replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}

/// 3x-ui serves a page to browsers whose data block carries what the headers
/// don't: account e-mails, last online time, support link, enabled state.
async fn read_html_page(url: &str, info: &mut Subscription) -> bool {
    let Ok(res) = client(10)
        .get(url)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) VoidRay/1.0")
        .header("Accept", "text/html,application/xhtml+xml")
        .send()
        .await
    else {
        return false;
    };
    if !res.status().is_success() {
        return false;
    }
    let Ok(html) = res.text().await else { return false };
    let Some(block) = DATA_BLOCK.find(&html) else { return false };

    let data: HashMap<String, String> = DATA_ATTR
        .captures_iter(block.as_str())
        .map(|c| (c[1].to_ascii_lowercase(), html_decode(&c[2]).trim().to_string()))
        .collect();
    let d = |k: &str| data.get(k).filter(|v| !v.is_empty()).cloned();
    let n = |k: &str| d(k).and_then(|v| v.parse::<i64>().ok()).unwrap_or(0);

    info.enabled = d("enabled").as_deref() != Some("0");
    if let Some(sid) = d("sid") {
        info.sid = Some(sid);
    }
    if info.support_url.is_none() {
        info.support_url = d("support");
    }
    if n("last-online") > 0 {
        info.last_online = Some(n("last-online"));
    }
    if info.upload + info.download == 0 {
        info.upload = n("upload-byte");
        info.download = n("download-byte");
    }
    if info.total == 0 {
        info.total = n("total-byte");
    }
    if info.expire.is_none() && n("expire") > 0 {
        info.expire = Some(n("expire"));
    }

    let mut emails: Vec<String> = Vec::new();
    for c in MAIL_ATTR.captures_iter(&html) {
        let e = html_decode(&c[1]).trim().to_string();
        if !e.is_empty() && !emails.contains(&e) {
            emails.push(e);
        }
    }
    if info.username.is_none() {
        info.username = emails.first().cloned();
    }
    info.emails = emails;

    if info.servers.is_empty() {
        let joined: Vec<String> = LINK_ATTR.captures_iter(&html).map(|c| html_decode(&c[1])).collect();
        info.servers = links::parse_body(&joined.join("\n")).0;
    }
    true
}

/// Marzban / Remnawave expose "{link}/info" with the username and status.
async fn read_info_endpoint(url: &str, info: &mut Subscription) {
    let base = url.split(['?', '#']).next().unwrap_or(url).trim_end_matches('/');
    let Ok(res) = client(8).get(format!("{base}/info")).header("User-Agent", USER_AGENTS[0]).send().await else {
        return;
    };
    if !res.status().is_success() {
        return;
    }
    let Ok(root) = res.json::<Value>().await else { return };
    let root = match root.get("response") {
        Some(r) => r.get("user").cloned().unwrap_or_else(|| r.clone()),
        None => root,
    };
    let s = |k: &str| root.get(k).and_then(Value::as_str).map(str::to_string);
    let num = |keys: &[&str]| {
        keys.iter().find_map(|k| match root.get(*k)? {
            Value::Number(n) => n.as_i64(),
            Value::String(t) => t.parse().ok(),
            _ => None,
        })
    };
    if info.username.is_none() {
        info.username = s("username");
    }
    if info.status.is_none() {
        info.status = s("status").or_else(|| s("userStatus"));
    }
    if info.total == 0 {
        if let Some(limit) = num(&["data_limit", "trafficLimitBytes"]).filter(|v| *v > 0) {
            info.total = limit;
        }
    }
    if info.used() == 0 {
        if let Some(used) = num(&["used_traffic", "trafficUsedBytes"]).filter(|v| *v > 0) {
            info.download = used;
        }
    }
    if info.expire.is_none() {
        info.expire = match root.get("expire").or_else(|| root.get("expiresAt")) {
            Some(Value::Number(n)) => n.as_i64().filter(|v| *v > 0),
            Some(Value::String(t)) => chrono::DateTime::parse_from_rfc3339(t).ok().map(|d| d.timestamp()),
            _ => None,
        };
    }
}
