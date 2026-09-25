using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;
using VoidRay.Models;

namespace VoidRay.Services;

/// <summary>
/// Downloads a subscription link and extracts both the server list and the
/// account information (traffic, expiry, title…) exposed by panels such as
/// Marzban, Marzneshin, Remnawave, 3x-ui or Hiddify.
/// </summary>
public sealed class SubscriptionService
{
    private static readonly string[] UserAgents = { "VoidRay/1.0", "v2rayN/6.45", "v2rayNG/1.8.5" };

    private readonly HttpClient _http = new(new HttpClientHandler
    {
        AutomaticDecompression = DecompressionMethods.All,
        AllowAutoRedirect = true,
    })
    {
        Timeout = TimeSpan.FromSeconds(20),
    };

    public event Action<string>? Log;

    public async Task<SubscriptionInfo> FetchAsync(string url, CancellationToken ct = default)
    {
        url = url.Trim();
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || (uri.Scheme != "https" && uri.Scheme != "http"))
            throw new InvalidOperationException("Lien d'abonnement invalide (il doit commencer par https://).");

        SubscriptionInfo? info = null;
        foreach (var ua in UserAgents)
        {
            using var request = new HttpRequestMessage(HttpMethod.Get, uri);
            request.Headers.UserAgent.ParseAdd(ua);
            request.Headers.Accept.ParseAdd("*/*");

            using var response = await _http.SendAsync(request, ct);
            if (response.StatusCode == HttpStatusCode.NotFound)
                throw new InvalidOperationException("Abonnement introuvable (404). Vérifie ton lien.");
            if (!response.IsSuccessStatusCode)
                throw new InvalidOperationException($"Le serveur a répondu {(int)response.StatusCode} {response.ReasonPhrase}.");

            var body = await response.Content.ReadAsStringAsync(ct);
            var candidate = new SubscriptionInfo { Url = url, FetchedAt = DateTimeOffset.Now };
            ReadHeaders(response, candidate);
            candidate.Servers = LinkParser.ParseSubscriptionBody(body, out var unsupported);
            if (unsupported > 0)
                Log?.Invoke($"{unsupported} serveur(s) ignoré(s) (protocole non supporté par Xray).");

            // Keep the richest answer we get (headers can appear on any of them).
            if (info is null || candidate.Servers.Count > info.Servers.Count)
                info = Merge(candidate, info);
            else
                Merge(info, candidate);

            if (info.Servers.Count > 0)
                break;
            Log?.Invoke($"Aucun serveur avec l'agent « {ua} », nouvel essai…");
        }

