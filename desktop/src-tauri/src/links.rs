//! Share links (vless, vmess, trojan, ss) and subscription bodies.

use std::collections::HashMap;

use base64::Engine;
use serde_json::Value;

use crate::models::ServerProfile;

/// Lenient base64: url-safe alphabet, missing padding and whitespace accepted.
pub fn b64_decode(input: &str) -> Option<String> {
    let mut t: String = input
        .chars()
        .filter(|c| !c.is_whitespace())
        .map(|c| match c {
            '-' => '+',
            '_' => '/',
            c => c,
        })
        .collect();
    while t.ends_with('=') {
        t.pop();
    }
    match t.len() % 4 {
        1 => return None,
        2 => t.push_str("=="),
        3 => t.push('='),
        _ => {}
    }
    let bytes = base64::engine::general_purpose::STANDARD.decode(t).ok()?;
    Some(String::from_utf8_lossy(&bytes).into_owned())
}

pub fn unescape(s: &str) -> String {
    percent_encoding::percent_decode_str(s).decode_utf8_lossy().into_owned()
}

/// Parses a subscription body. Returns the servers and how many links were skipped.
pub fn parse_body(body: &str) -> (Vec<ServerProfile>, usize) {
    let mut result = Vec::new();
    let mut unsupported = 0;
    let mut text = body.trim().to_string();
    if text.is_empty() {
        return (result, 0);
    }

    // Some panels return full Xray configs.
    if text.starts_with('[') || text.starts_with('{') {
        result.extend(parse_xray_json(&text));
        if !result.is_empty() {
            return (result, 0);
        }
    }

    if !text.contains("://") {
        if let Some(decoded) = b64_decode(&text) {
            if decoded.contains("://") {
                text = decoded.trim().to_string();
            }
        }
    }

    for line in text.split(['\r', '\n']).map(str::trim).filter(|l| !l.is_empty()) {
        if !line.contains("://") {
            continue;
        }
        match parse_link(line) {
            Some(mut p) => {
                p.raw_link = Some(line.to_string());
                result.push(p);
            }
            None => unsupported += 1,
        }
    }
    (result, unsupported)
}

pub fn parse_link(link: &str) -> Option<ServerProfile> {
    let idx = link.find("://")?;
    if idx == 0 {
        return None;
    }
    match link[..idx].to_ascii_lowercase().as_str() {
        "vless" => parse_vless_or_trojan(link, "vless"),
        "trojan" => parse_vless_or_trojan(link, "trojan"),
        "vmess" => parse_vmess(link),
        "ss" => parse_shadowsocks(link),
        _ => None,
    }
}

// ------------------------------------------------------------------ url parts

struct UrlParts {
    user: String,
    host: String,
    port: u16,
    fragment: String,
    query: HashMap<String, String>,
}

fn split_host_port(value: &str) -> Option<(String, u16)> {
    if let Some(rest) = value.strip_prefix('[') {
        let close = rest.find(']')?;
        let host = rest[..close].to_string();
        let port = rest[close + 1..].strip_prefix(':')?.parse().ok()?;
        return Some((host, port));
    }
    let colon = value.rfind(':')?;
    if colon == 0 {
        return None;
    }
    Some((value[..colon].to_string(), value[colon + 1..].parse().ok()?))
}

fn parse_url(link: &str) -> Option<UrlParts> {
    let idx = link.find("://")?;
    let mut rest = &link[idx + 3..];

    let mut fragment = String::new();
    if let Some(h) = rest.find('#') {
        fragment = unescape(&rest[h + 1..]);
        rest = &rest[..h];
    }
    let mut query_string = "";
    if let Some(q) = rest.find('?') {
        query_string = &rest[q + 1..];
        rest = &rest[..q];
    }
    let rest = rest.trim_end_matches('/');

    let (user, hostport) = match rest.rfind('@') {
        Some(at) => (unescape(&rest[..at]), &rest[at + 1..]),
        None => (String::new(), rest),
    };
    let (host, port) = split_host_port(hostport)?;

    let mut query = HashMap::new();
    for pair in query_string.split('&').filter(|p| !p.is_empty()) {
        match pair.find('=') {
            Some(eq) => query.insert(unescape(&pair[..eq]).to_ascii_lowercase(), unescape(&pair[eq + 1..])),
            None => query.insert(unescape(pair).to_ascii_lowercase(), String::new()),
        };
    }
    Some(UrlParts { user, host, port, fragment, query })
}

