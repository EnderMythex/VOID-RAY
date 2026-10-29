//! Brand rules, the Firewall Bypass rewrite, and the Xray / sing-box configs.

use std::net::IpAddr;
use std::sync::LazyLock;

use regex::Regex;
use serde_json::{json, Map, Value};

use crate::models::ServerProfile;

pub const DEFAULT_SUPPORT_URL: &str = "https://discord.gg/BzG6FJz6zK";

static LINK: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(?i)^https://sub\.enderr\.win/ender/([A-Za-z0-9_-]{4,64})/?$").unwrap());

/// Accepts only EnderrVPN subscription links; returns (normalised link, sid).
pub fn normalize_link(input: &str) -> Option<(String, String)> {
    let caps = LINK.captures(input.trim())?;
    let sid = caps.get(1)?.as_str().to_string();
    Some((format!("https://sub.enderr.win/ender/{sid}"), sid))
}

// ------------------------------------------------------------------ firewall bypass

pub const BYPASS_IP: &str = "172.67.186.245";
const BYPASS_PORT: u16 = 443;
const BYPASS_DOMAIN: &str = "xray.enderr.win";
const BYPASS_PATH: &str = "/x7k2p";

static BYPASS: LazyLock<Regex> =
    LazyLock::new(|| Regex::new(r"(?i)firewall[\s_-]*bypass|bypass[\s_-]*firewall").unwrap());

/// The "Firewall Bypass" server only works through Cloudflare over TLS; the
/// subscription ships it as plain WebSocket, so it is rewritten on import.
pub fn apply_bypass(servers: &mut [ServerProfile]) -> usize {
    let mut count = 0;
    for p in servers.iter_mut() {
        if p.raw_xray_json.is_some() || p.protocol != "vless" || !BYPASS.is_match(&p.remark) {
            continue;
        }
        p.address = BYPASS_IP.into();
        p.port = BYPASS_PORT;
        p.network = "ws".into();
        p.host = Some(BYPASS_DOMAIN.into());
        if p.path.as_deref().map_or(true, |s| s.trim().is_empty()) {
            p.path = Some(BYPASS_PATH.into());
        }
        p.security = "tls".into();
        p.sni = Some(BYPASS_DOMAIN.into());
        p.fingerprint = Some("chrome".into());
        p.alpn = Some("http/1.1".into());
        p.allow_insecure = false;
        p.flow = None;
        p.encryption = Some("none".into());
        p.public_key = None;
        p.short_id = None;
        p.is_bypass_patched = true;
        count += 1;
    }
    count
}

// ------------------------------------------------------------------ xray

pub fn xray_config(p: &ServerProfile, socks_port: u16, http_port: u16) -> Result<String, String> {
    let mut root: Map<String, Value> = match &p.raw_xray_json {
        Some(raw) => serde_json::from_str::<Value>(raw)
            .ok()
            .and_then(|v| v.as_object().cloned())
            .ok_or("Configuration JSON invalide")?,
        None => {
            let mut m = Map::new();
            m.insert(
                "outbounds".into(),
                json!([outbound(p)?, {"tag": "direct", "protocol": "freedom"}, {"tag": "block", "protocol": "blackhole"}]),
            );
            m
        }
    };

    root.insert("log".into(), json!({"loglevel": "warning", "access": "none"}));
    root.insert(
        "inbounds".into(),
        json!([
            inbound("socks", socks_port, json!({"udp": true, "auth": "noauth"})),
            inbound("http", http_port, json!({})),
        ]),
    );
    root.remove("api");
    root.remove("stats");
    if !root.get("routing").is_some_and(Value::is_object) {
        root.insert(
            "routing".into(),
            json!({
                "domainStrategy": "AsIs",
                "rules": [{"type": "field", "ip": ["geoip:private"], "outboundTag": "direct"}]
            }),
        );
    }
    serde_json::to_string_pretty(&Value::Object(root)).map_err(|e| e.to_string())
}

fn inbound(protocol: &str, port: u16, settings: Value) -> Value {
    json!({
        "tag": protocol,
        "listen": "127.0.0.1",
        "port": port,
        "protocol": protocol,
        "settings": settings,
        "sniffing": {"enabled": true, "destOverride": ["http", "tls", "quic"], "routeOnly": true}
    })
}

fn outbound(p: &ServerProfile) -> Result<Value, String> {
    let vnext = |user: Value| json!({"vnext": [{"address": p.address, "port": p.port, "users": [user]}]});
    let settings = match p.protocol.as_str() {
        "vless" => {
            let mut user = json!({"id": p.id, "encryption": p.encryption.clone().unwrap_or_else(|| "none".into())});
            if let Some(flow) = p.flow.as_deref().filter(|f| !f.is_empty()) {
                user["flow"] = json!(flow);
            }
            vnext(user)
        }
        "vmess" => vnext(json!({
            "id": p.id,
            "alterId": p.alter_id,
            "security": p.vmess_security.clone().unwrap_or_else(|| "auto".into())
        })),
        "trojan" => json!({"servers": [{"address": p.address, "port": p.port, "password": p.id}]}),
        "shadowsocks" => {
            json!({"servers": [{"address": p.address, "port": p.port, "method": p.method, "password": p.id}]})
        }
        other => return Err(format!("Protocole non supporté : {other}")),
    };
    Ok(json!({"tag": "proxy", "protocol": p.protocol, "settings": settings, "streamSettings": stream(p)}))
}

