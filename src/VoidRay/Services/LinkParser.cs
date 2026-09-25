using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using VoidRay.Models;

namespace VoidRay.Services;

/// <summary>Parses share links (vless, vmess, trojan, ss) and subscription bodies.</summary>
public static class LinkParser
{
    public static List<ServerProfile> ParseSubscriptionBody(string body, out int unsupported)
    {
        unsupported = 0;
        var result = new List<ServerProfile>();
        if (string.IsNullOrWhiteSpace(body))
            return result;

        var text = body.Trim();

        // Some panels (Marzban/Remnawave custom JSON) return full Xray configs.
        if (text.StartsWith('[') || text.StartsWith('{'))
        {
            result.AddRange(ParseXrayJson(text));
            if (result.Count > 0)
                return result;
        }

        if (!text.Contains("://"))
        {
            var decoded = Base64.TryDecode(text);
            if (decoded is not null && decoded.Contains("://"))
                text = decoded.Trim();
        }

        foreach (var line in text.Split(new[] { '\r', '\n' },
                     StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
        {
            if (!line.Contains("://"))
                continue;
            var profile = TryParse(line);
            if (profile is null)
            {
                unsupported++;
            }
            else
            {
                profile.RawLink = line;
                result.Add(profile);
            }
        }
        return result;
    }

    public static ServerProfile? TryParse(string link)
    {
        try
        {
            var idx = link.IndexOf("://", StringComparison.Ordinal);
            if (idx <= 0)
                return null;
            return link[..idx].ToLowerInvariant() switch
            {
                "vless" => ParseVlessOrTrojan(link, "vless"),
                "trojan" => ParseVlessOrTrojan(link, "trojan"),
                "vmess" => ParseVmess(link),
                "ss" => ParseShadowsocks(link),
                _ => null,
            };
        }
        catch
        {
            return null;
        }
    }

    // ---------------------------------------------------------------- vless / trojan

    private static ServerProfile? ParseVlessOrTrojan(string link, string protocol)
    {
        var u = UrlParts.Parse(link);
        if (u is null || string.IsNullOrEmpty(u.UserInfo))
            return null;

        var p = new ServerProfile
        {
            Protocol = protocol,
            Id = u.UserInfo,
            Address = u.Host,
            Port = u.Port,
            Remark = string.IsNullOrWhiteSpace(u.Fragment) ? $"{u.Host}:{u.Port}" : u.Fragment,
        };
        FillTransport(p, u.Query);
        if (protocol == "trojan" && !u.Query.ContainsKey("security"))
            p.Security = "tls";
        return p;
    }

    private static void FillTransport(ServerProfile p, IReadOnlyDictionary<string, string> q)
    {
        string? Get(string k) => q.TryGetValue(k, out var v) && !string.IsNullOrEmpty(v) ? v : null;

        p.Network = Get("type") ?? "tcp";
        p.Security = Get("security") ?? "none";
        p.Sni = Get("sni") ?? Get("peer");
        p.Fingerprint = Get("fp");
        p.Alpn = Get("alpn");
        p.PublicKey = Get("pbk");
        p.ShortId = Get("sid");
        p.SpiderX = Get("spx");
        p.Path = Get("path");
        p.Host = Get("host");
        p.ServiceName = Get("serviceName");
        p.Mode = Get("mode");
        p.HeaderType = Get("headerType");
        p.Flow = Get("flow");
        p.Encryption = Get("encryption");
        p.Extra = Get("extra");
        p.AllowInsecure = Get("allowInsecure") is "1" or "true" || Get("insecure") is "1" or "true";
    }

    // ---------------------------------------------------------------- vmess

    private static ServerProfile? ParseVmess(string link)
    {
        var payload = link[8..];
        var hash = payload.IndexOf('#');
        if (hash >= 0)
            payload = payload[..hash];
        var json = Base64.TryDecode(payload);
        if (json is null)
            return null;

        using var doc = JsonDocument.Parse(json);
        var r = doc.RootElement;
        string? S(string name) =>
            r.TryGetProperty(name, out var v)
                ? v.ValueKind switch
                {
                    JsonValueKind.String => string.IsNullOrEmpty(v.GetString()) ? null : v.GetString(),
                    JsonValueKind.Number => v.GetRawText(),
                    _ => null,
                }
                : null;

        var address = S("add");
        if (address is null || !int.TryParse(S("port"), out var port))
            return null;

        return new ServerProfile
        {
            Protocol = "vmess",
            Remark = S("ps") ?? $"{address}:{port}",
            Address = address,
            Port = port,
            Id = S("id"),
            AlterId = int.TryParse(S("aid"), out var aid) ? aid : 0,
            VmessSecurity = S("scy") ?? "auto",
            Network = S("net") ?? "tcp",
            HeaderType = S("type"),
            Host = S("host"),
            Path = S("path"),
            ServiceName = S("net") == "grpc" ? S("path") : null,
            Security = S("tls") ?? "none",
            Sni = S("sni"),
            Alpn = S("alpn"),
            Fingerprint = S("fp"),
        };
    }

    // ---------------------------------------------------------------- shadowsocks

    private static ServerProfile? ParseShadowsocks(string link)
    {
        var rest = link[5..];
        var fragment = "";
        var hash = rest.IndexOf('#');
        if (hash >= 0)
        {
            fragment = UrlParts.Unescape(rest[(hash + 1)..]);
            rest = rest[..hash];
        }
        var q = rest.IndexOf('?');
        if (q >= 0)
            rest = rest[..q];
        rest = rest.TrimEnd('/');

        string userInfo, hostPort;
        var at = rest.LastIndexOf('@');
        if (at >= 0)
        {
            var raw = rest[..at];
            userInfo = Base64.TryDecode(raw) is { } dec && dec.Contains(':') ? dec : UrlParts.Unescape(raw);
            hostPort = rest[(at + 1)..];
        }
        else
        {
            var decoded = Base64.TryDecode(rest);
            if (decoded is null)
                return null;
            at = decoded.LastIndexOf('@');
            if (at < 0)
                return null;
            userInfo = decoded[..at];
            hostPort = decoded[(at + 1)..];
        }

        var colon = userInfo.IndexOf(':');
        if (colon < 0 || !UrlParts.TrySplitHostPort(hostPort, out var host, out var port))
            return null;

        return new ServerProfile
        {
            Protocol = "shadowsocks",
            Method = userInfo[..colon],
            Id = userInfo[(colon + 1)..],
            Address = host,
            Port = port,
            Remark = string.IsNullOrWhiteSpace(fragment) ? $"{host}:{port}" : fragment,
        };
    }

    // ---------------------------------------------------------------- xray json

    private static IEnumerable<ServerProfile> ParseXrayJson(string text)
    {
        JsonNode? root;
        try { root = JsonNode.Parse(text); }
        catch { yield break; }

        var configs = root switch
        {
            JsonArray arr => arr.OfType<JsonObject>(),
            JsonObject obj => new[] { obj },
            _ => Enumerable.Empty<JsonObject>(),
        };

        foreach (var cfg in configs)
        {
            if (cfg["outbounds"] is not JsonArray outbounds)
                continue;
            var proxy = outbounds.OfType<JsonObject>()
                .FirstOrDefault(o => (string?)o["protocol"] is "vless" or "vmess" or "trojan" or "shadowsocks");
            if (proxy is null)
                continue;

            var settings = proxy["settings"] as JsonObject;
            var server = (settings?["vnext"] as JsonArray)?[0] as JsonObject
                         ?? (settings?["servers"] as JsonArray)?[0] as JsonObject
                         ?? settings;
            var address = (string?)server?["address"] ?? "";
            var port = server?["port"] is JsonValue pv && pv.TryGetValue<int>(out var pi) ? pi : 0;
            var stream = proxy["streamSettings"] as JsonObject;

            yield return new ServerProfile
            {
                Protocol = (string)proxy["protocol"]!,
                Remark = (string?)cfg["remarks"] ?? $"{address}:{port}",
                Address = address,
                Port = port,
                Network = (string?)stream?["network"] ?? "tcp",
                Security = (string?)stream?["security"] ?? "none",
                RawXrayJson = cfg.ToJsonString(),
            };
        }
    }
}

internal sealed class UrlParts
{
    public string UserInfo { get; private init; } = "";
    public string Host { get; private init; } = "";
    public int Port { get; private init; }
    public string Fragment { get; private init; } = "";
    public Dictionary<string, string> Query { get; } = new(StringComparer.OrdinalIgnoreCase);

    public static UrlParts? Parse(string link)
    {
        var idx = link.IndexOf("://", StringComparison.Ordinal);
        var rest = link[(idx + 3)..];

        var fragment = "";
        var hash = rest.IndexOf('#');
        if (hash >= 0)
        {
            fragment = Unescape(rest[(hash + 1)..]);
            rest = rest[..hash];
        }

        var queryString = "";
        var q = rest.IndexOf('?');
        if (q >= 0)
        {
            queryString = rest[(q + 1)..];
            rest = rest[..q];
        }
        rest = rest.TrimEnd('/');

        var user = "";
        var at = rest.LastIndexOf('@');
        if (at >= 0)
        {
            user = Unescape(rest[..at]);
            rest = rest[(at + 1)..];
        }

        if (!TrySplitHostPort(rest, out var host, out var port))
            return null;

        var parts = new UrlParts { UserInfo = user, Host = host, Port = port, Fragment = fragment };
        foreach (var pair in queryString.Split('&', StringSplitOptions.RemoveEmptyEntries))
        {
            var eq = pair.IndexOf('=');
            if (eq < 0)
                parts.Query[Unescape(pair)] = "";
            else
                parts.Query[Unescape(pair[..eq])] = Unescape(pair[(eq + 1)..]);
        }
        return parts;
    }

    public static bool TrySplitHostPort(string value, out string host, out int port)
    {
        host = "";
        port = 0;
        if (value.StartsWith('['))
        {
            var close = value.IndexOf(']');
            if (close < 0)
                return false;
            host = value[1..close];
            return value.Length > close + 2 && int.TryParse(value[(close + 2)..], out port);
        }
        var colon = value.LastIndexOf(':');
        if (colon <= 0)
            return false;
        host = value[..colon];
        return int.TryParse(value[(colon + 1)..], out port);
    }

    public static string Unescape(string s)
    {
        try { return Uri.UnescapeDataString(s); }
        catch { return s; }
    }
}

public static class Base64
{
    public static string? TryDecode(string input)
    {
        var sb = new StringBuilder(input.Length + 3);
        foreach (var c in input)
        {
            if (char.IsWhiteSpace(c))
                continue;
            sb.Append(c switch { '-' => '+', '_' => '/', _ => c });
        }
        var t = sb.ToString().TrimEnd('=');
        switch (t.Length % 4)
        {
            case 1: return null;
            case 2: t += "=="; break;
            case 3: t += "="; break;
        }
        try
        {
            return Encoding.UTF8.GetString(Convert.FromBase64String(t));
        }
        catch
        {
            return null;
        }
    }
}
