namespace VoidRay.Models;

/// <summary>A single proxy endpoint parsed from the subscription.</summary>
public sealed class ServerProfile
{
    public string Protocol { get; set; } = "vless";
    public string Remark { get; set; } = "";
    public string Address { get; set; } = "";
    public int Port { get; set; }

    // Credentials
    public string? Id { get; set; }            // uuid (vless/vmess) or password (trojan/ss)
    public string? Method { get; set; }        // shadowsocks cipher
    public string? Flow { get; set; }
    public string? Encryption { get; set; }
    public int AlterId { get; set; }
    public string? VmessSecurity { get; set; }

    // Transport
    public string Network { get; set; } = "tcp";
    public string Security { get; set; } = "none";
    public string? Sni { get; set; }
    public string? Fingerprint { get; set; }
    public string? Alpn { get; set; }
    public string? PublicKey { get; set; }
    public string? ShortId { get; set; }
    public string? SpiderX { get; set; }
    public bool AllowInsecure { get; set; }
    public string? Path { get; set; }
    public string? Host { get; set; }
    public string? ServiceName { get; set; }
    public string? Mode { get; set; }
    public string? HeaderType { get; set; }
    public string? Extra { get; set; }

    /// <summary>Full Xray JSON config when the subscription delivers JSON instead of links.</summary>
    public string? RawXrayJson { get; set; }

    /// <summary>Original share link, for the copy button.</summary>
    public string? RawLink { get; set; }

    /// <summary>True when <see cref="Services.FirewallBypass"/> rewrote this server.</summary>
    public bool IsBypassPatched { get; set; }

    public string Key => $"{Protocol}|{Remark}|{Address}|{Port}";

    public string TransportLabel
    {
        get
        {
            var sec = Security is null or "" or "none" ? null : Security.ToUpperInvariant();
            var net = Network.ToLowerInvariant();
            return sec is null ? net : $"{sec} · {net}";
        }
    }
}
