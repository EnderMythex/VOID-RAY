using System.Net;
using System.Text.Json;
using System.Text.Json.Nodes;
using VoidRay.Models;

namespace VoidRay.Services;

/// <summary>Builds the xray-core JSON configuration for a server.</summary>
public static class XrayConfigBuilder
{
    public static string Build(ServerProfile p, int socksPort, int httpPort)
    {
        JsonObject root;
        if (p.RawXrayJson is not null)
        {
            root = JsonNode.Parse(p.RawXrayJson)!.AsObject();
        }
        else
        {
            root = new JsonObject
            {
                ["outbounds"] = new JsonArray(
                    BuildOutbound(p),
                    new JsonObject { ["tag"] = "direct", ["protocol"] = "freedom" },
                    new JsonObject { ["tag"] = "block", ["protocol"] = "blackhole" }),
            };
        }

        root["log"] = new JsonObject { ["loglevel"] = "warning" };
        root["inbounds"] = new JsonArray(
            Inbound("socks", socksPort, new JsonObject { ["udp"] = true, ["auth"] = "noauth" }),
            Inbound("http", httpPort, new JsonObject()));
        root.Remove("api");
        root.Remove("stats");

        if (root["routing"] is not JsonObject)
        {
            root["routing"] = new JsonObject
            {
                ["domainStrategy"] = "AsIs",
                ["rules"] = new JsonArray(new JsonObject
                {
                    ["type"] = "field",
                    ["ip"] = new JsonArray("geoip:private"),
                    ["outboundTag"] = "direct",
                }),
            };
        }

        return root.ToJsonString(new JsonSerializerOptions { WriteIndented = true });
    }

    private static JsonObject Inbound(string protocol, int port, JsonObject settings) => new()
    {
        ["tag"] = protocol,
        ["listen"] = "127.0.0.1",
        ["port"] = port,
        ["protocol"] = protocol,
        ["settings"] = settings,
        ["sniffing"] = new JsonObject
        {
            ["enabled"] = true,
            ["destOverride"] = new JsonArray("http", "tls", "quic"),
            ["routeOnly"] = true,
        },
    };

    private static JsonObject BuildOutbound(ServerProfile p)
    {
        JsonObject settings;
        switch (p.Protocol)
        {
            case "vless":
                var user = new JsonObject { ["id"] = p.Id, ["encryption"] = p.Encryption ?? "none" };
                if (!string.IsNullOrEmpty(p.Flow))
                    user["flow"] = p.Flow;
                settings = Vnext(p, user);
                break;
            case "vmess":
                settings = Vnext(p, new JsonObject
                {
                    ["id"] = p.Id,
                    ["alterId"] = p.AlterId,
                    ["security"] = p.VmessSecurity ?? "auto",
                });
                break;
            case "trojan":
                settings = new JsonObject
                {
                    ["servers"] = new JsonArray(new JsonObject
                    {
                        ["address"] = p.Address, ["port"] = p.Port, ["password"] = p.Id,
                    }),
                };
                break;
            case "shadowsocks":
                settings = new JsonObject
                {
                    ["servers"] = new JsonArray(new JsonObject
                    {
                        ["address"] = p.Address, ["port"] = p.Port, ["method"] = p.Method, ["password"] = p.Id,
                    }),
                };
                break;
            default:
                throw new NotSupportedException($"Protocole non supporté : {p.Protocol}");
        }

        return new JsonObject
        {
            ["tag"] = "proxy",
            ["protocol"] = p.Protocol,
            ["settings"] = settings,
            ["streamSettings"] = BuildStream(p),
        };
    }

    private static JsonObject Vnext(ServerProfile p, JsonObject user) => new()
    {
        ["vnext"] = new JsonArray(new JsonObject
        {
            ["address"] = p.Address,
            ["port"] = p.Port,
            ["users"] = new JsonArray(user),
        }),
    };

    private static JsonObject BuildStream(ServerProfile p)
    {
        var network = p.Network.ToLowerInvariant() switch
        {
            "raw" => "tcp",
            "splithttp" => "xhttp",
            var n => n,
        };
        var stream = new JsonObject { ["network"] = network };

        var security = p.Security.ToLowerInvariant();
        var serverName = p.Sni ?? p.Host ?? (IPAddress.TryParse(p.Address, out _) ? null : p.Address);
        switch (security)
        {
            case "tls":
            case "xtls":
                var tls = new JsonObject
                {
                    ["serverName"] = serverName,
                    ["fingerprint"] = p.Fingerprint ?? "chrome",
                    ["allowInsecure"] = p.AllowInsecure,
                };
                if (!string.IsNullOrEmpty(p.Alpn))
                    tls["alpn"] = new JsonArray(p.Alpn.Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
                        .Select(a => (JsonNode)a).ToArray());
                stream["security"] = "tls";
                stream["tlsSettings"] = tls;
                break;
            case "reality":
                stream["security"] = "reality";
                stream["realitySettings"] = new JsonObject
                {
                    ["serverName"] = serverName,
                    ["fingerprint"] = p.Fingerprint ?? "chrome",
                    ["publicKey"] = p.PublicKey,
                    ["shortId"] = p.ShortId ?? "",
                    ["spiderX"] = p.SpiderX ?? "",
                };
                break;
            default:
                stream["security"] = "none";
                break;
        }

        switch (network)
        {
            case "ws":
                stream["wsSettings"] = new JsonObject { ["path"] = p.Path ?? "/", ["host"] = p.Host ?? "" };
                break;
            case "httpupgrade":
                stream["httpupgradeSettings"] = new JsonObject { ["path"] = p.Path ?? "/", ["host"] = p.Host ?? "" };
                break;
            case "grpc":
                stream["grpcSettings"] = new JsonObject
                {
                    ["serviceName"] = p.ServiceName ?? p.Path ?? "",
                    ["multiMode"] = p.Mode == "multi",
                    ["authority"] = p.Host ?? "",
                };
                break;
            case "xhttp":
                var xhttp = new JsonObject
                {
                    ["path"] = p.Path ?? "/",
                    ["host"] = p.Host ?? "",
                    ["mode"] = p.Mode ?? "auto",
                };
                if (!string.IsNullOrEmpty(p.Extra))
                {
                    try { xhttp["extra"] = JsonNode.Parse(p.Extra); }
                    catch (JsonException) { /* ignore malformed extra */ }
                }
                stream["xhttpSettings"] = xhttp;
                break;
            case "kcp":
                stream["kcpSettings"] = new JsonObject
                {
                    ["header"] = new JsonObject { ["type"] = p.HeaderType ?? "none" },
                    ["seed"] = p.Path ?? "",
                };
                break;
            case "tcp" when p.HeaderType == "http":
                stream["tcpSettings"] = new JsonObject
                {
                    ["header"] = new JsonObject
                    {
                        ["type"] = "http",
                        ["request"] = new JsonObject
                        {
                            ["path"] = new JsonArray(p.Path ?? "/"),
                            ["headers"] = new JsonObject { ["Host"] = new JsonArray(p.Host ?? "") },
                        },
                    },
                };
                break;
        }

        return stream;
    }
}