        await TryReadInfoEndpointAsync(uri, info!, ct);
        return info!;
    }

    private static SubscriptionInfo Merge(SubscriptionInfo target, SubscriptionInfo? other)
    {
        if (other is null)
            return target;
        target.Title ??= other.Title;
        target.Username ??= other.Username;
        target.Status ??= other.Status;
        if (target.Upload + target.Download + target.Total == 0)
        {
            target.Upload = other.Upload;
            target.Download = other.Download;
            target.Total = other.Total;
        }
        target.Expire ??= other.Expire;
        target.UpdateIntervalHours ??= other.UpdateIntervalHours;
        target.SupportUrl ??= other.SupportUrl;
        target.WebPageUrl ??= other.WebPageUrl;
        target.Announce ??= other.Announce;
        return target;
    }

    private static void ReadHeaders(HttpResponseMessage response, SubscriptionInfo info)
    {
        string? H(string name)
        {
            if (response.Headers.TryGetValues(name, out var v) || response.Content.Headers.TryGetValues(name, out v))
                return DecodeHeader(v.FirstOrDefault());
            return null;
        }

        // subscription-userinfo: upload=123; download=456; total=789; expire=1700000000
        var userInfo = H("subscription-userinfo");
        if (userInfo is not null)
        {
            foreach (var part in userInfo.Split(';', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries))
            {
                var kv = part.Split('=', 2, StringSplitOptions.TrimEntries);
                if (kv.Length != 2 || !double.TryParse(kv[1], System.Globalization.NumberStyles.Float,
                        System.Globalization.CultureInfo.InvariantCulture, out var number))
                    continue;
                var value = (long)number;
                switch (kv[0].ToLowerInvariant())
                {
                    case "upload": info.Upload = value; break;
                    case "download": info.Download = value; break;
                    case "total": info.Total = value; break;
                    case "expire": info.Expire = value > 0 ? DateTimeOffset.FromUnixTimeSeconds(value) : null; break;
                }
            }
        }

        info.Title = H("profile-title");
        info.SupportUrl = H("support-url");
        info.WebPageUrl = H("profile-web-page-url");
        info.Announce = H("announce");
        if (int.TryParse(H("profile-update-interval"), out var hours) && hours > 0)
            info.UpdateIntervalHours = hours;

        // Fallback title: filename from Content-Disposition.
        if (info.Title is null && response.Content.Headers.ContentDisposition is ContentDispositionHeaderValue cd)
            info.Title = (cd.FileNameStar ?? cd.FileName)?.Trim('"');
    }

    private static string? DecodeHeader(string? value)
    {
        if (string.IsNullOrWhiteSpace(value))
            return null;
        value = value.Trim();
        if (value.StartsWith("base64:", StringComparison.OrdinalIgnoreCase))
            return Base64.TryDecode(value[7..]) ?? value;
        return value;
    }

    /// <summary>Marzban / Remnawave expose "{link}/info" with the username and status.</summary>
    private async Task TryReadInfoEndpointAsync(Uri uri, SubscriptionInfo info, CancellationToken ct)
    {
        try
        {
            var infoUri = new Uri(uri.GetLeftPart(UriPartial.Path).TrimEnd('/') + "/info");
            using var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
            cts.CancelAfter(TimeSpan.FromSeconds(8));
            using var request = new HttpRequestMessage(HttpMethod.Get, infoUri);
            request.Headers.UserAgent.ParseAdd(UserAgents[0]);
            using var response = await _http.SendAsync(request, cts.Token);
            if (!response.IsSuccessStatusCode)
                return;
            var json = await response.Content.ReadAsStringAsync(cts.Token);
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            // Remnawave wraps everything in { "response": { "user": { … } } }
            if (root.TryGetProperty("response", out var r))
                root = r.TryGetProperty("user", out var user) ? user : r;

            info.Username ??= Str(root, "username");
            info.Status ??= Str(root, "status") ?? Str(root, "userStatus");

            if (info.Total == 0 && Num(root, "data_limit", "trafficLimitBytes") is > 0 and var limit)
                info.Total = limit;
            if (info.Used == 0 && Num(root, "used_traffic", "trafficUsedBytes") is > 0 and var used)
                info.Download = used;
            if (info.Expire is null)
            {
                var exp = root.TryGetProperty("expire", out var e) ? e
                    : root.TryGetProperty("expiresAt", out e) ? e
                    : default;
                if (exp.ValueKind == JsonValueKind.Number && exp.GetInt64() > 0)
                    info.Expire = DateTimeOffset.FromUnixTimeSeconds(exp.GetInt64());
                else if (exp.ValueKind == JsonValueKind.String && DateTimeOffset.TryParse(exp.GetString(), out var dt))
                    info.Expire = dt;
            }
        }
        catch
        {
            // Optional endpoint: silently ignored when unavailable.
        }
    }

    private static string? Str(JsonElement e, string name) =>
        e.TryGetProperty(name, out var v) && v.ValueKind == JsonValueKind.String ? v.GetString() : null;

    private static long? Num(JsonElement e, params string[] names)
    {
        foreach (var name in names)
        {
            if (!e.TryGetProperty(name, out var v))
                continue;
            if (v.ValueKind == JsonValueKind.Number && v.TryGetInt64(out var n))
                return n;
            if (v.ValueKind == JsonValueKind.String && long.TryParse(v.GetString(), out n))
                return n;
        }
        return null;
    }
}