// ------------------------------------------------------------------ vless / trojan

fn parse_vless_or_trojan(link: &str, protocol: &str) -> Option<ServerProfile> {
    let u = parse_url(link)?;
    if u.user.is_empty() {
        return None;
    }
    let mut p = ServerProfile::new(protocol);
    p.id = Some(u.user.clone());
    p.address = u.host.clone();
    p.port = u.port;
    p.remark = if u.fragment.trim().is_empty() { format!("{}:{}", u.host, u.port) } else { u.fragment.clone() };

    let get = |k: &str| u.query.get(k).filter(|v| !v.is_empty()).cloned();
    p.network = get("type").unwrap_or_else(|| "tcp".into());
    p.security = get("security").unwrap_or_else(|| "none".into());
    p.sni = get("sni").or_else(|| get("peer"));
    p.fingerprint = get("fp");
    p.alpn = get("alpn");
    p.public_key = get("pbk");
    p.short_id = get("sid");
    p.spider_x = get("spx");
    p.path = get("path");
    p.host = get("host");
    p.service_name = get("servicename");
    p.mode = get("mode");
    p.header_type = get("headertype");
    p.flow = get("flow");
    p.encryption = get("encryption");
    p.extra = get("extra");
    let truthy = |v: Option<String>| matches!(v.as_deref(), Some("1") | Some("true"));
    p.allow_insecure = truthy(get("allowinsecure")) || truthy(get("insecure"));

    if protocol == "trojan" && !u.query.contains_key("security") {
        p.security = "tls".into();
    }
    Some(p)
}

// ------------------------------------------------------------------ vmess

fn parse_vmess(link: &str) -> Option<ServerProfile> {
    let mut payload = &link[8..];
    if let Some(h) = payload.find('#') {
        payload = &payload[..h];
    }
    let json = b64_decode(payload)?;
    let r: Value = serde_json::from_str(&json).ok()?;
    let s = |name: &str| -> Option<String> {
        match r.get(name)? {
            Value::String(v) if !v.is_empty() => Some(v.clone()),
            Value::Number(n) => Some(n.to_string()),
            _ => None,
        }
    };

    let address = s("add")?;
    let port: u16 = s("port")?.parse().ok()?;
    let mut p = ServerProfile::new("vmess");
    p.remark = s("ps").unwrap_or_else(|| format!("{address}:{port}"));
    p.address = address;
    p.port = port;
    p.id = s("id");
    p.alter_id = s("aid").and_then(|v| v.parse().ok()).unwrap_or(0);
    p.vmess_security = Some(s("scy").unwrap_or_else(|| "auto".into()));
    p.network = s("net").unwrap_or_else(|| "tcp".into());
    p.header_type = s("type");
    p.host = s("host");
    p.path = s("path");
    p.service_name = if s("net").as_deref() == Some("grpc") { s("path") } else { None };
    p.security = s("tls").unwrap_or_else(|| "none".into());
    p.sni = s("sni");
    p.alpn = s("alpn");
    p.fingerprint = s("fp");
    Some(p)
}

// ------------------------------------------------------------------ shadowsocks

fn parse_shadowsocks(link: &str) -> Option<ServerProfile> {
    let mut rest = &link[5..];
    let mut fragment = String::new();
    if let Some(h) = rest.find('#') {
        fragment = unescape(&rest[h + 1..]);
        rest = &rest[..h];
    }
    if let Some(q) = rest.find('?') {
        rest = &rest[..q];
    }
    let rest = rest.trim_end_matches('/');

    let (user_info, host_port) = match rest.rfind('@') {
        Some(at) => {
            let raw = &rest[..at];
            let user = match b64_decode(raw) {
                Some(dec) if dec.contains(':') => dec,
                _ => unescape(raw),
            };
            (user, rest[at + 1..].to_string())
        }
        None => {
            let decoded = b64_decode(rest)?;
            let at = decoded.rfind('@')?;
            (decoded[..at].to_string(), decoded[at + 1..].to_string())
        }
    };

    let colon = user_info.find(':')?;
    let (host, port) = split_host_port(&host_port)?;
    let mut p = ServerProfile::new("shadowsocks");
    p.method = Some(user_info[..colon].to_string());
    p.id = Some(user_info[colon + 1..].to_string());
    p.remark = if fragment.trim().is_empty() { format!("{host}:{port}") } else { fragment };
    p.address = host;
    p.port = port;
    Some(p)
}

