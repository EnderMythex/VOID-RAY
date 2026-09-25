using System.Text.Json;
using System.Text.Json.Nodes;

namespace VoidRay.Services;

/// <summary>sing-box 1.14 configuration: TUN → Xray's local SOCKS inbound.</summary>
public static class TunConfigBuilder
{
    public static string Build(int socksPort)
    {
        var root = new JsonObject
        {
            ["log"] = new JsonObject { ["level"] = "warn", ["timestamp"] = true },
            ["dns"] = new JsonObject
            {
                // DNS queries caught by the TUN are answered through the tunnel.
                ["servers"] = new JsonArray(new JsonObject
                {
                    ["type"] = "tcp",
                    ["tag"] = "remote",
                    ["server"] = "1.1.1.1",
                    ["detour"] = "proxy",
                }),
                ["strategy"] = "ipv4_only",
            },
            ["inbounds"] = new JsonArray(new JsonObject
            {
                ["type"] = "tun",
                ["tag"] = "tun-in",
                ["interface_name"] = "VOID-RAY",
                ["address"] = new JsonArray("172.19.0.1/30"),
                ["mtu"] = 9000,
                ["auto_route"] = true,
                ["strict_route"] = true,
                ["stack"] = "mixed",
            }),
            ["outbounds"] = new JsonArray(
                new JsonObject
                {
                    ["type"] = "socks",
                    ["tag"] = "proxy",
                    ["server"] = "127.0.0.1",
                    ["server_port"] = socksPort,
                    ["version"] = "5",
                },
                new JsonObject { ["type"] = "direct", ["tag"] = "direct" }),
            ["route"] = new JsonObject
            {
                // Bind direct connections to the real network card: no loop through the TUN.
                ["auto_detect_interface"] = true,
                ["rules"] = new JsonArray(
                    new JsonObject { ["action"] = "sniff" },
                    new JsonObject { ["protocol"] = "dns", ["action"] = "hijack-dns" },
                    // Xray's own connections to the VPN server must not re-enter the tunnel.
                    new JsonObject
                    {
                        ["process_name"] = new JsonArray("xray.exe", "sing-box.exe", "VoidRay.exe"),
                        ["action"] = "route",
                        ["outbound"] = "direct",
                    },
                    new JsonObject { ["ip_is_private"] = true, ["action"] = "route", ["outbound"] = "direct" }),
                ["final"] = "proxy",
            },
        };
        return root.ToJsonString(new JsonSerializerOptions { WriteIndented = true });
    }
}
