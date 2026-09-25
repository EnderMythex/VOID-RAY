using System.Text.RegularExpressions;
using VoidRay.Models;

namespace VoidRay.Services;

/// <summary>
/// The "Firewall Bypass" server works only when it goes through Cloudflare over
/// TLS. The subscription ships it as plain WebSocket, so it is rewritten on
/// import with the settings that get through restrictive networks.
/// </summary>
public static class FirewallBypass
{
    public const string CleanIp = "172.67.186.245";
    public const int Port = 443;
    public const string Domain = "xray.enderr.win";
    public const string DefaultPath = "/x7k2p";
    public const string Fingerprint = "chrome";
    public const string Alpn = "http/1.1";

    private static readonly Regex Marker = new(@"firewall[\s_-]*bypass|bypass[\s_-]*firewall",
        RegexOptions.IgnoreCase | RegexOptions.CultureInvariant);

    public static bool IsBypassServer(ServerProfile p) =>
        p.RawXrayJson is null && p.Protocol == "vless" && Marker.IsMatch(p.Remark);

    /// <summary>Rewrites every bypass server in place. Returns how many were changed.</summary>
    public static int Apply(IEnumerable<ServerProfile> servers)
    {
        var count = 0;
        foreach (var p in servers.Where(IsBypassServer))
        {
            p.Address = CleanIp;
            p.Port = Port;
            p.Network = "ws";
            p.Host = Domain;
            p.Path = string.IsNullOrWhiteSpace(p.Path) ? DefaultPath : p.Path;
            p.Security = "tls";
            p.Sni = Domain;
            p.Fingerprint = Fingerprint;
            p.Alpn = Alpn;
            p.AllowInsecure = false;
            p.Flow = null;
            p.Encryption = "none";
            p.PublicKey = null;
            p.ShortId = null;
            p.IsBypassPatched = true;
            count++;
        }
        return count;
    }
}