fn stream(p: &ServerProfile) -> Value {
    let network = match p.network.to_ascii_lowercase().as_str() {
        "raw" => "tcp".to_string(),
        "splithttp" => "xhttp".to_string(),
        n => n.to_string(),
    };
    let mut s = json!({"network": network});
    let server_name = p
        .sni
        .clone()
        .or_else(|| p.host.clone())
        .or_else(|| if p.address.parse::<IpAddr>().is_ok() { None } else { Some(p.address.clone()) });
    let fp = p.fingerprint.clone().unwrap_or_else(|| "chrome".into());

    match p.security.to_ascii_lowercase().as_str() {
        "tls" | "xtls" => {
            let mut tls = json!({"serverName": server_name, "fingerprint": fp, "allowInsecure": p.allow_insecure});
            if let Some(alpn) = p.alpn.as_deref().filter(|a| !a.is_empty()) {
                let list: Vec<&str> = alpn.split(',').map(str::trim).filter(|a| !a.is_empty()).collect();
                tls["alpn"] = json!(list);
            }
            s["security"] = json!("tls");
            s["tlsSettings"] = tls;
        }
        "reality" => {
            s["security"] = json!("reality");
            s["realitySettings"] = json!({
                "serverName": server_name,
                "fingerprint": fp,
                "publicKey": p.public_key,
                "shortId": p.short_id.clone().unwrap_or_default(),
                "spiderX": p.spider_x.clone().unwrap_or_default()
            });
        }
        _ => s["security"] = json!("none"),
    }

    let path = p.path.clone().unwrap_or_else(|| "/".into());
    let host = p.host.clone().unwrap_or_default();
    match network.as_str() {
        "ws" => s["wsSettings"] = json!({"path": path, "host": host}),
        "httpupgrade" => s["httpupgradeSettings"] = json!({"path": path, "host": host}),
        "grpc" => {
            s["grpcSettings"] = json!({
                "serviceName": p.service_name.clone().or_else(|| p.path.clone()).unwrap_or_default(),
                "multiMode": p.mode.as_deref() == Some("multi"),
                "authority": host
            })
        }
        "xhttp" => {
            let mut x = json!({"path": path, "host": host, "mode": p.mode.clone().unwrap_or_else(|| "auto".into())});
            if let Some(extra) = p.extra.as_deref().and_then(|e| serde_json::from_str::<Value>(e).ok()) {
                x["extra"] = extra;
            }
            s["xhttpSettings"] = x;
        }
        "kcp" => {
            s["kcpSettings"] = json!({
                "header": {"type": p.header_type.clone().unwrap_or_else(|| "none".into())},
                "seed": p.path.clone().unwrap_or_default()
            })
        }
        "tcp" if p.header_type.as_deref() == Some("http") => {
            s["tcpSettings"] = json!({
                "header": {"type": "http", "request": {"path": [path], "headers": {"Host": [host]}}}
            })
        }
        _ => {}
    }
    s
}

// ------------------------------------------------------------------ sing-box (TUN)

/// sing-box 1.14: a TUN card that hands the whole PC's traffic to Xray's SOCKS inbound.
pub fn tun_config(socks_port: u16) -> String {
    let mut own: Vec<String> = vec!["xray.exe".into(), "sing-box.exe".into(), "VoidRay.exe".into()];
    if let Some(name) = std::env::current_exe().ok().and_then(|p| p.file_name().map(|n| n.to_string_lossy().into_owned())) {
        if !own.contains(&name) {
            own.push(name);
        }
    }
    let v = json!({
        "log": {"level": "warn", "timestamp": true},
        "dns": {
            "servers": [{"type": "tcp", "tag": "remote", "server": "1.1.1.1", "detour": "proxy"}],
            "strategy": "ipv4_only"
        },
        "inbounds": [{
            "type": "tun",
            "tag": "tun-in",
            "interface_name": "VOID-RAY",
            "address": ["172.19.0.1/30"],
            "mtu": 9000,
            "auto_route": true,
            "strict_route": true,
            "stack": "mixed"
        }],
        "outbounds": [
            {"type": "socks", "tag": "proxy", "server": "127.0.0.1", "server_port": socks_port, "version": "5"},
            {"type": "direct", "tag": "direct"}
        ],
        "route": {
            "auto_detect_interface": true,
            "rules": [
                {"action": "sniff"},
                {"protocol": "dns", "action": "hijack-dns"},
                {"process_name": own, "action": "route", "outbound": "direct"},
                {"ip_is_private": true, "action": "route", "outbound": "direct"}
            ],
            "final": "proxy"
        }
    });
    serde_json::to_string_pretty(&v).unwrap_or_default()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn link_rules() {
        assert_eq!(normalize_link(" https://sub.enderr.win/ender/k7p2x9q4/ ").unwrap().1, "k7p2x9q4");
        assert!(normalize_link("https://evil.com/ender/abcd").is_none());
    }

    #[test]
    fn bypass_rewrite_and_config() {
        let mut p = ServerProfile::new("vless");
        p.remark = "Firewall Bypass 🇫🇷".into();
        p.address = "1.1.1.1".into();
        p.port = 80;
        p.id = Some("u".into());
        let mut v = vec![p];
        assert_eq!(apply_bypass(&mut v), 1);
        let cfg: Value = serde_json::from_str(&xray_config(&v[0], 10808, 10809).unwrap()).unwrap();
        let out = &cfg["outbounds"][0];
        assert_eq!(out["settings"]["vnext"][0]["address"], BYPASS_IP);
        assert_eq!(out["streamSettings"]["tlsSettings"]["serverName"], "xray.enderr.win");
        assert_eq!(out["streamSettings"]["tlsSettings"]["alpn"][0], "http/1.1");
        assert_eq!(out["streamSettings"]["wsSettings"]["path"], "/x7k2p");
        assert_eq!(cfg["inbounds"][1]["port"], 10809);
    }
}