// ------------------------------------------------------------------ xray json

fn parse_xray_json(text: &str) -> Vec<ServerProfile> {
    let Ok(root) = serde_json::from_str::<Value>(text) else { return Vec::new() };
    let configs: Vec<&Value> = match &root {
        Value::Array(a) => a.iter().filter(|v| v.is_object()).collect(),
        Value::Object(_) => vec![&root],
        _ => Vec::new(),
    };
    let mut out = Vec::new();
    for cfg in configs {
        let Some(outbounds) = cfg.get("outbounds").and_then(Value::as_array) else { continue };
        let Some(proxy) = outbounds.iter().find(|o| {
            matches!(o.get("protocol").and_then(Value::as_str), Some("vless" | "vmess" | "trojan" | "shadowsocks"))
        }) else {
            continue;
        };
        let settings = proxy.get("settings");
        let server = settings
            .and_then(|s| s.get("vnext").and_then(|v| v.get(0)))
            .or_else(|| settings.and_then(|s| s.get("servers").and_then(|v| v.get(0))))
            .or(settings);
        let address = server.and_then(|s| s.get("address")).and_then(Value::as_str).unwrap_or("").to_string();
        let port = server.and_then(|s| s.get("port")).and_then(Value::as_u64).unwrap_or(0) as u16;
        let stream = proxy.get("streamSettings");

        let mut p = ServerProfile::new(proxy.get("protocol").and_then(Value::as_str).unwrap_or("vless"));
        p.remark = cfg
            .get("remarks")
            .and_then(Value::as_str)
            .map(str::to_string)
            .unwrap_or_else(|| format!("{address}:{port}"));
        p.address = address;
        p.port = port;
        p.network = stream.and_then(|s| s.get("network")).and_then(Value::as_str).unwrap_or("tcp").to_string();
        p.security = stream.and_then(|s| s.get("security")).and_then(Value::as_str).unwrap_or("none").to_string();
        p.raw_xray_json = Some(cfg.to_string());
        out.push(p);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn vless_reality() {
        let p = parse_link("vless://uuid-1@example.com:443?type=tcp&security=reality&pbk=KEY&sid=ab&fp=chrome&sni=www.x.com&flow=xtls-rprx-vision#Paris%20%F0%9F%87%AB%F0%9F%87%B7-user@mail.com").unwrap();
        assert_eq!(p.address, "example.com");
        assert_eq!(p.port, 443);
        assert_eq!(p.security, "reality");
        assert_eq!(p.public_key.as_deref(), Some("KEY"));
        assert_eq!(p.flow.as_deref(), Some("xtls-rprx-vision"));
        assert!(p.remark.starts_with("Paris"));
    }

    #[test]
    fn vmess_and_ss() {
        let json = r#"{"v":"2","ps":"VM","add":"1.2.3.4","port":"8443","id":"u","aid":"0","net":"ws","path":"/p","tls":"tls"}"#;
        let link = format!("vmess://{}", base64::engine::general_purpose::STANDARD.encode(json));
        let p = parse_link(&link).unwrap();
        assert_eq!((p.port, p.network.as_str(), p.security.as_str()), (8443, "ws", "tls"));

        let ss = format!("ss://{}@5.6.7.8:8388#SS", base64::engine::general_purpose::STANDARD.encode("aes-256-gcm:pw"));
        let p = parse_link(&ss).unwrap();
        assert_eq!(p.method.as_deref(), Some("aes-256-gcm"));
        assert_eq!(p.id.as_deref(), Some("pw"));
    }

    #[test]
    fn base64_body() {
        let body = base64::engine::general_purpose::STANDARD.encode("trojan://pw@h.com:443#T\nhttp://nope\nfoo://x@y:1");
        let (servers, unsupported) = parse_body(&body);
        assert_eq!(servers.len(), 1);
        assert_eq!(servers[0].security, "tls");
        assert_eq!(unsupported, 2); // http:// and foo:// are both skipped
    }
}
